package com.k8bas.skyblockutility.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A HUD element for gametests only (REQ-HUD-10): a solid box whose feature, content and size the test sets. */
final class TestHudElement implements HudElement {
	static final String ID = "k8bas_gametest_box";

	/** A filled box of {@code width}×{@code height} at scale 1. */
	record Box(int width, int height, int argb) implements HudContent {
		@Override
		public int width(Font font) {
			return width;
		}

		@Override
		public int height(Font font) {
			return height;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font) {
			graphics.fill(0, 0, width, height, argb);
		}
	}

	volatile boolean enabled = true;
	volatile HudContent content = new Box(40, 20, 0xFFFF00FF);

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Test box";
	}

	@Override
	public boolean enabled() {
		return enabled;
	}

	@Override
	public HudContent content() {
		return content;
	}

	@Override
	public HudContent preview() {
		return new Box(40, 20, 0xFF808080);
	}

	@Override
	public HudPosition defaultPosition() {
		return new HudPosition(HudAnchor.TOP_LEFT, 4, 4, 1);
	}
}
