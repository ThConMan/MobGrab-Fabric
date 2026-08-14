package com.mobgrab.gui;

import com.mobgrab.MobGrabMod;
import com.mobgrab.config.MobGrabConfig;
import com.mobgrab.item.HeadTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The plugin's chest GUI, rebuilt as a real server-side container.
 *
 * <p>Unlike the key-bound menu in {@code src/client}, this needs nothing installed on the
 * player's machine: it is an ordinary inventory screen, so it works for a stock client on a
 * dedicated server, and in singleplayer, exactly as the Paper plugin's did.
 *
 * <p>Clicks never move items. Every slot is a button, so {@link #clicked} interprets the click
 * and rebuilds the page instead of letting the default container logic pick anything up.
 */
public final class MobToggleMenu extends ChestMenu {

	private static final int ROWS = 6;
	private static final int SIZE = ROWS * 9;
	/** The bottom row is navigation, so the top five rows hold mobs. */
	private static final int PER_PAGE = SIZE - 9;

	private static final int SLOT_PREV = SIZE - 9;
	private static final int SLOT_INFO = SIZE - 5;
	private static final int SLOT_NEXT = SIZE - 1;

	private final ServerPlayer viewer;
	private final SimpleContainer container;
	private final List<String> mobs;
	private final boolean canEdit;
	private int page;

	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider(
				(id, inventory, p) -> new MobToggleMenu(id, inventory, player),
				Component.literal("MobGrab").withStyle(ChatFormatting.DARK_GRAY)));
	}

	private MobToggleMenu(int containerId, Inventory inventory, ServerPlayer viewer) {
		this(containerId, inventory, viewer, new SimpleContainer(SIZE));
	}

	private MobToggleMenu(int containerId, Inventory inventory, ServerPlayer viewer,
	                      SimpleContainer container) {
		super(MenuType.GENERIC_9x6, containerId, inventory, container, ROWS);
		this.viewer = viewer;
		this.container = container;
		this.canEdit = viewer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
		this.mobs = new ArrayList<>(MobGrabMod.config().mobs.keySet());
		this.mobs.sort(String::compareTo);
		render();
	}

	private int pageCount() {
		return Math.max(1, (mobs.size() + PER_PAGE - 1) / PER_PAGE);
	}

	/** Repaints the whole window from current config, which is also how a toggle takes effect. */
	private void render() {
		MobGrabConfig config = MobGrabMod.config();
		for (int i = 0; i < SIZE; i++) container.setItem(i, ItemStack.EMPTY);

		int start = page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && start + i < mobs.size(); i++) {
			container.setItem(i, mobEntry(mobs.get(start + i), config));
		}

		if (page > 0) container.setItem(SLOT_PREV, button(Items.ARROW, "Previous page", ChatFormatting.YELLOW));
		if (page < pageCount() - 1) container.setItem(SLOT_NEXT, button(Items.ARROW, "Next page", ChatFormatting.YELLOW));
		container.setItem(SLOT_INFO, info(config));

		broadcastChanges();
	}

	/** One mob, as its own head, with its state and what a click will do. */
	private ItemStack mobEntry(String id, MobGrabConfig config) {
		boolean allowed = config.isMobEnabled(id);
		Identifier identifier = Identifier.tryParse(id);
		EntityType<?> type = identifier == null
				? null
				: BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);

		ItemStack icon = type == null ? new ItemStack(Items.PLAYER_HEAD) : HeadTextures.headFor(type);

		Component name = type != null ? type.getDescription().copy() : Component.literal(id);
		icon.set(DataComponents.CUSTOM_NAME, Component.empty().append(name)
				.withStyle(style -> style.withItalic(false)
						.withColor(allowed ? ChatFormatting.GREEN : ChatFormatting.RED)));

		List<Component> lore = new ArrayList<>();
		lore.add(plain(id, ChatFormatting.DARK_GRAY));
		lore.add(plain(allowed ? "Grabbable" : "Not grabbable",
				allowed ? ChatFormatting.GREEN : ChatFormatting.RED));
		lore.add(Component.empty());
		lore.add(plain(canEdit ? "Click to toggle" : "Operators only", ChatFormatting.YELLOW));
		icon.set(DataComponents.LORE, new ItemLore(lore));
		return icon;
	}

	private ItemStack info(MobGrabConfig config) {
		ItemStack stack = new ItemStack(Items.BOOK);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("MobGrab")
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));

		long allowed = mobs.stream().filter(config::isMobEnabled).count();
		List<Component> lore = new ArrayList<>();
		lore.add(plain("Page " + (page + 1) + " of " + pageCount(), ChatFormatting.GRAY));
		lore.add(plain(allowed + " of " + mobs.size() + " grabbable", ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(plain("Grabbing: " + (config.enabled ? "on" : "off"), ChatFormatting.GRAY));
		lore.add(plain("Sneak required: " + config.requireSneak, ChatFormatting.GRAY));
		lore.add(plain("Fireproof items: " + config.fireproofItems, ChatFormatting.GRAY));
		if (!canEdit) {
			lore.add(Component.empty());
			lore.add(plain("Read-only: not an operator", ChatFormatting.RED));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private ItemStack button(net.minecraft.world.item.Item item, String label, ChatFormatting color) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(label)
				.withStyle(style -> style.withItalic(false).withColor(color)));
		return stack;
	}

	private static Component plain(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(color));
	}

	/**
	 * Treats every slot as a button.
	 *
	 * <p>Nothing is ever picked up or moved: this window's items are painted labels, and letting
	 * the default handling run would let a player pull mob heads out of it as real items.
	 */
	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		// Slots outside the chest belong to the player's own inventory; leaving those alone
		// would still allow shift-clicking items in, so all clicks are swallowed.
		if (slotId < 0 || slotId >= SIZE) return;

		if (slotId == SLOT_PREV && page > 0) {
			page--;
			render();
			click(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
			return;
		}
		if (slotId == SLOT_NEXT && page < pageCount() - 1) {
			page++;
			render();
			click(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
			return;
		}
		if (slotId >= PER_PAGE) return;   // info slot and empty navigation row

		int index = page * PER_PAGE + slotId;
		if (index >= mobs.size()) return;

		if (!canEdit) {
			viewer.sendSystemMessage(Component.literal("Only operators can change MobGrab settings.")
					.withStyle(ChatFormatting.RED));
			return;
		}

		String id = mobs.get(index);
		MobGrabConfig config = MobGrabMod.config();
		boolean now = !config.isMobEnabled(id);
		config.setMobEnabled(id, now);
		config.save();
		render();
		click(now ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
				now ? 1.5f : 0.7f);
	}

	private void click(net.minecraft.sounds.SoundEvent sound, float pitch) {
		viewer.level().playSound(null, viewer.blockPosition(), sound, SoundSource.PLAYERS, 0.4f, pitch);
	}

	/** Shift-click has its own path into the container, so it is closed off too. */
	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return player == viewer && !viewer.isRemoved();
	}

	static {
		// Trips immediately in dev if the layout maths ever stops making sense.
		if (SLOT_INFO >= SIZE || SLOT_PREV >= SIZE || SLOT_NEXT >= SIZE) {
			throw new IllegalStateException("MobGrab menu slots fall outside the container");
		}
		if (SharedConstants.IS_RUNNING_IN_IDE && PER_PAGE <= 0) {
			throw new IllegalStateException("MobGrab menu has no room for mobs");
		}
	}
}
