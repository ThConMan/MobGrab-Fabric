package com.mobgrab.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The tooltip on a grabbed mob: everything the game knows about it that a player would want
 * to see before deciding to put it back down.
 *
 * <p>Ported from the MobGrab plugin's item lore. The plugin decorated trade lines with emoji
 * item icons; those are left out here deliberately.
 *
 * <p>Every branch is defensive about absent data. Lore is cosmetic, so a species accessor
 * throwing or returning null must never be the reason a mob cannot be picked up.
 */
public final class MobLore {

	private MobLore() {}

	/**
	 * Slimes, magma cubes and 26.2's sulfur cube, identified by id rather than by class.
	 *
	 * <p>26.2 moved Slime from {@code entity.monster} to {@code entity.monster.cubemob}, so a
	 * jar compiled against either version would fail to link against the other the moment it
	 * named that class. The size is read from save data instead, which has not moved.
	 */
	private static final Set<String> CUBE_MOBS =
			Set.of("minecraft:slime", "minecraft:magma_cube", "minecraft:sulfur_cube");

	public static List<Component> build(Entity entity, CompoundTag data) {
		List<Component> lore = new ArrayList<>();

		lore.add(line(entity.getType().getDescription().getString(), ChatFormatting.GRAY));

		if (entity.hasCustomName() && entity.getCustomName() != null) {
			lore.add(label("Name", entity.getCustomName().getString(), ChatFormatting.AQUA));
		}

		if (entity instanceof LivingEntity living) {
			lore.add(label("Health",
					"%.1f/%.1f".formatted(living.getHealth(), living.getMaxHealth()),
					ChatFormatting.RED));
		}

		if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
			LivingEntity owner = tamable.getOwner();
			// Null whenever the owner is offline or out of range, which is most of the time.
			if (owner != null) {
				lore.add(label("Owner", owner.getName().getString(), ChatFormatting.GREEN));
			}
		}

		addSpecies(lore, entity, data);
		addEquipment(lore, entity);

		if (entity instanceof AgeableMob ageable && ageable.isBaby()) {
			lore.add(tag("Baby", ChatFormatting.AQUA));
		}
		if (entity instanceof TamableAnimal tamable && tamable.isInSittingPose()) {
			lore.add(tag("Sitting", ChatFormatting.AQUA));
		}

