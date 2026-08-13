package com.mobgrab.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Wires the two interactions MobGrab cares about, plus cooldown cleanup on disconnect. */
public final class MobGrabEvents {

	private MobGrabEvents() {}

	public static void register() {
		UseEntityCallback.EVENT.register(GrabHandler::onUseEntity);
		UseBlockCallback.EVENT.register(PlaceHandler::onUseBlock);
		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> Cooldowns.forget(handler.getPlayer().getUUID()));
	}
}
