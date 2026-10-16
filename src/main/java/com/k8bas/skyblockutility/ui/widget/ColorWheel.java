package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.color.ColorPickerState;
import com.k8bas.skyblockutility.ui.color.WheelGeometry;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.UiFade;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

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
	public PointerShape cursorAt(double mouseX, double mouseY) {
		return PointerShape.HAND;
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
		float value = state.hsv().v();
		// REQ-UI-31: baked at the wheel's size in screen pixels, drawn 1:1 and sampled linear.
		int size = width * Shapes.guiScale();
		Identifier wheel = WheelTexture.get(size, value);
		if (wheel != null) {
			Shapes.image(graphics, wheel, x, y, size, size, 0xFFFFFFFF);
		} else {
			// REQ-UI-47: the 1.2.1 wheel, one fixed texture scaled onto the wheel's bounds.
			Identifier legacy = WheelTexture.legacy(value);
			if (legacy != null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, legacy, x, y, 0F, 0F, width, height, WheelTexture.LEGACY_SIZE, WheelTexture.LEGACY_SIZE,
						WheelTexture.LEGACY_SIZE, WheelTexture.LEGACY_SIZE, UiFade.apply(0xFFFFFFFF));
			}
		}
		// REQ-UI-30: a white disc with a disc of the picked colour inside it, both from the disc mask.
		int[] marker = marker();
		Shapes.roundedRect(graphics, marker[0] - 4, marker[1] - 4, 8, 8, 4, 0xFFFFFFFF);
		Shapes.roundedRect(graphics, marker[0] - 3, marker[1] - 3, 6, 6, 3, 0xFF000000 | state.hsv().toRgb());
	}

	/** The marker's centre in GUI pixels: where the picked hue and saturation lie on the wheel. */
	int[] marker() {
		double radius = width / 2.0;
		double[] puck = WheelGeometry.puck(state.hsv().h(), state.hsv().s(), radius);
		return new int[] {(int) Math.round(x + radius + puck[0]), (int) Math.round(y + radius + puck[1])};
	}
}
