package com.mobgrab.command;

import com.mobgrab.MobGrabMod;
import com.mobgrab.item.MobItem;
import com.mobgrab.preset.PresetStore;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Optional;

/**
 * Named mob templates: save the mob item in your hand, then hand copies of it out later.
 *
 * <p>Saving reads the item you are holding rather than the mob you are looking at, which is
 * how the Paper plugin did it. Holding the mob is the state MobGrab already puts you in, and
 * it sidesteps guessing which entity a look direction meant.
 */
public final class PresetCommand {

	private PresetCommand() {}

	private static final SuggestionProvider<CommandSourceStack> PRESET_NAMES = (context, builder) ->
			SharedSuggestionProvider.suggest(MobGrabMod.presets().names(), builder);

	static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("preset")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(PresetCommand::list)
				.then(Commands.literal("list")
						.executes(PresetCommand::list))
				.then(Commands.literal("save")
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(PresetCommand::save)))
				.then(Commands.literal("delete")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests(PRESET_NAMES)
								.executes(PresetCommand::delete)))
				.then(Commands.literal("give")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(PRESET_NAMES)
										.executes(PresetCommand::give))));
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		PresetStore presets = MobGrabMod.presets();
		if (presets.names().isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal(
					"No presets yet. Hold a grabbed mob and run /mobgrab preset save <name>.")
					.withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal(
				presets.names().size() + " preset(s):").withStyle(ChatFormatting.GOLD), false);
		for (String name : presets.names()) {
			String entity = presets.entityIdOf(name);
			context.getSource().sendSuccess(() -> Component.literal("  " + name)
					.withStyle(ChatFormatting.YELLOW)
					.append(Component.literal(" - " + entity).withStyle(ChatFormatting.GRAY)), false);
		}
		return presets.names().size();
	}

	private static int save(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(context, "name");

		if (!PresetStore.isValidName(name)) {
			context.getSource().sendFailure(Component.literal(
					"Preset names use lowercase letters, digits, '_' and '-', up to 32 characters."));
			return 0;
		}

		ItemStack held = player.getMainHandItem();
		Optional<MobItem.Grabbed> grabbed = MobItem.read(held);
		if (grabbed.isEmpty()) {
			context.getSource().sendFailure(Component.literal(
					"Hold a grabbed mob in your main hand to save it as a preset."));
			return 0;
		}

		String entityId = EntityType.getKey(grabbed.get().type()).toString();
		boolean replaced = MobGrabMod.presets().has(name);
		MobGrabMod.presets().put(name, entityId, grabbed.get().data());

		context.getSource().sendSuccess(() -> Component.literal(
				(replaced ? "Replaced preset '" : "Saved preset '") + name + "' (" + entityId + ").")
				.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int delete(CommandContext<CommandSourceStack> context) {
		String name = StringArgumentType.getString(context, "name");
		if (!MobGrabMod.presets().remove(name)) {
			context.getSource().sendFailure(Component.literal("There is no preset called '" + name + "'."));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Deleted preset '" + name + "'.")
				.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		String name = StringArgumentType.getString(context, "name");
		ServerLevel level = context.getSource().getLevel();

		PresetStore presets = MobGrabMod.presets();
		Optional<CompoundTag> data = presets.data(name);
		if (data.isEmpty()) {
			context.getSource().sendFailure(Component.literal(
					"Preset '" + name + "' is missing or its data could not be read."));
			return 0;
		}

		ItemStack template = buildItem(presets.entityIdOf(name), data.get(), level);
		if (template == null) {
			context.getSource().sendFailure(Component.literal(
					"Preset '" + name + "' could not be turned back into a mob on this version."));
			return 0;
		}

		for (ServerPlayer target : targets) {
			ItemStack copy = template.copy();
			if (!target.getInventory().add(copy)) target.drop(copy, false);
		}

		int count = targets.size();
		context.getSource().sendSuccess(() -> Component.literal(
				"Gave '" + name + "' to " + count + " player(s).").withStyle(ChatFormatting.GREEN), true);
		return count;
	}

	/**
	 * Rebuilds the mob from stored data and captures it again.
	 *
	 * <p>The entity is created but deliberately never added to the world — it exists only long
	 * enough to be read back into an item. Going through a real entity means the head texture,
	 * name and tooltip come from the same code that handles a live grab, rather than a second
	 * implementation that would drift from it.
	 */
	private static ItemStack buildItem(String entityId, CompoundTag data, ServerLevel level) {
		// The type has to be passed explicitly. Preset data comes from saveWithoutId and so
		// carries no "id" key, and the overload without a type looks for exactly that.
		Identifier id = Identifier.tryParse(entityId);
		Optional<EntityType<?>> type = id == null
				? Optional.empty()
				: BuiltInRegistries.ENTITY_TYPE.getOptional(id);
		if (type.isEmpty()) {
			MobGrabMod.LOGGER.warn("Preset names entity {}, which does not exist here", entityId);
			return null;
		}

		Entity entity = EntityType.loadEntityRecursive(
				type.get(), data, level, EntitySpawnReason.SPAWN_ITEM_USE, EntityProcessor.NOP);
		if (entity == null) {
			MobGrabMod.LOGGER.warn("Preset for {} could not be rebuilt", entityId);
			return null;
		}
		try {
			return MobItem.create(entity, level, MobGrabMod.config());
		} finally {
			entity.discard();
		}
	}
}
