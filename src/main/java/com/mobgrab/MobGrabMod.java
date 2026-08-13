package com.mobgrab;

import com.mobgrab.command.MobGrabCommand;
import com.mobgrab.config.MobGrabConfig;
import com.mobgrab.event.MobGrabEvents;
import com.mobgrab.item.HeadTextures;
import com.mobgrab.net.MobGrabServerNet;
import com.mobgrab.preset.PresetStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MobGrab — pick a mob up as a head item and set it back down with its data intact.
 *
 * <p>A server-side mod: it runs on the logical server, which in a singleplayer or hardcore
 * world is the one inside the game client. Nothing is required on a connecting client, so a
 * dedicated server running this can still be joined with a stock Minecraft install.
 */
public final class MobGrabMod implements ModInitializer {

	public static final String MOD_ID = "mobgrab";
	public static final Logger LOGGER = LoggerFactory.getLogger("MobGrab");

	/** Swapped wholesale on reload; handlers read it per interaction, hence volatile. */
	private static volatile MobGrabConfig config = new MobGrabConfig();

	public static MobGrabConfig config() {
		return config;
	}

	private static final PresetStore PRESETS = new PresetStore();

	public static PresetStore presets() {
		return PRESETS;
	}

	public static void reload() {
		config = MobGrabConfig.load();
		PRESETS.load();
	}

	@Override
	public void onInitialize() {
		HeadTextures.load();
		reload();

		MobGrabEvents.register();
		MobGrabServerNet.register();
		// Checked when the command tree is built rather than here, because that happens after
		// the config has been read and lets `enableCommands` actually suppress registration.
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			if (config().enableCommands) {
				MobGrabCommand.register(dispatcher, buildContext, selection);
			}
		});

		LOGGER.info("MobGrab ready — sneak + right-click a mob to pick it up");
	}
}
