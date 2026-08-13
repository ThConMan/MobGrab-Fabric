package com.mobgrab.net;

import com.mobgrab.MobGrabMod;
import com.mobgrab.config.MobGrabConfig;
import com.mobgrab.event.GrabHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server half of the optional client menu.
 *
 * <p>Nothing here is required for MobGrab to work. It exists so a player who has the mod
 * installed gets a menu and key mappings; players who do not are unaffected, and a server
 * whose players are all vanilla will simply never send or receive any of it.
 *
 * <p>Every inbound payload is treated as an untrusted request: permissions, reach, config and
 * cooldown are all re-checked here, because a client can send whatever it likes.
 */
public final class MobGrabServerNet {

	private MobGrabServerNet() {}

	/** Reach slack, matching what vanilla allows for lag on entity interactions. */
	private static final double REACH_PADDING = 1.0;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MobGrabPayloads.Sync.TYPE, MobGrabPayloads.Sync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MobGrabPayloads.RequestSync.TYPE, MobGrabPayloads.RequestSync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MobGrabPayloads.ToggleMob.TYPE, MobGrabPayloads.ToggleMob.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MobGrabPayloads.SetFlag.TYPE, MobGrabPayloads.SetFlag.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MobGrabPayloads.GrabEntity.TYPE, MobGrabPayloads.GrabEntity.CODEC);

		// Only clients that declared the channel get told anything, which is how a stock client
		// stays entirely unaware that any of this exists.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.player, MobGrabPayloads.Sync.TYPE)) {
				sendSync(handler.player);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(MobGrabPayloads.RequestSync.TYPE,
				(payload, context) -> context.server().execute(() -> sendSync(context.player())));

		ServerPlayNetworking.registerGlobalReceiver(MobGrabPayloads.ToggleMob.TYPE,
				(payload, context) -> context.server().execute(() -> {
					ServerPlayer player = context.player();
					if (!isOperator(player)) return;

					Identifier id = Identifier.tryParse(payload.entityId());
					if (id == null || BuiltInRegistries.ENTITY_TYPE.getOptional(id).isEmpty()) return;

					MobGrabConfig config = MobGrabMod.config();
					config.setMobEnabled(id.toString(), payload.allowed());
					config.save();
					broadcastSync(context.server());
				}));

		ServerPlayNetworking.registerGlobalReceiver(MobGrabPayloads.SetFlag.TYPE,
				(payload, context) -> context.server().execute(() -> {
					ServerPlayer player = context.player();
					if (!isOperator(player)) return;

					MobGrabConfig config = MobGrabMod.config();
					switch (payload.flag()) {
						case "enabled" -> config.enabled = payload.value();
						case "requireSneak" -> config.requireSneak = payload.value();
						case "fireproofItems" -> config.fireproofItems = payload.value();
						default -> {
							// An unknown flag means a client newer than this server; ignore it
							// rather than letting a typo write an arbitrary setting.
							MobGrabMod.LOGGER.debug("Ignoring unknown flag '{}'", payload.flag());
							return;
						}
					}
					config.save();
					broadcastSync(context.server());
				}));

		ServerPlayNetworking.registerGlobalReceiver(MobGrabPayloads.GrabEntity.TYPE,
				(payload, context) -> context.server().execute(() -> {
					ServerPlayer player = context.player();
					MobGrabConfig config = MobGrabMod.config();
					if (!config.enabled) return;
					if (!(player.level() instanceof ServerLevel level)) return;

					// Resolved in the player's own level, so a crafted id cannot reach into
					// another dimension or name an entity the player cannot see.
					Entity entity = level.getEntity(payload.entityId());
					if (entity == null || entity == player || entity.isRemoved()) return;
					if (!player.isWithinEntityInteractionRange(entity, REACH_PADDING)) return;
					if (!GrabHandler.isGrabbable(entity, level, config)) return;

					GrabHandler.grab(player, level, entity, config);
				}));
	}

	private static boolean isOperator(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/** Pushes the current state to every connected client that can receive it. */
	private static void broadcastSync(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, MobGrabPayloads.Sync.TYPE)) {
				sendSync(player);
			}
		}
	}

	private static void sendSync(ServerPlayer player) {
		MobGrabConfig config = MobGrabMod.config();

		// The toggle list the server actually keeps, plus its own verdict for each entry. The
		// client cannot disagree with the server about which mobs will really work, and does
		// not have to decide for itself which entity types count as mobs.
		List<String> mobs = new ArrayList<>(config.mobs.keySet());
		Collections.sort(mobs);

		List<String> notGrabbable = new ArrayList<>();
		for (String id : mobs) {
			if (!config.isMobEnabled(id)) notGrabbable.add(id);
		}

		ServerPlayNetworking.send(player, new MobGrabPayloads.Sync(
				isOperator(player), config.enabled, config.requireSneak,
				config.fireproofItems, mobs, notGrabbable));
	}
}
