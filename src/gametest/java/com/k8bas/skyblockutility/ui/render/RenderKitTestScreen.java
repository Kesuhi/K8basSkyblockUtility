package com.k8bas.skyblockutility.ui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * T2.2's test panel, for gametests only: every part of the render kit on one screen, sized to the GUI
 * with the 4 px margin (REQ-UI-02). With {@link #probe} on it also draws magenta inside empty clips,
 * which must never show.
 */
public final class RenderKitTestScreen extends Screen {
	static final int PROBE_COLOR = 0xFFFF00FF;
	volatile boolean probe;
	/** visible() of each probe clip in the last frame. */
	volatile List<Boolean> probeVisible = List.of();
	/** Whether the clip stack was empty again after the probe (every push popped). */
	volatile boolean probeBalanced;
	/** The first card's top-left corner and width, in GUI pixels, for the corner pixel checks. */
	volatile int[] firstCard = new int[3];

	public RenderKitTestScreen() {
		super(Component.literal("Render kit test"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// A plain background, so the title panorama cannot make screenshots differ.
		graphics.fill(0, 0, width, height, 0xFF404850);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Theme theme = Theme.current();
		Shapes.rect(graphics, 0, 0, width, height, Theme.BACKDROP);
		int w = Math.min(500, width - 8);
		int h = Math.min(300, height - 8);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		Shapes.rect(graphics, x, y, w, h, Theme.PANEL);
		// Header with a 1 px accent line.
		Shapes.rect(graphics, x, y, w, 38, Theme.HEADER);
		Shapes.rect(graphics, x, y + 37, w, 1, theme.accent());
		UiText.draw(graphics, font, "K8bas Skyblock Utility", x + 8, y + 15, Theme.TEXT_PRIMARY);
		UiText.rightAligned(graphics, font, "v1.2.0", x + w - 8, y + 15, Theme.TEXT_SECONDARY);
		// Sidebar with a selected and a plain tab.
		int sidebar = Math.min(160, w / 3);
		Shapes.rect(graphics, x, y + 38, sidebar, h - 38, Theme.SIDEBAR);
		Shapes.roundedRect(graphics, x + 6, y + 46, sidebar - 12, 20, Shapes.RADIUS_TAB, theme.accentBackground());
		UiText.draw(graphics, font, "Highlights", x + 14, y + 52, theme.accent());
		UiText.draw(graphics, font, "Waypoints", x + 14, y + 76, Theme.TEXT_SECONDARY);
		// Cards: normal and hovered.
		int cx = x + sidebar + 8;
		int cw = w - sidebar - 16;
		firstCard = new int[] {cx, y + 46, cw};
		Shapes.roundedRect(graphics, cx, y + 46, cw, 44, Shapes.RADIUS_CARD, Theme.CARD);
		Shapes.roundedOutline(graphics, cx, y + 46, cw, 44, Shapes.RADIUS_CARD, 1, Theme.SEPARATOR);
		UiText.draw(graphics, font, "Mob Highlighter", cx + 8, y + 54, Theme.TEXT_PRIMARY);
		UiText.truncated(graphics, font, "Outlines mobs that match your rules while you can see them, cut to fit.", cx + 8, y + 68, cw - 16,
				Theme.TEXT_SECONDARY);
		Shapes.roundedRect(graphics, cx, y + 96, cw, 44, Shapes.RADIUS_CARD, Theme.CARD_HOVER);
		UiText.draw(graphics, font, "Disabled option", cx + 8, y + 104, Theme.TEXT_DISABLED);
		UiText.draw(graphics, font, "Error: pattern does not compile", cx + 8, y + 118, Theme.ERROR);
		// Buttons: normal, primary, destructive.
		int by = y + 150;
		Shapes.roundedRect(graphics, cx, by, 60, 18, Shapes.RADIUS_CONTROL, Theme.CARD_HOVER);
		UiText.centred(graphics, font, "Cancel", cx + 30, by + 5, Theme.TEXT_PRIMARY);
		Shapes.roundedRect(graphics, cx + 66, by, 60, 18, Shapes.RADIUS_CONTROL, theme.accent());
		UiText.centred(graphics, font, "Save", cx + 96, by + 5, theme.textOnAccent());
		Shapes.roundedRect(graphics, cx + 132, by, 60, 18, Shapes.RADIUS_CONTROL, Theme.DESTRUCTIVE);
		UiText.centred(graphics, font, "Delete", cx + 162, by + 5, Theme.TEXT_PRIMARY);
		// A clipped, scrolled region: only its window shows.
		try (Clip clip = Clip.push(graphics, cx, y + 176, cw, 30)) {
			if (clip.visible()) {
				for (int row = 0; row < 6; row++) {
					UiText.draw(graphics, font, "Scrolled row " + row, cx + 8, y + 170 + row * 10, Theme.TEXT_SECONDARY);
				}
			}
		}
		if (probe) {
			drawProbe(graphics);
		}
	}

	/** Empty clips: zero size, negative size, an empty one inside a visible one, and a sized one inside an empty one. */
	private void drawProbe(GuiGraphicsExtractor graphics) {
		List<Boolean> visible = new ArrayList<>();
		try (Clip zero = Clip.push(graphics, 10, 10, 0, 0)) {
			visible.add(zero.visible());
			graphics.fill(0, 0, width, height, PROBE_COLOR);
		}
		try (Clip negative = Clip.push(graphics, 30, 10, -20, 15)) {
			visible.add(negative.visible());
			graphics.fill(0, 0, width, height, PROBE_COLOR);
			UiText.draw(graphics, font, "never shown", 30, 10, PROBE_COLOR);
		}
		try (Clip outer = Clip.push(graphics, 0, 0, width, height)) {
			visible.add(outer.visible());
			try (Clip inner = Clip.push(graphics, width / 2, height / 2, 0, 5)) {
				visible.add(inner.visible());
				graphics.fill(0, 0, width, height, PROBE_COLOR);
			}
		}
		try (Clip empty = Clip.push(graphics, 10, 10, 0, 0)) {
			try (Clip sized = Clip.push(graphics, 0, 0, 100, 20)) {
				visible.add(sized.visible());
				graphics.fill(0, 0, width, height, PROBE_COLOR);
			}
		}
		probeBalanced = graphics.scissorStack.peek() == null;
		probeVisible = List.copyOf(visible);
	}
}
