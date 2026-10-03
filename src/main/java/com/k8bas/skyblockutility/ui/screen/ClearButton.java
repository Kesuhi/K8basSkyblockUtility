package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.BooleanSupplier;

/** The × at the search box's right end (REQ-UI-08): shown while there is text, a click empties the box. */
final class ClearButton extends Widget {
	static final int SIZE = 12;

	private final BooleanSupplier hasText;
	private final Runnable clear;

	ClearButton(BooleanSupplier hasText, Runnable clear) {
		this.hasText = hasText;
		this.clear = clear;
		setTooltip("Clear the search");
	}

	@Override
	public boolean contains(double mouseX, double mouseY) {
		return hasText.getAsBoolean() && super.contains(mouseX, mouseY);
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		clear.run();
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (!hasText.getAsBoolean()) {
			return;
		}
		float hover = hoverAmount(contains(mouseX, mouseY));
		UiText.centred(graphics, font, "×", x + width / 2, y + (height - font.lineHeight) / 2 + 1,
				ColorMath.lerp(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, hover));
	}
}
