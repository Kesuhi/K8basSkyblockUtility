package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Keybind capture (REQ-UI-14, R27): shows the binding; a left click arms it and the next key binds,
 * Esc unbinds, a right click resets to the default. While armed a left click cancels and the other
 * mouse buttons bind. A key another mapping also uses is marked in yellow, as vanilla does (R26).
 */
public final class KeybindButton extends Widget {
	private final KeyTarget target;
	private final KeyCapture capture;
	private final Supplier<List<KeyConflicts.KeyState>> allKeys;
	private final Function<String, String> displayName;
	private final Runnable clickSound;
	private boolean forcedArmed;

	/**
	 * @param allKeys     every mapping of the game, for the conflict check
	 * @param displayName a key's name as shown, e.g. "key.keyboard.f7" -> "F7"
	 */
	public KeybindButton(KeyTarget target, Supplier<List<KeyConflicts.KeyState>> allKeys, Function<String, String> displayName,
			Runnable clickSound) {
		this.target = target;
		this.capture = new KeyCapture(target);
		this.allKeys = allKeys;
		this.displayName = displayName;
		this.clickSound = clickSound;
	}

	/** Draws the armed look regardless of input, for screenshots of every state. */
	public void forceArmed(boolean armed) {
		this.forcedArmed = armed;
	}

	public boolean armed() {
		return capture.armed();
	}

	/** The mappings that share this binding. */
	public List<String> conflicts() {
		return KeyConflicts.of(new KeyConflicts.KeyState(target.mapping(), target.bound(), target.defaultKey()), allKeys.get());
	}

	@Override
	public boolean wantsKeyboard() {
		return capture.armed();
	}

	@Override
	public boolean usesTextInput() {
		return false;
	}

	@Override
	public boolean capturesKeys() {
		return true;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Space or Enter arms the capture, as a left click does: the next key binds (REQ-UI-14). */
	@Override
	public boolean activate() {
		if (!enabled) {
			return false;
		}
		capture.arm();
		clickSound.run();
		return true;
	}

	/** Delete resets the default, as a right click does. */
	@Override
	public boolean navKey(NavKey key, boolean fine) {
		if (!enabled || key != NavKey.DELETE) {
			return false;
		}
		capture.reset();
		clickSound.run();
		return true;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || !contains(mouseX, mouseY)) {
			return false;
		}
		if (button == 0) {
			capture.arm();
			clickSound.run();
			return true;
		}
		if (button == 1) {
			capture.reset();
			clickSound.run();
			return true;
		}
		return false;
	}

	@Override
	public void captureKey(String keyName, boolean escape) {
		capture.onKey(keyName, escape);
	}

	@Override
	public void captureMouse(int button, String mouseKeyName) {
		capture.onMouse(button, mouseKeyName);
	}

	@Override
	public void setFocused(boolean focused) {
		if (!focused) {
			capture.cancel();
		}
		super.setFocused(focused);
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		boolean armed = forcedArmed || capture.armed();
		boolean hot = enabled && contains(mouseX, mouseY);
		float hover = hoverAmount(hot);
		Theme theme = Theme.current();
		int fill = enabled ? ColorMath.lerp(Theme.CARD_HOVER, Theme.SEPARATOR, hover) : Theme.CARD;
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(fill));
		Shapes.roundedOutline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, 1, faded(armed ? theme.accent() : Theme.SEPARATOR));
		String bound = target.bound();
		String name = bound.equals(Keybind.UNBOUND) ? "Not bound" : displayName.apply(bound);
		boolean conflict = !armed && !conflicts().isEmpty();
		String text;
		int color;
		if (armed) {
			text = "> " + name + " <";
			color = Theme.CONFLICT_TEXT;
		} else if (conflict) {
			text = "[ " + name + " ]";
			color = Theme.CONFLICT_TEXT;
			graphics.fill(x - 5, y, x - 2, y + height, Theme.CONFLICT_BAR);
		} else {
			text = name;
			color = enabled ? (bound.equals(Keybind.UNBOUND) ? Theme.TEXT_SECONDARY : Theme.TEXT_PRIMARY) : Theme.TEXT_DISABLED;
		}
		String shown = Ellipsis.fit(text, width - 8, font::width);
		UiText.centred(graphics, font, shown, x + width / 2, y + (height - font.lineHeight) / 2 + 1, faded(color));
	}
}
