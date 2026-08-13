package com.mobgrab.item;

import com.mobgrab.MobGrabMod;
import com.mobgrab.config.MobGrabConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a live mob into an item and back again.
 *
 * <p>The mob's own save data is stored verbatim under a private key in {@code custom_data},
 * so anything the game can persist about an entity — trades, equipment, variant, anger, age,
 * leash, passengers — survives the round trip without MobGrab needing to know it exists.
 */
public final class MobItem {

	/** Our root key inside the item's custom_data. */
	private static final String ROOT = "MobGrab";
	/** Bumped only if the stored layout ever changes; old items are rejected rather than misread. */
	private static final int FORMAT = 1;

	private MobItem() {}

	/** A mob recovered from an item, ready to be spawned. */
	public record Grabbed(EntityType<?> type, CompoundTag data) {}

	/**
	 * Captures {@code entity} as an item. The caller is responsible for removing the entity —
	 * this only reads it.
	 */
	public static ItemStack create(Entity entity, ServerLevel level, MobGrabConfig config) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		entity.saveWithoutId(output);
		CompoundTag data = output.buildResult();

		// Identity and placement are re-established when the mob is put back down. Carrying the
		// old UUID forward would let a duplicated item spawn two entities that claim to be the
		// same one, which corrupts leads, mounts and pet ownership.
		data.remove("UUID");
		data.remove("Pos");
		data.remove("Motion");
		data.remove("Rotation");

		Identifier id = EntityType.getKey(entity.getType());

		CompoundTag stored = new CompoundTag();
		stored.putInt("v", FORMAT);
		stored.putString("id", id.toString());
		stored.put("data", data);

		CompoundTag root = new CompoundTag();
		root.put(ROOT, stored);

		ItemStack stack = HeadTextures.headFor(entity);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		stack.set(DataComponents.CUSTOM_NAME, itemName(entity, config));
		if (config.showLore) {
			List<Component> lore = MobLore.build(entity, data);
			if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
		}
		applyDamageImmunity(stack, level, config);
		return stack;
	}

	/**
	 * Reads a mob back out of an item, or empty if this is not a MobGrab item.
	 *
	 * <p>The entity type comes from our own key rather than from the entity data, so a
	 * hand-crafted item cannot claim to be a chicken and spawn a wither.
	 */
	public static Optional<Grabbed> read(ItemStack stack) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		if (custom == null) return Optional.empty();

		CompoundTag stored = custom.copyTag().getCompoundOrEmpty(ROOT);
		if (stored.isEmpty()) return Optional.empty();

		if (stored.getIntOr("v", 0) != FORMAT) {
			MobGrabMod.LOGGER.warn("Ignoring a MobGrab item written in format {} (this build reads {})",
					stored.getIntOr("v", 0), FORMAT);
			return Optional.empty();
		}

		Optional<String> id = stored.getString("id");
		if (id.isEmpty()) return Optional.empty();

		// Resolved through the registry rather than EntityType.byString, which 26.1 has and
		// 26.2 removed. This lookup is present in both, so one jar covers the whole range.
		Identifier typeId = Identifier.tryParse(id.get());
		Optional<EntityType<?>> type = typeId == null
				? Optional.empty()
				: BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);
		if (type.isEmpty()) {
			// The item names a mob this version does not have — a 26.2 mob on a 26.1 server, say.
			MobGrabMod.LOGGER.warn("A MobGrab item holds unknown entity type {}", id.get());
			return Optional.empty();
		}

		return Optional.of(new Grabbed(type.get(), stored.getCompoundOrEmpty("data")));
	}

	public static boolean isMobItem(ItemStack stack) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		return custom != null && custom.copyTag().contains(ROOT);
	}

	private static Component itemName(Entity entity, MobGrabConfig config) {
		MutableComponent name = Component.empty();
		if (config.keepMobNameOnItem && entity.hasCustomName() && entity.getCustomName() != null) {
			name.append(entity.getCustomName().copy());
		} else {
			name.append(entity.getType().getDescription().copy());
		}
		// Items with a custom name render italic by default; these are not "renamed" items.
		return name.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD));
	}


	/**
	 * Makes the item shrug off fire and lava by giving it the same {@code damage_resistant}
	 * component netherite gear uses, so it holds up while dropped on the ground with no
	 * event handling of our own.
	 */
	private static void applyDamageImmunity(ItemStack stack, ServerLevel level, MobGrabConfig config) {
		if (!config.fireproofItems || config.itemDamageImmunity.isEmpty()) return;

		var damageTypes = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
		List<Holder<DamageType>> holders = new ArrayList<>();
		HolderSet.Named<DamageType> single = null;

		for (String raw : config.itemDamageImmunity) {
			Identifier id = Identifier.tryParse(raw);
			if (id == null) {
				MobGrabMod.LOGGER.warn("itemDamageImmunity entry '{}' is not a valid tag id", raw);
				continue;
			}
			Optional<HolderSet.Named<DamageType>> tag = damageTypes.get(TagKey.create(Registries.DAMAGE_TYPE, id));
			if (tag.isEmpty()) {
				MobGrabMod.LOGGER.warn("itemDamageImmunity names damage type tag '{}', which does not exist", raw);
				continue;
			}
			single = tag.get();
			tag.get().forEach(holders::add);
		}

		if (holders.isEmpty()) return;
		// One tag stays a named set so the component reads as "#minecraft:is_fire" rather than
		// an expanded list; several have to be flattened because a component holds one set.
		HolderSet<DamageType> types = config.itemDamageImmunity.size() == 1 && single != null
				? single
				: HolderSet.direct(holders);
		stack.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(types));
	}
}
