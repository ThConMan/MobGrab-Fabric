package com.mobgrab.client;

import com.mobgrab.net.MobGrabPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The mob-toggle menu, opened with the MobGrab key.
 *
 * <p>Paged rather than scrolling, which keeps it to plain buttons and avoids a selection-list
 * widget for what is a flat list of toggles. Everything shown is the server's state: pressing a
 * toggle sends a request and the display only changes when the server's reply comes back, so
 * the menu can never claim a change the server refused.
 */
public final class MobGrabScreen extends Screen {

	private static final int ROWS = 8;
	private static final int ROW_HEIGHT = 20;
	private static final int LIST_WIDTH = 220;

	/**
	 * The menu, while it is on screen.
	 *
	 * <p>Tracked here rather than read back off Minecraft, because 26.1 exposes a screen field
	 * and 26.2 does not — self-registration works the same on both.
	 */
	private static MobGrabScreen open;

	private EditBox search;
	private String filter = "";
	private int page;

	public MobGrabScreen() {
		super(Component.literal("MobGrab"));
	}

	private List<String> visibleMobs() {
		MobGrabPayloads.Sync state = MobGrabClient.state();
		if (state == null) return List.of();
		if (filter.isEmpty()) return state.mobs();

		List<String> matches = new ArrayList<>();
		for (String id : state.mobs()) {
			if (id.toLowerCase(Locale.ROOT).contains(filter)) matches.add(id);
		}
		return matches;
	}

	/** Rebuilds the menu if it is currently on screen, after the server's state changed. */
	static void refreshIfOpen() {
		if (open != null) open.rebuild();
	}

	@Override
	protected void init() {
		open = this;
		MobGrabPayloads.Sync state = MobGrabClient.state();
		int left = (this.width - LIST_WIDTH) / 2;
		int top = 46;

		String previous = search == null ? "" : search.getValue();
		search = new EditBox(this.font, left, top - 22, LIST_WIDTH, 18,
				Component.literal("Search"));
		search.setValue(previous);
		search.setResponder(value -> {
			filter = value.toLowerCase(Locale.ROOT);
			page = 0;
			rebuild();
		});
		addRenderableWidget(search);

		if (state == null) {
			addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
					.bounds(this.width / 2 - 50, top, 100, 20).build());
			return;
		}

		// Global settings first: they decide whether the per-mob list matters at all.
		int flagY = top;
		addFlagButton(left, flagY, "Grabbing", "enabled", state.enabled(), state.canEdit());
		addFlagButton(left + LIST_WIDTH / 2 + 2, flagY, "Sneak needed", "requireSneak",
				state.requireSneak(), state.canEdit());
		addFlagButton(left, flagY + 22, "Fireproof items", "fireproofItems",
				state.fireproof(), state.canEdit());

		List<String> mobs = visibleMobs();
		int listTop = flagY + 50;
		int start = page * ROWS;

		for (int i = 0; i < ROWS && start + i < mobs.size(); i++) {
			String id = mobs.get(start + i);
			boolean on = MobGrabClient.isGrabbable(id);
			Button button = Button.builder(mobLabel(id, on), b -> {
						if (!state.canEdit()) return;
						ClientPlayNetworking.send(new MobGrabPayloads.ToggleMob(id, !MobGrabClient.isGrabbable(id)));
					})
					.bounds(left, listTop + i * ROW_HEIGHT, LIST_WIDTH, 18)
					.build();
			button.active = state.canEdit();
			addRenderableWidget(button);
		}

		int navY = listTop + ROWS * ROW_HEIGHT + 4;
		int pages = Math.max(1, (mobs.size() + ROWS - 1) / ROWS);

		Button previousPage = Button.builder(Component.literal("< Prev"), b -> {
			if (page > 0) { page--; rebuild(); }
		}).bounds(left, navY, 60, 20).build();
		previousPage.active = page > 0;
		addRenderableWidget(previousPage);

		Button nextPage = Button.builder(Component.literal("Next >"), b -> {
			if (page < pages - 1) { page++; rebuild(); }
		}).bounds(left + LIST_WIDTH - 60, navY, 60, 20).build();
		nextPage.active = page < pages - 1;
		addRenderableWidget(nextPage);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(left + LIST_WIDTH / 2 - 40, navY + 24, 80, 20).build());
	}

	private void addFlagButton(int x, int y, String label, String flag, boolean value, boolean canEdit) {
		Button button = Button.builder(
						Component.literal(label + ": ").append(onOff(value)),
						b -> ClientPlayNetworking.send(new MobGrabPayloads.SetFlag(flag, !value)))
				.bounds(x, y, LIST_WIDTH / 2 - 2, 20)
				.build();
		button.active = canEdit;
		addRenderableWidget(button);
	}

	private static Component onOff(boolean value) {
		return Component.literal(value ? "on" : "off")
				.withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private static Component mobLabel(String id, boolean on) {
		String name = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
		return Component.literal(name + " ").append(onOff(on));
	}

	/** Rebuilds the widgets in place, which is how a paged screen re-renders after a change. */
	private void rebuild() {
		clearWidgets();
		init();
	}

	@Override
	public void removed() {
		open = null;
		super.removed();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

		MobGrabPayloads.Sync state = MobGrabClient.state();
		if (state == null) {
			graphics.centeredText(this.font,
					Component.literal("Waiting for the server...").withStyle(ChatFormatting.GRAY),
					this.width / 2, 30, 0xFFAAAAAA);
			return;
		}

		List<String> mobs = visibleMobs();
		int pages = Math.max(1, (mobs.size() + ROWS - 1) / ROWS);
		String footer = mobs.size() + " mobs - page " + (page + 1) + " of " + pages
				+ (state.canEdit() ? "" : "  (read-only: not an operator)");
		graphics.centeredText(this.font,
				Component.literal(footer).withStyle(ChatFormatting.GRAY),
				this.width / 2, 30, 0xFFAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
