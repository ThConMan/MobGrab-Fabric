package com.mobgrab.event;

import com.mobgrab.config.MobGrabConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player grab/place spacing.
 *
 * <p>Measured in game ticks rather than wall-clock time so that a paused singleplayer world —
 * the normal case for a hardcore run — does not quietly burn the cooldown while nothing is
 * happening.
 */
final class Cooldowns {

	private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();

	private Cooldowns() {}

	static boolean isCoolingDown(Player player, Level level, MobGrabConfig config) {
		if (config.cooldownSeconds <= 0) return false;
		Long last = LAST_ACTION.get(player.getUUID());
		if (last == null) return false;
		long ticks = Math.round(config.cooldownSeconds * 20.0);
		long elapsed = level.getGameTime() - last;
		// A backwards jump means a different world or a restored backup; treat it as expired.
		return elapsed >= 0 && elapsed < ticks;
	}

	static void mark(Player player, Level level) {
		LAST_ACTION.put(player.getUUID(), level.getGameTime());
	}

	static void forget(UUID playerId) {
		LAST_ACTION.remove(playerId);
	}
}
