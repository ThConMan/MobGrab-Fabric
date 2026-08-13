package com.mobgrab.command;

import com.mobgrab.MobGrabMod;
import com.mobgrab.config.MobGrabConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntityType;

/**
 * Server-side administration for MobGrab.
 *
 * <p>Deliberately optional: everything here also lives in {@code config/mobgrab.json}, because
 * a singleplayer hardcore world usually has no cheats and therefore no commands at all. Nothing
 * about the mod's normal use requires running one of these.
 */
public final class MobGrabCommand {

	private MobGrabCommand() {}

	private static final SuggestionProvider<CommandSourceStack> MOB_IDS = (context, builder) ->
			SharedSuggestionProvider.suggest(
					BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString), builder);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
	                            CommandBuildContext buildContext, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("mobgrab")
				// Without an executor here, a bare "/mobgrab" is merely a prefix of valid
				// commands and Brigadier rejects it as an incomplete command. Listing the
				// subcommands is the useful thing to do with it.
				.executes(MobGrabCommand::help)
				.then(Commands.literal("help")
						.executes(MobGrabCommand::help))
				.then(Commands.literal("status")
						.executes(MobGrabCommand::status))
				.then(Commands.literal("reload")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.executes(MobGrabCommand::reload))
				.then(Commands.literal("fireproof")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("enabled", BoolArgumentType.bool())
								.executes(MobGrabCommand::fireproof)))
				.then(Commands.literal("enable")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("mob", IdentifierArgument.id())
								.suggests(MOB_IDS)
								.executes(context -> toggle(context, true))))
				.then(Commands.literal("disable")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("mob", IdentifierArgument.id())
								.suggests(MOB_IDS)
								.executes(context -> toggle(context, false)))));
	}

	/**
	 * What "/mobgrab" on its own does. Only lists what the caller can actually run, so a
	 * player without operator permission is not shown admin subcommands.
	 */
	private static int help(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		boolean admin = source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

		source.sendSuccess(() -> Component.literal("MobGrab")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" — sneak + right-click a mob to pick it up, "
						+ "right-click a block to put it back.").withStyle(ChatFormatting.GRAY)), false);
		usage(source, "/mobgrab status", "show current settings");
		if (admin) {
			usage(source, "/mobgrab reload", "re-read config/mobgrab.json");
			usage(source, "/mobgrab fireproof <true|false>", "fireproof newly grabbed mobs");
			usage(source, "/mobgrab enable <mob>", "allow a mob to be grabbed");
			usage(source, "/mobgrab disable <mob>", "stop a mob being grabbed");
		} else {
			source.sendSuccess(() -> Component.literal(
					"Everything else is configured in config/mobgrab.json.")
					.withStyle(ChatFormatting.DARK_GRAY), false);
		}
		return 1;
	}

	private static void usage(CommandSourceStack source, String command, String description) {
		source.sendSuccess(() -> Component.literal(command).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY)), false);
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		MobGrabConfig config = MobGrabMod.config();
		long enabled = config.mobs.values().stream().filter(Boolean::booleanValue).count();

		context.getSource().sendSuccess(() -> Component.literal("MobGrab")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" — " + (config.enabled ? "on" : "off"))
						.withStyle(config.enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
		line(context, "Grabbable mobs", enabled + " of " + config.mobs.size() + " listed");
		line(context, "Sneak required", String.valueOf(config.requireSneak));
		line(context, "Operators only", String.valueOf(config.requireOp));
		line(context, "Cooldown", config.cooldownSeconds + "s");
		line(context, "Fireproof items", config.fireproofItems
				? "on (" + String.join(", ", config.itemDamageImmunity) + ")"
				: "off");
		if (!config.disabledDimensions.isEmpty()) {
			line(context, "Disabled in", String.join(", ", config.disabledDimensions));
		}
		return 1;
	}

	private static void line(CommandContext<CommandSourceStack> context, String label, String value) {
		context.getSource().sendSuccess(() -> Component.literal(label + ": ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		MobGrabMod.reload();
		context.getSource().sendSuccess(
				() -> Component.literal("MobGrab config reloaded.").withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int fireproof(CommandContext<CommandSourceStack> context) {
		boolean enabled = BoolArgumentType.getBool(context, "enabled");
		MobGrabConfig config = MobGrabMod.config();
		config.fireproofItems = enabled;
		config.save();
		// Items already in the world keep the component they were made with; only new grabs change.
		context.getSource().sendSuccess(() -> Component.literal(
				"Fireproof mob items " + (enabled ? "enabled" : "disabled")
						+ ". Items grabbed earlier keep whatever they were made with.")
				.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int toggle(CommandContext<CommandSourceStack> context, boolean enabled) {
		// IdentifierArgument parses "minecraft:cow" and bare "cow" alike; Brigadier's plain
		// string argument cannot read an unquoted colon at all.
		String id = IdentifierArgument.getId(context, "mob").toString();

		if (EntityType.byString(id).isEmpty()) {
			context.getSource().sendFailure(
					Component.literal("There is no entity called '" + id + "' on this version."));
			return 0;
		}

		MobGrabConfig config = MobGrabMod.config();
		config.setMobEnabled(id, enabled);
		config.save();
		context.getSource().sendSuccess(() -> Component.literal(
				id + " is now " + (enabled ? "grabbable" : "not grabbable") + ".")
				.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}
}
