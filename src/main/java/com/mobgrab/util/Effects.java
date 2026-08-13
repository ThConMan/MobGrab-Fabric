package com.mobgrab.util;

import com.mobgrab.MobGrabMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Sound and particle playback driven by ids from the config. Anything unrecognised is
 * reported once and then skipped, so a typo costs a line in the log rather than the grab.
 */
public final class Effects {

	private Effects() {}

	public static void play(ServerLevel level, Vec3 at, String soundId, float volume, float pitch,
	                        String particleId, int particleCount) {
		sound(level, at, soundId, volume, pitch);
		particles(level, at, particleId, particleCount);
	}

	private static void sound(ServerLevel level, Vec3 at, String soundId, float volume, float pitch) {
		if (soundId == null || soundId.isBlank()) return;
		Identifier id = Identifier.tryParse(soundId);
		if (id == null) {
			warnOnce(soundId, "is not a valid sound id");
			return;
		}
		Optional<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.getOptional(id);
		if (sound.isEmpty()) {
			warnOnce(soundId, "is not a sound on this version");
			return;
		}
		level.playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
	}

	private static void particles(ServerLevel level, Vec3 at, String particleId, int count) {
		if (count <= 0 || particleId == null || particleId.isBlank()) return;
		Identifier id = Identifier.tryParse(particleId);
		if (id == null) {
			warnOnce(particleId, "is not a valid particle id");
			return;
		}
		var type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
		// Particles that carry extra data (block dust, dust colour, item particles) cannot be
		// built from an id alone, so only the plain ones are offered.
		if (!(type instanceof ParticleOptions options)) {
			warnOnce(particleId, "is not a particle that can be used without extra data");
			return;
		}
		level.sendParticles(options, at.x, at.y + 0.5, at.z, count, 0.3, 0.3, 0.3, 0.02);
	}

	private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static void warnOnce(String id, String problem) {
		if (WARNED.add(id)) MobGrabMod.LOGGER.warn("Config effect '{}' {}", id, problem);
	}
}
