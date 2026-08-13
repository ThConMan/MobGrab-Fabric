package com.mobgrab.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mobgrab.MobGrabMod;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Picks the item that represents a grabbed mob.
 *
 * <p>Six mobs have a real vanilla skull; everything else is a player head wearing a stock
 * skin texture. The table lives in a JSON resource keyed by entity id rather than in code,
 * so ids belonging to mobs that do not exist on the running version simply never match —
 * which is what lets one jar cover 26.1.2 and everything after it.
 */
public final class HeadTextures {

	private static final Map<Identifier, Item> NATIVE_SKULLS = new HashMap<>();
	private static final Map<Identifier, String> ENTITY_TEXTURES = new HashMap<>();
	private static final Map<Identifier, String> PROFESSION_TEXTURES = new HashMap<>();

	private HeadTextures() {}

	/** Shape of {@code /data/mobgrab/heads.json}. */
	private static final class Table {
		Map<String, String> items;
		Map<String, String> textures;
		Map<String, String> villager_professions;
	}

	public static void load() {
		NATIVE_SKULLS.clear();
		ENTITY_TEXTURES.clear();
		PROFESSION_TEXTURES.clear();

		try (InputStream in = HeadTextures.class.getResourceAsStream("/data/mobgrab/heads.json")) {
			if (in == null) {
				MobGrabMod.LOGGER.error("heads.json is missing from the jar; every mob will use a plain player head");
				return;
			}
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
			Table table = new Gson().fromJson(reader, Table.class);
			if (table == null) return;

			if (table.items != null) {
				table.items.forEach((entityId, itemId) -> {
					Identifier entity = Identifier.tryParse(entityId);
					Identifier item = Identifier.tryParse(itemId);
					if (entity == null || item == null) return;
					// An unknown item id means that skull does not exist here; fall through to a
					// textured player head rather than failing the whole table.
					BuiltInRegistries.ITEM.getOptional(item)
							.ifPresent(value -> NATIVE_SKULLS.put(entity, value));
				});
			}
			putTextures(table.textures, ENTITY_TEXTURES);
			putTextures(table.villager_professions, PROFESSION_TEXTURES);

			MobGrabMod.LOGGER.info("Loaded {} skull items and {} head textures",
					NATIVE_SKULLS.size(), ENTITY_TEXTURES.size());
		} catch (Exception e) {
			MobGrabMod.LOGGER.error("Could not read heads.json", e);
		}
	}

	private static void putTextures(Map<String, String> source, Map<Identifier, String> target) {
		if (source == null) return;
		source.forEach((key, texture) -> {
			Identifier id = Identifier.tryParse(key);
			if (id != null && texture != null && !texture.isBlank()) target.put(id, texture);
		});
	}

	/**
	 * Builds the display item for a mob. Villagers resolve to their profession's head so a
	 * pocketed librarian still looks like a librarian.
	 */
	public static ItemStack headFor(Entity entity) {
		Identifier id = EntityType.getKey(entity.getType());

		if (entity instanceof Villager villager) {
			String professionTexture = professionTexture(villager);
			if (professionTexture != null) return texturedHead(professionTexture);
		}

		Item skull = NATIVE_SKULLS.get(id);
		if (skull != null) return new ItemStack(skull);

		String texture = ENTITY_TEXTURES.get(id);
		if (texture != null) return texturedHead(texture);

		return new ItemStack(Items.PLAYER_HEAD);
	}

	private static String professionTexture(Villager villager) {
		Holder<VillagerProfession> profession = villager.getVillagerData().profession();
		return profession.unwrapKey()
				.map(key -> PROFESSION_TEXTURES.get(key.identifier()))
				.orElse(null);
	}

	/**
	 * A player head carrying a base64 skin blob. The profile is marked resolved so the client
	 * renders the embedded texture directly instead of trying to look the name up against
	 * Mojang's session servers — which matters because these profiles name no real account.
	 */
	private static ItemStack texturedHead(String base64Texture) {
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		PropertyMap properties = new PropertyMap(
				ImmutableMultimap.of("textures", new Property("textures", base64Texture)));
		GameProfile profile = new GameProfile(UUID.randomUUID(), "MobGrab", properties);
		head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
		return head;
	}
}
