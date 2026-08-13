package com.mobgrab.net;

import com.mobgrab.MobGrabMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The wire format between a MobGrab server and a MobGrab client.
 *
 * <p>All of this is optional. The server only sends to clients that declared these channels,
 * a stock client simply never does, and every message from a client is re-checked server-side
 * before it changes anything — a payload is a request, never an instruction.
 */
public final class MobGrabPayloads {

	private MobGrabPayloads() {}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MobGrabMod.MOD_ID, path);
	}

	/**
	 * Server's answer to "what is MobGrab doing here?".
	 *
	 * <p>{@code mobs} is the toggle list the server actually keeps, and {@code notGrabbable} is
	 * the server's own verdict for each of them rather than a copy of the config. The client
	 * therefore never reimplements whitelist/blacklist precedence and cannot disagree with the
	 * server about what will really work.
	 */
	public record Sync(boolean canEdit, boolean enabled, boolean requireSneak, boolean fireproof,
	                   List<String> mobs, List<String> notGrabbable) implements CustomPacketPayload {

		public static final Type<Sync> TYPE = new Type<>(id("sync"));

		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Sync::canEdit,
				ByteBufCodecs.BOOL, Sync::enabled,
				ByteBufCodecs.BOOL, Sync::requireSneak,
				ByteBufCodecs.BOOL, Sync::fireproof,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)), Sync::mobs,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)), Sync::notGrabbable,
				Sync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client asking for a fresh {@link Sync}, sent when the menu opens. */
	public record RequestSync() implements CustomPacketPayload {

		public static final Type<RequestSync> TYPE = new Type<>(id("request_sync"));

		public static final StreamCodec<RegistryFriendlyByteBuf, RequestSync> CODEC =
				StreamCodec.unit(new RequestSync());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client asking to flip one mob's toggle. Operator-only, enforced on the server. */
	public record ToggleMob(String entityId, boolean allowed) implements CustomPacketPayload {

		public static final Type<ToggleMob> TYPE = new Type<>(id("toggle_mob"));

		public static final StreamCodec<RegistryFriendlyByteBuf, ToggleMob> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ToggleMob::entityId,
				ByteBufCodecs.BOOL, ToggleMob::allowed,
				ToggleMob::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client asking to flip one boolean setting. Operator-only, enforced on the server. */
	public record SetFlag(String flag, boolean value) implements CustomPacketPayload {

		public static final Type<SetFlag> TYPE = new Type<>(id("set_flag"));

		public static final StreamCodec<RegistryFriendlyByteBuf, SetFlag> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, SetFlag::flag,
				ByteBufCodecs.BOOL, SetFlag::value,
				SetFlag::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Client asking to grab the entity it is looking at, for players who bound the grab key.
	 *
	 * <p>Only the entity's network id travels; the server resolves it in the player's own level
	 * and re-checks reach, permissions, config and cooldown, so this is no more trusting than
	 * the ordinary right-click path.
	 */
	public record GrabEntity(int entityId) implements CustomPacketPayload {

		public static final Type<GrabEntity> TYPE = new Type<>(id("grab_entity"));

		public static final StreamCodec<RegistryFriendlyByteBuf, GrabEntity> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, GrabEntity::entityId,
				GrabEntity::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