		lore.add(Component.empty());
		lore.add(line("Right-click a block to place", ChatFormatting.YELLOW));
		return lore;
	}

	private static void addSpecies(List<Component> lore, Entity entity, CompoundTag data) {
		switch (entity) {
			case Villager villager -> {
				String profession = holderName(villager.getVillagerData().profession());
				if (!profession.isEmpty() && !profession.equalsIgnoreCase("None")) {
					lore.add(label("Profession", profession, ChatFormatting.YELLOW));
				}
				lore.add(label("Level", String.valueOf(villager.getVillagerData().level()),
						ChatFormatting.YELLOW));
				addTrades(lore, villager);
			}
			case Horse horse -> {
				lore.add(label("Color", variantName(horse.getVariant()), ChatFormatting.WHITE));
				lore.add(label("Style", variantName(horse.getMarkings()), ChatFormatting.WHITE));
			}
			case Cat cat -> {
				lore.add(label("Type", holderName(cat.getVariant()), ChatFormatting.WHITE));
				if (cat.isTame()) {
					lore.add(label("Collar", dye(cat.getCollarColor()), ChatFormatting.WHITE));
				}
			}
			case Wolf wolf -> {
				if (wolf.isTame()) {
					lore.add(label("Collar", dye(wolf.getCollarColor()), ChatFormatting.WHITE));
				}
			}
			case Sheep sheep -> {
				lore.add(label("Color", dye(sheep.getColor()), ChatFormatting.WHITE));
				if (sheep.isSheared()) lore.add(tag("Sheared", ChatFormatting.GRAY));
			}
			case Axolotl axolotl ->
					lore.add(label("Variant", variantName(axolotl.getVariant()), ChatFormatting.LIGHT_PURPLE));
			case Frog frog ->
					lore.add(label("Variant", holderName(frog.getVariant()), ChatFormatting.WHITE));
			case Parrot parrot ->
					lore.add(label("Color", variantName(parrot.getVariant()), ChatFormatting.WHITE));
			case Fox fox ->
					lore.add(label("Type", variantName(fox.getVariant()), ChatFormatting.WHITE));
			case Rabbit rabbit ->
					lore.add(label("Type", variantName(rabbit.getVariant()), ChatFormatting.WHITE));
			case MushroomCow mooshroom ->
					lore.add(label("Variant", variantName(mooshroom.getVariant()), ChatFormatting.WHITE));
			case Llama llama -> {
				lore.add(label("Color", variantName(llama.getVariant()), ChatFormatting.WHITE));
				lore.add(label("Strength", String.valueOf(llama.getStrength()), ChatFormatting.WHITE));
			}
			case Panda panda ->
					lore.add(label("Personality", variantName(panda.getMainGene()), ChatFormatting.WHITE));
			case Bee bee -> {
				if (bee.hasNectar()) lore.add(tag("Has Nectar", ChatFormatting.YELLOW));
				if (bee.hasStung()) lore.add(tag("Has Stung", ChatFormatting.RED));
				if (bee.isAngry()) lore.add(tag("Angry", ChatFormatting.RED));
			}
			case Goat goat -> {
				if (goat.isScreamingGoat()) lore.add(tag("Screaming", ChatFormatting.RED));
				if (!goat.hasLeftHorn() || !goat.hasRightHorn()) {
					String horns = goat.hasLeftHorn() ? "Left Only"
							: goat.hasRightHorn() ? "Right Only"
							: "None";
					lore.add(label("Horns", horns, ChatFormatting.WHITE));
				}
			}
			case Creeper creeper -> {
				if (creeper.isPowered()) lore.add(tag("Charged", ChatFormatting.LIGHT_PURPLE));
			}
			case Phantom phantom -> lore.add(label("Size", switch (phantom.getPhantomSize()) {
				case 0 -> "Small";
				case 1 -> "Medium";
				case 2 -> "Large";
				default -> "Size " + phantom.getPhantomSize();
			}, ChatFormatting.WHITE));
			case TropicalFish fish -> {
				lore.add(label("Pattern", variantName(fish.getPattern()), ChatFormatting.WHITE));
				lore.add(label("Body", dye(fish.getBaseColor()), ChatFormatting.WHITE));
				lore.add(label("Pattern Color", dye(fish.getPatternColor()), ChatFormatting.WHITE));
			}
			case Pufferfish puffer -> lore.add(label("Puff", switch (puffer.getPuffState()) {
				case 0 -> "Deflated";
				case 1 -> "Half Puffed";
				case 2 -> "Fully Puffed";
				default -> "State " + puffer.getPuffState();
			}, ChatFormatting.WHITE));
			case Strider strider -> {
				if (strider.isSuffocating()) lore.add(tag("Shivering", ChatFormatting.AQUA));
			}
			case CopperGolem golem ->
					lore.add(label("Weathering", variantName(golem.getWeatherState()), ChatFormatting.GOLD));
			case Creaking creaking -> {
				if (creaking.isActive()) lore.add(tag("Active", ChatFormatting.RED));
			}
			case Shulker shulker -> {
				if (shulker.getColor() != null) {
					lore.add(label("Color", dye(shulker.getColor()), ChatFormatting.WHITE));
				}
			}
			default -> { /* nothing species-specific worth showing */ }
		}

		// Cube mobs store their size one lower than it reads in game, so a "Size 0" tag is a
		// size-1 slime. Handled here rather than in the switch above to avoid naming the class.
		if (CUBE_MOBS.contains(net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString())) {
			int size = data.getIntOr("Size", 0) + 1;
			lore.add(label("Size", switch (size) {
				case 1 -> "Tiny";
				case 2 -> "Small";
				case 4 -> "Big";
				default -> "Size " + size;
			}, ChatFormatting.WHITE));
		}

		// Anger is worth surfacing on anything that tracks it, not just bees.
		if (!(entity instanceof Bee) && entity instanceof NeutralMob neutral && neutral.isAngry()) {
			lore.add(tag("Angry", ChatFormatting.RED));
		}
	}

	private static void addTrades(List<Component> lore, Villager villager) {
		List<MerchantOffer> offers;
		try {
			offers = villager.getOffers();
		} catch (Exception e) {
			return; // a villager mid-restock can be in an odd state; the tooltip is not worth a crash
		}
		if (offers == null || offers.isEmpty()) return;

		lore.add(Component.empty());
		lore.add(line("Trades (" + offers.size() + ")", ChatFormatting.YELLOW));

		for (MerchantOffer offer : offers) {
			MutableComponent trade = Component.literal("  ")
					.append(tradeItem(offer.getCostA()));
			if (!offer.getCostB().isEmpty()) {
				trade.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY))
						.append(tradeItem(offer.getCostB()));
			}
			trade.append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
					.append(tradeItem(offer.getResult()));

			// A trade the villager has exhausted still shows, struck through, because knowing
			// it exists and is locked is exactly what you want before placing them again.
			boolean soldOut = offer.isOutOfStock();
			lore.add(trade.withStyle(style -> style.withItalic(false)
					.withStrikethrough(soldOut)
					.withColor(soldOut ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY)));
		}
	}

	private static Component tradeItem(ItemStack stack) {
		String name = stack.getHoverName().getString();
		String display = stack.getCount() > 1 ? stack.getCount() + "x " + name : name;

		MutableComponent text = Component.literal(display).withStyle(ChatFormatting.GRAY);
		String enchants = enchantSummary(stack);
		if (!enchants.isEmpty()) {
			text.append(Component.literal(" (" + enchants + ")").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return text;
	}

	/** Enchantments on an item, including the stored ones that make an enchanted book useful. */
	private static String enchantSummary(ItemStack stack) {
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		ItemEnchantments direct = stack.getEnchantments();
		ItemEnchantments source = stored != null && !stored.isEmpty() ? stored : direct;
		if (source == null || source.isEmpty()) return "";

		List<String> parts = new ArrayList<>();
		for (var entry : source.entrySet()) {
			parts.add(holderName(entry.getKey()) + " " + roman(entry.getIntValue()));
		}
		return String.join(", ", parts);
	}

	private static void addEquipment(List<Component> lore, Entity entity) {
		if (!(entity instanceof LivingEntity living)) return;
		addEquipmentLine(lore, "Helmet", living.getItemBySlot(EquipmentSlot.HEAD));
		addEquipmentLine(lore, "Chestplate", living.getItemBySlot(EquipmentSlot.CHEST));
		addEquipmentLine(lore, "Leggings", living.getItemBySlot(EquipmentSlot.LEGS));
		addEquipmentLine(lore, "Boots", living.getItemBySlot(EquipmentSlot.FEET));
		addEquipmentLine(lore, "Holding", living.getItemBySlot(EquipmentSlot.MAINHAND));
		addEquipmentLine(lore, "Off Hand", living.getItemBySlot(EquipmentSlot.OFFHAND));
	}

	private static void addEquipmentLine(List<Component> lore, String slot, ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		lore.add(label(slot, stack.getHoverName().getString(), ChatFormatting.LIGHT_PURPLE));

		ItemEnchantments enchants = stack.getEnchantments();
		if (enchants == null || enchants.isEmpty()) return;
		for (var entry : enchants.entrySet()) {
			lore.add(line("  " + holderName(entry.getKey()) + " " + roman(entry.getIntValue()),
					ChatFormatting.BLUE));
		}
	}

	// ---- formatting helpers -------------------------------------------------------------

	private static Component line(String text, ChatFormatting color) {
		return Component.literal(text)
				.withStyle(style -> style.withItalic(false).withColor(color));
	}

	/** A standalone state, e.g. "Charged". Marked with a bullet so it reads apart from labels. */
	private static Component tag(String text, ChatFormatting color) {
		return Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(text).withStyle(color))
				.withStyle(style -> style.withItalic(false));
	}

	private static Component label(String label, String value, ChatFormatting valueColor) {
		return Component.literal(label + ": ")
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.DARK_GRAY))
				.append(Component.literal(value)
						.withStyle(style -> style.withItalic(false).withColor(valueColor)));
	}

	/** Registry-backed variants (cat, frog, villager profession, enchantment) name themselves by key. */
	private static String holderName(Holder<?> holder) {
		if (holder == null) return "";
		return holder.unwrapKey()
				.map(key -> prettify(key.identifier().getPath()))
				.orElse("");
	}

	/** Plain enum or StringRepresentable variants (horse colour, fox type, panda gene). */
	private static String variantName(Object variant) {
		if (variant == null) return "";
		if (variant instanceof StringRepresentable named) return prettify(named.getSerializedName());
		if (variant instanceof Enum<?> value) return prettify(value.name());
		return prettify(variant.toString());
	}

	private static String dye(Object color) {
		return variantName(color);
	}

	private static String prettify(String raw) {
		if (raw == null || raw.isEmpty()) return "";
		StringBuilder out = new StringBuilder(raw.length());
		for (String part : raw.toLowerCase(Locale.ROOT).split("[_ ]")) {
			if (part.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
		}
		return out.toString();
	}

	private static String roman(int value) {
		return switch (value) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(value);
		};
	}
}
