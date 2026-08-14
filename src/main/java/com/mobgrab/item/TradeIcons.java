package com.mobgrab.item;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mobgrab.MobGrabMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The little glyph and shortened name a trade line shows for an item, carried over from the
 * plugin so trades read the same here.
 *
 * <p>These are ordinary text characters rendered by the client's font. Most are emoji, which
 * the vanilla font covers; anything it does not know falls back to the default bullet rather
 * than showing a missing-glyph box.
 */
public final class TradeIcons {

	private TradeIcons() {}

	private static final Map<String, String> ICONS = new HashMap<>();
	private static final Map<String, String> SHORT_NAMES = new HashMap<>();
	private static String fallback = "●";

	/** Shape of the bundled resource. */
	private static final class Document {
		String _comment;
		@SuppressWarnings("unused")
		String defaultIcon;
		Map<String, String> icons = new LinkedHashMap<>();
		Map<String, String> short_names = new LinkedHashMap<>();
	}

	public static void load() {
		try (InputStream in = TradeIcons.class.getResourceAsStream("/data/mobgrab/trade_icons.json")) {
			if (in == null) {
				MobGrabMod.LOGGER.warn("trade_icons.json is missing from the jar; trades will use plain text");
				return;
			}
			var reader = new InputStreamReader(in, StandardCharsets.UTF_8);
			var root = new Gson().fromJson(reader, com.google.gson.JsonObject.class);
			if (root == null) return;

			if (root.has("default")) fallback = root.get("default").getAsString();
			if (root.has("icons")) {
				root.getAsJsonObject("icons").entrySet()
						.forEach(e -> ICONS.put(e.getKey(), e.getValue().getAsString()));
			}
			if (root.has("short_names")) {
				root.getAsJsonObject("short_names").entrySet()
						.forEach(e -> SHORT_NAMES.put(e.getKey(), e.getValue().getAsString()));
			}
			MobGrabMod.LOGGER.info("Loaded {} trade icons and {} short names", ICONS.size(), SHORT_NAMES.size());
		} catch (IOException | JsonSyntaxException e) {
			MobGrabMod.LOGGER.error("Could not read the bundled trade icons", e);
		}
	}

	public static String iconFor(ItemStack stack) {
		return ICONS.getOrDefault(idOf(stack), fallback);
	}

	/**
	 * The plugin's shortened name for an item, or null to use the item's own. A renamed item
	 * always wins, since its name is the point.
	 */
	public static String shortNameFor(ItemStack stack) {
		return SHORT_NAMES.get(idOf(stack));
	}

	private static String idOf(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}
