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
		if (!(entity instanceof LivingEntity)) return InteractionResult.PASS;
		if (entity instanceof Player) return InteractionResult.PASS;
		if (config.requireSneak && !player.isShiftKeyDown()) return InteractionResult.PASS;
		if (config.isDimensionDisabled(level.dimension().identifier().toString())) return InteractionResult.PASS;

		String entityId = EntityType.getKey(entity.getType()).toString();
		if (!config.isMobEnabled(entityId)) return InteractionResult.PASS;

		// Everything past here is a decision only the server can make, and in singleplayer this
		// callback runs on both sides. Claiming the interaction on the client too stops the
		// vanilla right-click (trading, mounting, shearing) firing alongside the grab.
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

		if (config.requireOp && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			refuse(player, "You do not have permission to pick up mobs.");
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
			item = MobItem.create(entity, serverLevel, config);
		} catch (Exception e) {
			MobGrabMod.LOGGER.error("Could not capture {}", entityId, e);
			refuse(player, "That mob could not be picked up.");
			return InteractionResult.FAIL;
		}

		Effects.play(serverLevel, entity.position(), config.pickupSound, config.pickupSoundVolume,
				config.pickupSoundPitch, config.pickupParticle, config.pickupParticleCount);

		entity.discard();
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
