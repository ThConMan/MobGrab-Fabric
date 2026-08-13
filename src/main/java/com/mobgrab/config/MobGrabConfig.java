package com.mobgrab.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mobgrab.MobGrabMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything MobGrab can be told to do, as one JSON file in {@code config/mobgrab.json}.
 *
 * <p>Editing the file is the only setup path that always works: a singleplayer hardcore world
 * has no operator and usually no cheats, so commands may be unavailable there. Every option
 * therefore has a default that is correct with no setup at all, and the commands only ever
 * duplicate what the file can already express.
 */
public final class MobGrabConfig {

	/** Written into the file purely so it explains itself when someone opens it. */
	public List<String> _readme = new ArrayList<>(List.of(
			"MobGrab config. Sneak + right-click a mob to pick it up; right-click a block to put it back.",
			"This file is read on startup and by '/mobgrab reload'. Delete it to regenerate the defaults.",
			"requireOp=false means everyone can grab — that is what makes singleplayer and hardcore work untouched.",
			"itemDamageImmunity takes damage-type tags; add 'minecraft:is_explosion' to also survive blasts.",
			"mobs: true = grabbable. Any mob missing from this list follows allowNewMobsByDefault."
	));

	/** Master switch. When false MobGrab does nothing at all. */
	public boolean enabled = true;

	/** Register the /mobgrab command tree. Turn off on servers that drive everything from
	 *  this file; grabbing and placing are unaffected either way. Takes effect on restart,
	 *  because Minecraft builds its command tree once at startup. */
	public boolean enableCommands = true;

	/** Require the player to be sneaking to grab. Off means a bare right-click grabs, which
	 *  collides with riding, trading, breeding and shearing — leave it on unless you mean it. */
	public boolean requireSneak = true;

	/** Restrict grabbing/placing to operators. Off by default so singleplayer and hardcore
	 *  worlds work with no permissions setup and no cheats enabled. */
	public boolean requireOp = false;

	/** Per-player delay between grabs, in seconds. Stops a held right-click emptying a farm. */
	public double cooldownSeconds = 1.0;

	/** false = only mobs set true below can be grabbed. true = everything except those set false. */
	public boolean blacklistMode = false;

	/** What to do about a mob that is not listed in {@link #mobs} at all — which is how mobs
	 *  added by a later Minecraft version arrive. */
	public boolean allowNewMobsByDefault = true;

	/** Grabbed mob items survive fire and lava, like netherite gear. */
	public boolean fireproofItems = true;

	/** Damage-type tags the mob item is immune to while {@link #fireproofItems} is on.
	 *  Anything in the vanilla damage_type tag list works, e.g. minecraft:is_explosion. */
	public List<String> itemDamageImmunity = new ArrayList<>(List.of("minecraft:is_fire"));

	/** Carry a named mob's name onto the item, so a named pet is still recognisable in the bag. */
	public boolean keepMobNameOnItem = true;

	/** Show health/age/profession detail on the item tooltip. */
	public boolean showLore = true;

	/** Dimensions where MobGrab is fully off, e.g. "minecraft:the_nether". */
	public List<String> disabledDimensions = new ArrayList<>();

	public String pickupSound = "minecraft:entity.chicken.egg";
	public float pickupSoundVolume = 0.8f;
	public float pickupSoundPitch = 1.4f;
	public String placeSound = "minecraft:entity.enderman.teleport";
	public float placeSoundVolume = 0.6f;
	public float placeSoundPitch = 1.2f;

	public String pickupParticle = "minecraft:smoke";
	public int pickupParticleCount = 15;
	public String placeParticle = "minecraft:happy_villager";
	public int placeParticleCount = 20;

