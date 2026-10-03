package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.color.ColorPickerState;
import com.k8bas.skyblockutility.ui.color.WheelGeometry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * The picker's hue/saturation wheel (REQ-UI-13): a press inside the circle picks (hit-testing matches
 * the drawing, REQ-UI-03), and a drag goes on picking also outside it.
 */
final class ColorWheel extends Widget {
	private final ColorPickerState state;
	private boolean dragging;

	ColorWheel(ColorPickerState state) {
		this.state = state;
	}

	@Override
	public boolean contains(double mouseX, double mouseY) {
		double radius = width / 2.0;
		double dx = mouseX - (x + radius);
		double dy = mouseY - (y + radius);
		return dx * dx + dy * dy <= radius * radius;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		dragging = true;
		pick(mouseX, mouseY);
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		if (dragging) {
			pick(mouseX, mouseY);
		}
	}

	@Override
	public void release(double mouseX, double mouseY) {
		dragging = false;
	}

	private void pick(double mouseX, double mouseY) {
		double radius = width / 2.0;
		float[] hs = WheelGeometry.hsAt(mouseX - (x + radius), mouseY - (y + radius), radius, state.hsv().h());
		state.setWheel(hs[0], hs[1]);
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		WheelTexture.ensureValue(state.hsv().v());
		// The whole texture, scaled onto the wheel's bounds.
		graphics.blit(RenderPipelines.GUI_TEXTURED, WheelTexture.ID, x, y, 0F, 0F, width, height, WheelTexture.SIZE, WheelTexture.SIZE,
				WheelTexture.SIZE, WheelTexture.SIZE);
		double radius = width / 2.0;
		double[] puck = WheelGeometry.puck(state.hsv().h(), state.hsv().s(), radius);
		int px = (int) Math.round(x + radius + puck[0]);
		int py = (int) Math.round(y + radius + puck[1]);
		graphics.fill(px - 4, py - 4, px + 4, py + 4, 0xFFFFFFFF);
		graphics.fill(px - 3, py - 3, px + 3, py + 3, 0xFF000000 | state.hsv().toRgb());
	}
}
