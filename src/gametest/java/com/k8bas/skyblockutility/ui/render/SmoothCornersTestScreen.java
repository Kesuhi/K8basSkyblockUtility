package com.k8bas.skyblockutility.ui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * T2.9d's probes, for gametests only: green rounded shapes on black at known GUI positions, so a screenshot's
 * green channel is each pixel's coverage. A magenta shape inside an empty clip must never show.
 */
public final class SmoothCornersTestScreen extends Screen {
	static final int GREEN = 0xFF00FF00;
	/** The alpha of a faded colour (Widget.faded: 45 %). */
	static final int FADED_ALPHA = 0x73;
	static final int MAGENTA = 0xFFFF00FF;
	static final int TOP = 10;
	static final int W = 40;
	static final int H = 24;
	/** The probes' left edges: filled, faded, outline, half outside a clip, and a 9×9 box whose radius 6 is clamped. */
	static final int FILLED = 10;
	static final int FADED = 60;
	static final int OUTLINE = 110;
	static final int CLIPPED = 160;
	static final int CLIP_WIDTH = 20;
	static final int SMALL = 210;
	static final int SMALL_SIZE = 9;

	public SmoothCornersTestScreen() {
		super(Component.literal("Smooth corners test"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xFF000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Shapes.roundedRect(graphics, FILLED, TOP, W, H, Shapes.RADIUS_CARD, GREEN);
		Shapes.roundedRect(graphics, FADED, TOP, W, H, Shapes.RADIUS_CARD, FADED_ALPHA << 24 | GREEN & 0xFFFFFF);
		Shapes.roundedOutline(graphics, OUTLINE, TOP, W, H, Shapes.RADIUS_CARD, 1, GREEN);
		try (Clip clip = Clip.push(graphics, CLIPPED, TOP, CLIP_WIDTH, H)) {
			Shapes.roundedRect(graphics, CLIPPED, TOP, W, H, Shapes.RADIUS_CARD, GREEN);
		}
		Shapes.roundedRect(graphics, SMALL, TOP, SMALL_SIZE, SMALL_SIZE, Shapes.RADIUS_CARD, GREEN);
		try (Clip empty = Clip.push(graphics, 10, 50, 0, 0)) {
			Shapes.roundedRect(graphics, 10, 50, W, H, Shapes.RADIUS_CARD, MAGENTA);
			Shapes.roundedOutline(graphics, 60, 50, W, H, Shapes.RADIUS_CARD, 1, MAGENTA);
		}
	}
}
