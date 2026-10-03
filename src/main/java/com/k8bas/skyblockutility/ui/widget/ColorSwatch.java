package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A colour swatch (REQ-UI-06, REQ-UI-13): shows the stored colour; a click opens the picker over the screen. */
public final class ColorSwatch extends Widget {
	private final Binding<Integer> binding;
	private final boolean storesAlpha;
	private final String title;
	private final OverlayHost host;
	private final Runnable commit;
	private final Runnable clickSound;

	/**
	 * @param binding the stored colour: 0xAARRGGBB if {@code storesAlpha}, else 0xRRGGBB
	 * @param commit  runs once after Save in the picker, to write the file
	 */
	public ColorSwatch(Binding<Integer> binding, boolean storesAlpha, String title, OverlayHost host, Runnable commit, Runnable clickSound) {
		this.binding = binding;
		this.storesAlpha = storesAlpha;
		this.title = title;
		this.host = host;
		this.commit = commit;
		this.clickSound = clickSound;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		clickSound.run();
		host.open(new ColorPickerOverlay(title, binding, storesAlpha, host, commit, clickSound));
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		int colour = binding.get();
		if (storesAlpha) {
			for (int cx = 0; cx < width; cx += 4) {
				for (int cy = 0; cy < height; cy += 4) {
					graphics.fill(x + cx, y + cy, Math.min(x + width, x + cx + 4), Math.min(y + height, y + cy + 4),
							((cx + cy) / 4) % 2 == 0 ? 0xFF9A9A9A : 0xFF5E5E5E);
				}
			}
		}
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(storesAlpha ? colour : 0xFF000000 | colour));
		float hover = hoverAmount(enabled && contains(mouseX, mouseY));
		Shapes.roundedOutline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, 1,
				faded(ColorMath.lerp(Theme.SEPARATOR, Theme.TEXT_PRIMARY, hover)));
	}
}