	/** Per-mob toggles, keyed by entity id. Ids that do not exist on this Minecraft version are
	 *  ignored, so one config file works across versions. */
	public Map<String, Boolean> mobs = new LinkedHashMap<>();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("mobgrab.json");
	}

	/**
	 * Reads the config, creating it from the defaults when absent. A damaged file is kept as
	 * {@code mobgrab.json.broken} rather than silently overwritten, because a hand-tuned mob
	 * list is not something to throw away on a stray comma.
	 */
	public static MobGrabConfig load() {
		Path file = path();
		MobGrabConfig config;

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, MobGrabConfig.class);
				if (config == null) throw new JsonSyntaxException("file is empty");
			} catch (IOException | JsonSyntaxException e) {
				MobGrabMod.LOGGER.error("Could not read {} ({}). Falling back to defaults; your file is kept as mobgrab.json.broken",
						file.getFileName(), e.getMessage());
				try {
					Files.move(file, file.resolveSibling("mobgrab.json.broken"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException moveFailed) {
					MobGrabMod.LOGGER.error("Could not set the broken config aside", moveFailed);
				}
				config = new MobGrabConfig();
			}
		} else {
			config = new MobGrabConfig();
		}

		config.sanitize();
		// Rewrite on every load so options added by a MobGrab update show up in the file
		// with their defaults instead of staying invisible until someone reads the changelog.
		config.save();
		return config;
	}

	/**
	 * Replaces nulls left by hand-edited files and fills in the stock mob list. Gson leaves a
	 * field untouched when the key is absent, but happily assigns null when the key is present
	 * and empty, so every collection has to be re-checked here rather than trusted.
	 */
	private void sanitize() {
		if (_readme == null || _readme.isEmpty()) _readme = new MobGrabConfig()._readme;
		if (itemDamageImmunity == null) itemDamageImmunity = new ArrayList<>(List.of("minecraft:is_fire"));
		if (disabledDimensions == null) disabledDimensions = new ArrayList<>();
		if (mobs == null) mobs = new LinkedHashMap<>();

		if (pickupSound == null) pickupSound = "";
		if (placeSound == null) placeSound = "";
		if (pickupParticle == null) pickupParticle = "";
		if (placeParticle == null) placeParticle = "";

		cooldownSeconds = Math.max(0.0, cooldownSeconds);
		pickupParticleCount = Math.max(0, pickupParticleCount);
		placeParticleCount = Math.max(0, placeParticleCount);
		pickupSoundVolume = clampVolume(pickupSoundVolume);
		placeSoundVolume = clampVolume(placeSoundVolume);
		pickupSoundPitch = clampPitch(pickupSoundPitch);
		placeSoundPitch = clampPitch(placeSoundPitch);

		if (mobs.isEmpty()) mobs.putAll(defaultMobs());
	}

	private static float clampVolume(float v) {
		return Float.isFinite(v) ? Math.clamp(v, 0.0f, 1.0f) : 1.0f;
	}

	private static float clampPitch(float p) {
		return Float.isFinite(p) ? Math.clamp(p, 0.5f, 2.0f) : 1.0f;
	}

	/** The stock toggle list, carried over from the MobGrab plugin's config.yml. */
	private static Map<String, Boolean> defaultMobs() {
		try (InputStream in = MobGrabConfig.class.getResourceAsStream("/data/mobgrab/default_mobs.json")) {
			if (in == null) {
				MobGrabMod.LOGGER.error("default_mobs.json is missing from the jar; starting with an empty mob list");
				return new LinkedHashMap<>();
			}
			@SuppressWarnings("unchecked")
			Map<String, Boolean> parsed = GSON.fromJson(
					new InputStreamReader(in, StandardCharsets.UTF_8), LinkedHashMap.class);
			return parsed == null ? new LinkedHashMap<>() : parsed;
		} catch (IOException | JsonSyntaxException e) {
			MobGrabMod.LOGGER.error("Could not read the bundled mob list", e);
			return new LinkedHashMap<>();
		}
	}

	public void save() {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MobGrabMod.LOGGER.error("Could not write {}", file, e);
		}
	}

	/**
	 * Whether a mob id may be grabbed.
	 *
	 * <p>An explicit entry in {@link #mobs} always wins, which makes both modes agree on every
	 * mob the file actually lists. The modes only diverge on ids that are absent — a mob added
	 * by a newer Minecraft version, say — where blacklist mode admits it and whitelist mode
	 * would not, unless {@link #allowNewMobsByDefault} says otherwise.
	 */
	public boolean isMobEnabled(String entityId) {
		Boolean listed = mobs.get(entityId);
		if (listed != null) return listed;
		return blacklistMode || allowNewMobsByDefault;
	}

	/**
	 * Resolved answers per entity type. {@code transient} keeps it out of the JSON.
	 *
	 * <p>This exists because the alternative — turning the type back into an id string on
	 * every interaction — allocates a string every time any player right-clicks any mob,
	 * whether or not MobGrab ends up doing anything.
	 */
	private transient volatile Map<EntityType<?>, Boolean> resolvedMobs = new ConcurrentHashMap<>();

	public boolean isMobEnabled(EntityType<?> type) {
		return resolvedMobs.computeIfAbsent(type, t -> isMobEnabled(EntityType.getKey(t).toString()));
	}

	/** Sets a toggle and drops the resolved cache, so command edits take effect immediately. */
	public void setMobEnabled(String entityId, boolean allowed) {
		mobs.put(entityId, allowed);
		resolvedMobs = new ConcurrentHashMap<>();
	}

	/** True when {@link #disabledDimensions} is worth consulting at all. Checked first so the
	 *  default empty list costs nothing instead of building a dimension id string per click. */
	public boolean hasDimensionRestrictions() {
		return !disabledDimensions.isEmpty();
	}

	public boolean isDimensionDisabled(String dimensionId) {
		return disabledDimensions.contains(dimensionId);
	}
}
