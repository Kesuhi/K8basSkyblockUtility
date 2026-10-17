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
	public PointerShape cursorAt(double mouseX, double mouseY) {
		return PointerShape.HAND;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		return activate();
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Space, Enter or a click opens the colour picker. */
	@Override
	public boolean activate() {
		if (!enabled) {
			return false;
		}
		clickSound.run();
		host.open(new ColorPickerOverlay(title, binding, storesAlpha, host, commit, clickSound));
		return true;
	}

	/** The board's light and dark colours as this swatch draws them (dimmed with it), for tests. */
	int[] boardColoursForTests() {
		return new int[] {faded(Checkerboard.LIGHT), faded(Checkerboard.DARK)};
	}

	/** A colour as this swatch draws it, for tests. */
	int fadedForTests(int argb) {
		return faded(argb);
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		int colour = binding.get();
		if (storesAlpha) {
			// REQ-UI-60: the board in the swatch's rounded outline, dimmed with it.
			Checkerboard.draw(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, this::faded);
		}
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(storesAlpha ? colour : 0xFF000000 | colour));
		float hover = hoverAmount(enabled && contains(mouseX, mouseY));
		Shapes.roundedHairline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL,
				faded(ColorMath.lerp(Theme.SEPARATOR, Theme.TEXT_PRIMARY, hover)));
	}
}
