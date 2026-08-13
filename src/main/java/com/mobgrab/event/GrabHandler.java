package com.mobgrab.event;

import com.mobgrab.MobGrabMod;
import com.mobgrab.config.MobGrabConfig;
import com.mobgrab.item.MobItem;
import com.mobgrab.util.Effects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/** Sneak + right-click a mob to pocket it. */
public final class GrabHandler {

	private GrabHandler() {}

	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand,
	                                            Entity entity, EntityHitResult hit) {
		MobGrabConfig config = MobGrabMod.config();

		if (!config.enabled) return InteractionResult.PASS;
		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
		if (config.requireSneak && !player.isShiftKeyDown()) return InteractionResult.PASS;
		if (!isGrabbable(entity, level, config)) return InteractionResult.PASS;

		// Everything past here is a decision only the server can make, and in singleplayer this
		// callback runs on both sides. Claiming the interaction on the client too stops the
		// vanilla right-click (trading, mounting, shearing) firing alongside the grab.
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

		return grab(player, serverLevel, entity, config);
	}

	/**
	 * Whether this entity is one MobGrab handles here at all. Kept allocation-free: it runs on
	 * every right-click of every entity, long before anything interesting happens.
	 */
	public static boolean isGrabbable(Entity entity, Level level, MobGrabConfig config) {
		if (!(entity instanceof LivingEntity)) return false;
		if (entity instanceof Player) return false;
		if (config.hasDimensionRestrictions()
				&& config.isDimensionDisabled(level.dimension().identifier().toString())) {
			return false;
		}
		return config.isMobEnabled(entity.getType());
	}

	/**
	 * Performs the grab, having already established that the mob is eligible.
	 *
	 * <p>Shared by the right-click path and the optional grab key, so a client that binds the
	 * key gets exactly the same permission, cooldown, inventory and rider checks rather than a
	 * more trusting second route into the same operation.
	 */
	public static InteractionResult grab(Player player, ServerLevel level, Entity entity,
	                                     MobGrabConfig config) {
		if (config.requireOp && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			refuse(player, "You do not have permission to pick up mobs.");
			return InteractionResult.FAIL;
		}

		// Refuse a mob somebody is sitting on. Grabbing it would mean discarding the whole
		// passenger stack, and that stack would include a player entity.
		if (entity.getSelfAndPassengers().anyMatch(riding -> riding instanceof Player)) {
			refuse(player, "Someone is riding that.");
			return InteractionResult.FAIL;
		}

		if (Cooldowns.isCoolingDown(player, level, config)) return InteractionResult.FAIL;

		// Check for room before the mob is removed, or a full inventory deletes it outright.
		if (player.getInventory().getFreeSlot() == -1) {
			refuse(player, "Your inventory is full.");
			return InteractionResult.FAIL;
		}

		ItemStack item;
		try {
			item = MobItem.create(entity, level, config);
		} catch (Exception e) {
			MobGrabMod.LOGGER.error("Could not capture {}", EntityType.getKey(entity.getType()), e);
			refuse(player, "That mob could not be picked up.");
			return InteractionResult.FAIL;
		}

		Effects.play(level, entity.position(), config.pickupSound, config.pickupSoundVolume,
				config.pickupSoundPitch, config.pickupParticle, config.pickupParticleCount);

		// The saved data includes Passengers, and discarding a vehicle only ejects its riders
		// rather than removing them. Discarding the vehicle alone would leave a chicken
		// jockey's zombie standing in the world while the item also holds a copy of it, so
		// placing the item would duplicate the rider along with whatever it was carrying.
		for (Entity ridden : entity.getSelfAndPassengers().toList()) {
			ridden.discard();
		}

		if (!player.getInventory().add(item)) {
			// getFreeSlot said there was room, so this should not happen — but dropping the
			// item is the only outcome here that cannot lose the mob.
			player.drop(item, false);
		}

		Cooldowns.mark(player, level);
		player.sendOverlayMessage(Component.literal("Picked up ")
				.withStyle(ChatFormatting.GREEN)
				.append(entity.getType().getDescription().copy().withStyle(ChatFormatting.GOLD)));
		return InteractionResult.SUCCESS_SERVER;
	}

	static void refuse(Player player, String message) {
		player.sendOverlayMessage(Component.literal(message).withStyle(ChatFormatting.RED));
	}
}
