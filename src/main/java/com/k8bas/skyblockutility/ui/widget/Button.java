package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A button in the normal, primary or destructive style (REQ-UI-06). It acts on release inside itself
 * after a press inside itself, as buttons usually do; its label stays readable in every state (EC-UI-15).
 */
public final class Button extends Widget {
	public enum Style {
		NORMAL, PRIMARY, DESTRUCTIVE
	}

	/** A state to draw regardless of the mouse, for screenshots of every state. */
	public enum Look {
		LIVE, HOVER, PRESSED
	}

	private final String label;
	private final Style style;
	private final Runnable action;
	private final Runnable clickSound;
	private boolean pressed;
	private Look look = Look.LIVE;

	public Button(String label, Style style, Runnable action, Runnable clickSound) {
		this.label = label;
		this.style = style;
		this.action = action;
		this.clickSound = clickSound;
	}

	public void setLook(Look look) {
		this.look = look;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Space or Enter: the action, as a click does. */
	@Override
	public boolean activate() {
		if (!enabled) {
			return false;
		}
		clickSound.run();
		action.run();
		return true;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		pressed = true;
		return true;
	}

	@Override
	public void release(double mouseX, double mouseY) {
		boolean wasPressed = pressed;
		pressed = false;
		if (wasPressed && enabled && contains(mouseX, mouseY)) {
			clickSound.run();
			action.run();
		}
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Theme theme = Theme.current();
		boolean down = look == Look.PRESSED || look == Look.LIVE && pressed && contains(mouseX, mouseY);
		boolean hot = !down && (look == Look.HOVER || look == Look.LIVE && enabled && contains(mouseX, mouseY));
		// A forced look shows its end state at once; a live hover eases in (REQ-UI-17).
		float hover = look == Look.LIVE ? hoverAmount(hot) : hot ? 1F : 0F;
		int fill;
		int text;
		if (!enabled) {
			fill = Theme.CARD;
			text = Theme.TEXT_DISABLED;
		} else {
			switch (style) {
				case PRIMARY -> {
					fill = down ? theme.accentPressed() : ColorMath.lerp(theme.accent(), theme.accentHover(), hover);
					text = theme.textOnAccent();
				}
				case DESTRUCTIVE -> {
					fill = down ? Theme.DESTRUCTIVE_PRESSED : ColorMath.lerp(Theme.DESTRUCTIVE, Theme.DESTRUCTIVE_HOVER, hover);
					text = Theme.TEXT_PRIMARY;
				}
				default -> {
					fill = down ? Theme.CARD : ColorMath.lerp(Theme.CARD_HOVER, Theme.SEPARATOR, hover);
					text = Theme.TEXT_PRIMARY;
				}
			}
		}
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(fill));
		if (style == Style.NORMAL || !enabled) {
			Shapes.roundedOutline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, 1, faded(Theme.SEPARATOR));
		} else if (style == Style.DESTRUCTIVE) {
			// A light rim, so a destructive button differs from a primary one even with a red accent (EC-UI-15).
			Shapes.roundedOutline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, 1, faded(ColorMath.withAlpha(0x70, Theme.TEXT_PRIMARY)));
		}
		String shown = Ellipsis.fit(label, width - 8, font::width);
		UiText.centred(graphics, font, shown, x + width / 2, y + (height - font.lineHeight) / 2 + 1, faded(text));
	}
}
