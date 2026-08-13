package com.mobgrab.event;

import com.mobgrab.MobGrabMod;
import com.mobgrab.config.MobGrabConfig;
import com.mobgrab.item.MobItem;
import com.mobgrab.util.Effects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Right-click a block holding a grabbed mob to set it back down. */
public final class PlaceHandler {

	private PlaceHandler() {}

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
	                                           BlockHitResult hit) {
		MobGrabConfig config = MobGrabMod.config();
		ItemStack held = player.getItemInHand(hand);

		if (!config.enabled) return InteractionResult.PASS;
		if (!MobItem.isMobItem(held)) return InteractionResult.PASS;
		if (config.isDimensionDisabled(level.dimension().identifier().toString())) return InteractionResult.PASS;

		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

		if (config.requireOp && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			GrabHandler.refuse(player, "You do not have permission to place mobs.");
			return InteractionResult.FAIL;
		}

		Optional<MobItem.Grabbed> grabbed = MobItem.read(held);
		if (grabbed.isEmpty()) {
			GrabHandler.refuse(player, "This mob item is damaged and cannot be placed.");
			return InteractionResult.FAIL;
		}

		EntityType<?> type = grabbed.get().type();
		String entityId = EntityType.getKey(type).toString();
		// An item can outlive the toggle that allowed it, so re-check rather than trusting it.
		if (!config.isMobEnabled(entityId)) {
			GrabHandler.refuse(player, "That mob cannot be placed here.");
			return InteractionResult.FAIL;
		}

		if (Cooldowns.isCoolingDown(player, level, config)) return InteractionResult.FAIL;

		BlockPos target = hit.getBlockPos().relative(hit.getDirection());
		Vec3 spawn = Vec3.atBottomCenterOf(target);

		Entity entity;
		try {
			entity = EntityType.loadEntityRecursive(type, grabbed.get().data(), serverLevel,
					EntitySpawnReason.SPAWN_ITEM_USE, EntityProcessor.NOP);
		} catch (Exception e) {
			MobGrabMod.LOGGER.error("Could not rebuild {} from a mob item", entityId, e);
			GrabHandler.refuse(player, "That mob could not be placed.");
			return InteractionResult.FAIL;
		}

		if (entity == null) {
			GrabHandler.refuse(player, "That mob could not be placed.");
			return InteractionResult.FAIL;
		}

		entity.snapTo(spawn.x, spawn.y, spawn.z, player.getYRot(), 0.0f);
		if (!serverLevel.addFreshEntity(entity)) {
			GrabHandler.refuse(player, "There is no room for that mob here.");
			return InteractionResult.FAIL;
		}

		Effects.play(serverLevel, spawn, config.placeSound, config.placeSoundVolume,
				config.placeSoundPitch, config.placeParticle, config.placeParticleCount);

		if (!player.getAbilities().instabuild) held.shrink(1);
		Cooldowns.mark(player, level);

		player.sendOverlayMessage(Component.literal("Placed ")
				.withStyle(ChatFormatting.GREEN)
				.append(type.getDescription().copy().withStyle(ChatFormatting.GOLD)));
		return InteractionResult.SUCCESS_SERVER;
	}
}
