package com.mobgrab.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mobgrab.MobGrabMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Named mob templates, kept in {@code config/mobgrab-presets.json}.
 *
 * <p>A preset is a mob's save data under a name, so a specific villager — trades, profession,
 * level and all — can be handed out repeatedly instead of being found again. Data is stored as
 * SNBT rather than a binary blob so the file stays something a person can read and hand-edit.
 */
public final class PresetStore {

	/** Presets are addressed by name in commands, so keep them to characters that survive that. */
	private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** One saved mob. {@code entity} is the entity id; {@code data} is its save data as SNBT. */
	private record Preset(String entity, String data) {}

	/** The JSON document itself. */
	private static final class Document {
		List<String> _readme = new ArrayList<>(List.of(
				"MobGrab presets. Save the mob item you are holding with '/mobgrab preset save <name>'.",
				"'data' is the mob's save data as SNBT — the same text /data get would show you.",
				"Hand-editing is fine; a preset that fails to parse is skipped with a warning, not fatal."
		));
		Map<String, Preset> presets = new LinkedHashMap<>();
	}

	private Document document = new Document();

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("mobgrab-presets.json");
	}

	public void load() {
		Path file = path();
		if (!Files.exists(file)) {
			document = new Document();
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Document parsed = GSON.fromJson(reader, Document.class);
			document = parsed == null ? new Document() : parsed;
			if (document.presets == null) document.presets = new LinkedHashMap<>();
			if (document._readme == null || document._readme.isEmpty()) {
				document._readme = new Document()._readme;
			}
		} catch (IOException | JsonSyntaxException e) {
			// Unlike the main config this is not moved aside, because presets are additive:
			// carrying on with none of them loses nothing that is not still in the file.
			MobGrabMod.LOGGER.error("Could not read {} ({}); starting with no presets",
					file.getFileName(), e.getMessage());
			document = new Document();
		}
	}

	public void save() {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(document), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MobGrabMod.LOGGER.error("Could not write {}", file, e);
		}
	}

	public static boolean isValidName(String name) {
		return VALID_NAME.matcher(name).matches();
	}

	public Set<String> names() {
		return document.presets.keySet();
	}

	public boolean has(String name) {
		return document.presets.containsKey(normalise(name));
	}

	public String entityIdOf(String name) {
		Preset preset = document.presets.get(normalise(name));
		return preset == null ? null : preset.entity();
	}

	/** Stores {@code data} under {@code name}, replacing any preset already there. */
	public void put(String name, String entityId, CompoundTag data) {
		document.presets.put(normalise(name), new Preset(entityId, data.toString()));
		save();
	}

	public boolean remove(String name) {
		boolean removed = document.presets.remove(normalise(name)) != null;
		if (removed) save();
		return removed;
	}

	/**
	 * Reads a preset back. Empty when the name is unknown or its SNBT no longer parses, which
	 * is recoverable — one unreadable preset should not take the others down with it.
	 */
	public Optional<CompoundTag> data(String name) {
		Preset preset = document.presets.get(normalise(name));
		if (preset == null) return Optional.empty();
		try {
			return Optional.of(TagParser.parseCompoundFully(preset.data()));
		} catch (Exception e) {
			MobGrabMod.LOGGER.warn("Preset '{}' has unreadable data: {}", name, e.getMessage());
			return Optional.empty();
		}
	}

	private static String normalise(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
