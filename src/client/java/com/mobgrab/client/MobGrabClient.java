package com.mobgrab.client;

import com.mobgrab.net.MobGrabPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashSet;
import java.util.Set;

/**
 * The optional client half: a menu and two key mappings.
 *
 * <p>Entirely cosmetic to MobGrab's operation. Sneak + right-click works without any of this,
 * on a vanilla client, which is what keeps the mod installable on a server alone.
 */
public final class MobGrabClient implements ClientModInitializer {

	/** Set by the server on join. Null means "no MobGrab on the other end". */
	private static MobGrabPayloads.Sync state;

	private static final Set<String> NOT_GRABBABLE = new HashSet<>();

	private static KeyMapping openMenu;
	private static KeyMapping grabLooked;

	public static MobGrabPayloads.Sync state() {
		return state;
	}

	public static boolean isGrabbable(String entityId) {
		return !NOT_GRABBABLE.contains(entityId);
	}

	/** Whether the server on the other end actually has MobGrab. */
	public static boolean serverHasMobGrab() {
		return state != null && ClientPlayNetworking.canSend(MobGrabPayloads.RequestSync.TYPE);
	}

	@Override
	public void onInitializeClient() {
		// Bound by default, since a menu nobody can find is not much of a menu. G is free in
		// vanilla. The grab key ships unbound: sneak + right-click already does the job, so it
		// is a convenience to opt into rather than another default to collide with.
		openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.mobgrab.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_G,
				KeyMapping.Category.MISC));
		grabLooked = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.mobgrab.grab", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
				KeyMapping.Category.GAMEPLAY));

		ClientPlayNetworking.registerGlobalReceiver(MobGrabPayloads.Sync.TYPE,
				(payload, context) -> context.client().execute(() -> {
					state = payload;
					NOT_GRABBABLE.clear();
					NOT_GRABBABLE.addAll(payload.notGrabbable());
					// The menu shows server state, so it has to follow the server's reply
					// rather than optimistically flipping when the button was pressed.
					MobGrabScreen.refreshIfOpen();
				}));

		// Forget the server's state on disconnect, so the menu on a later vanilla server does
		// not show whatever the previous server happened to be running.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			state = null;
			NOT_GRABBABLE.clear();
		});

		ClientTickEvents.END_CLIENT_TICK.register(MobGrabClient::onTick);

		com.mobgrab.MobGrabMod.LOGGER.info(
				"MobGrab client ready — menu on G, grab key unbound by default");
	}

	private static void onTick(Minecraft client) {
		if (client.player == null) return;

		while (openMenu.consumeClick()) {
			if (!serverHasMobGrab()) {
				notInstalled(client);
				continue;
			}
			ClientPlayNetworking.send(new MobGrabPayloads.RequestSync());
			client.setScreenAndShow(new MobGrabScreen());
		}

		while (grabLooked.consumeClick()) {
			if (!serverHasMobGrab()) {
				notInstalled(client);
				continue;
			}
			// Only ever a request. The server resolves the id in its own level and re-checks
			// reach, permissions and config before anything happens.
			if (client.hitResult instanceof EntityHitResult hit) {
				Entity target = hit.getEntity();
				ClientPlayNetworking.send(new MobGrabPayloads.GrabEntity(target.getId()));
			}
		}
	}

	private static void notInstalled(Minecraft client) {
		if (client.player == null) return;
		// sendSystemMessage rather than an action-bar overlay: 26.1 and 26.2 disagree about
		// where the overlay lives, and this exists unchanged in both.
		client.player.sendSystemMessage(
				Component.literal("This server does not have MobGrab installed.")
						.withStyle(ChatFormatting.RED));
	}
}
