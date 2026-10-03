package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Theme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A screen of our widgets (T2.3a, T2.3b): lays them out every frame, draws them, and routes input to
 * the one under the mouse, topmost first (REQ-UI-03). The widget that took a press gets that button's
 * drag and release.
 * <ul>
 *   <li>While a text field has the keyboard, every key and its release go to it and none to the game or
 *       other mods (E, the mod's keys, the debug keys; REQ-UI-22); Esc leaves the field.</li>
 *   <li>While a keybind widget is armed, the next key or mouse button is its binding (REQ-UI-14).</li>
 *   <li>One overlay (a dropdown's list, a modal) can be open; it is drawn over everything and gets all
 *       input. A click outside closes a plain overlay unchanged and is not passed on; a modal stays.
 *       Esc closes it without applying anything.</li>
 * </ul>
 * The game checks a few keys (the narrator hotkey) before the screen sees them and turns the input
 * method off each tick unless a vanilla text box is focused, so while one of our text fields has the
 * keyboard an invisible vanilla box is the screen's focused element; it never draws or gets input.
 */
public abstract class WidgetScreen extends Screen implements OverlayHost {
	private static final int KEY_ESCAPE = 256;
	private static final int MOD_SHIFT = 1;

	private final List<Widget> widgets = new ArrayList<>();
	private final Set<Integer> keysTaken = new HashSet<>();
	private Widget pressed;
	private int pressedButton = -1;
	/** A mouse button whose press went to a capture or overlay; its release is not passed on. */
	private int swallowedRelease = -1;
	private Widget keyboardFocus;
	private EditBox textInputGuard;
	private EditBox keyGuard;
	private Overlay overlay;
	/** Scrolling left over from fractional wheel or trackpad events, below one notch. */
	private double scrollRest;

	protected WidgetScreen(Component title) {
		super(title);
	}

	protected <W extends Widget> W add(W widget) {
		widgets.add(widget);
		return widget;
	}

	protected List<Widget> widgets() {
		return widgets;
	}

	/** The widget that has the keyboard, or null. */
	public Widget keyboardFocus() {
		return keyboardFocus;
	}

	/** The open overlay, or null. */
	public Overlay overlay() {
		return overlay;
	}

	@Override
	public void open(Overlay opened) {
		if (overlay != opened) {
			closeOverlay(true);
		}
		focus(null);
		overlay = opened;
		updateFocusGuard();
	}

	@Override
	public void close(Overlay closed) {
		if (overlay == closed) {
			closeOverlay(false);
		}
	}

	/** Gives the keyboard to a widget that wants it, or to none. */
	public void focus(Widget widget) {
		Widget target = widget != null && widget.wantsKeyboard() ? widget : null;
		if (target == keyboardFocus) {
			return;
		}
		if (keyboardFocus != null) {
			keyboardFocus.setFocused(false);
		}
		keyboardFocus = target;
		if (target != null) {
			target.setFocused(true);
		}
		updateFocusGuard();
	}

	/**
	 * The vanilla element the game sees as focused: the text-input guard while a field types (it keeps
	 * the input method on), the key guard while a keybind is armed or an overlay is open (it keeps the
	 * narrator hotkey from firing, without the input method), and nothing otherwise.
	 */
	private void updateFocusGuard() {
		if (keyboardFocus != null && keyboardFocus.usesTextInput()) {
			setFocused(textInputGuard());
		} else if (keyboardFocus != null && keyboardFocus.capturesKeys() || overlay != null) {
			setFocused(keyGuard());
		} else {
			setFocused(null);
		}
	}

	private EditBox textInputGuard() {
		if (textInputGuard == null) {
			textInputGuard = new EditBox(font, 0, 0, 0, 0, Component.empty());
		}
		return textInputGuard;
	}

	private EditBox keyGuard() {
		if (keyGuard == null) {
			keyGuard = new KeyGuard(font);
		}
		return keyGuard;
	}

	/** Looks like a focused text box to the game's narrator check, but never turns the input method on. */
	private static final class KeyGuard extends EditBox {
		KeyGuard(net.minecraft.client.gui.Font font) {
			super(font, 0, 0, 0, 0, Component.empty());
		}

		@Override
		public void setFocused(boolean focused) {
		}

		@Override
		public boolean canConsumeInput() {
			return true;
		}
	}

	/** Closes the open overlay; {@code cancel} first when it closes without applying. */
	private void closeOverlay(boolean cancel) {
		if (overlay == null) {
			return;
		}
		if (cancel) {
			overlay.cancel();
		}
		overlay = null;
		pressed = null;
		pressedButton = -1;
		updateFocusGuard();
	}

	@Override
	public void removed() {
		if (overlay != null) {
			closeOverlay(true);
		}
		focus(null);
		super.removed();
	}

	/** Sets every widget's bounds for this frame. */
	protected abstract void layout();

	/** Draws what lies behind the widgets (panel, labels). */
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		layout();
		// Under an open overlay nothing shows hover.
		int underX = overlay != null ? -1 : mouseX;
		int underY = overlay != null ? -1 : mouseY;
		drawContent(graphics, underX, underY);
		for (Widget widget : widgets) {
			widget.draw(graphics, font, underX, underY);
		}
		if (overlay != null) {
			overlay.layout(width, height);
			if (!overlay.stillAnchored()) {
				closeOverlay(true);
				return;
			}
			graphics.nextStratum();
			if (overlay.modal()) {
				graphics.fill(0, 0, width, height, Theme.BACKDROP);
				graphics.nextStratum();
			}
			overlay.drawFrame(graphics, font, mouseX, mouseY);
			for (Widget widget : overlay.widgets()) {
				widget.draw(graphics, font, mouseX, mouseY);
			}
		}
	}

	private List<Widget> activeWidgets() {
		return overlay != null ? overlay.widgets() : widgets;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// An armed keybind takes the button as its input (R27: left cancels, right resets, others bind).
		if (keyboardFocus != null && keyboardFocus.capturesKeys()) {
			Widget capturing = keyboardFocus;
			capturing.captureMouse(event.button(), KeyMappingTarget.mouseKeyName(event.button()));
			focus(null);
			swallowedRelease = event.button();
			return true;
		}
		List<Widget> active = activeWidgets();
		boolean overWidget = false;
		for (int i = active.size() - 1; i >= 0; i--) {
			Widget widget = active.get(i);
			overWidget |= widget.contains(event.x(), event.y());
			if (widget.press(event.x(), event.y(), event.button())) {
				pressed = widget;
				pressedButton = event.button();
				focus(widget);
				return true;
			}
		}
		if (overlay != null) {
			if (!overlay.modal() && !overlay.contains(event.x(), event.y())) {
				closeOverlay(true);
			}
			swallowedRelease = event.button();
			return true;
		}
		// A left click on nothing leaves the field; a right click on the field keeps it.
		if (event.button() == 0 && !overWidget) {
			focus(null);
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (pressed != null && event.button() == pressedButton) {
			pressed.drag(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressed != null && event.button() == pressedButton) {
			Widget widget = pressed;
			pressed = null;
			pressedButton = -1;
			widget.release(event.x(), event.y());
			return true;
		}
		if (event.button() == swallowedRelease) {
			swallowedRelease = -1;
			return true;
		}
		return super.mouseReleased(event);
	}

	/**
	 * One step per wheel event, whatever the scroll sensitivity; small trackpad movements add up to a
	 * step. Shift gives the fine step (on macOS Shift turns the wheel horizontal, which counts too).
	 * With an overlay open the wheel only reaches the overlay.
	 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		boolean fine = Minecraft.getInstance().hasShiftDown();
		double amount = scrollY != 0 ? scrollY : fine ? scrollX : 0;
		int notches;
		if (Math.abs(amount) >= 1) {
			notches = (int) Math.signum(amount);
			scrollRest = 0;
		} else {
			scrollRest += amount;
			notches = (int) scrollRest;
			scrollRest -= notches;
		}
		if (notches != 0) {
			List<Widget> active = activeWidgets();
			for (int i = active.size() - 1; i >= 0; i--) {
				if (active.get(i).scroll(mouseX, mouseY, notches, fine)) {
					return true;
				}
			}
		}
		return overlay != null || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (keyboardFocus != null && keyboardFocus.capturesKeys()) {
			keysTaken.add(keyId(event));
			Widget capturing = keyboardFocus;
			capturing.captureKey(InputConstants.getKey(event).getName(), event.key() == KEY_ESCAPE);
			focus(null);
			return true;
		}
		if (keyboardFocus != null) {
			keysTaken.add(keyId(event));
			if (event.key() == KEY_ESCAPE) {
				focus(null);
			} else {
				keyboardFocus.key(event.key(), shortcutCtrl(event), shift(event));
			}
			// Taken, so the game does not run the key's mapping or debug keys.
			return true;
		}
		if (overlay != null) {
			keysTaken.add(keyId(event));
			if (event.key() == KEY_ESCAPE) {
				closeOverlay(true);
			} else {
				overlay.key(event.key(), shortcutCtrl(event), shift(event));
			}
			// The screen never closes behind an overlay.
			return true;
		}
		// A repeat of a key taken above (a held Esc) is taken too, so it cannot close the screen.
		if (keysTaken.contains(keyId(event))) {
			return true;
		}
		return super.keyPressed(event);
	}

	/** Releases of keys taken above are taken too; others reach the game, so no key stays held. */
	@Override
	public boolean keyReleased(KeyEvent event) {
		if (keysTaken.remove(keyId(event))) {
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (keyboardFocus != null) {
			keyboardFocus.typed(event.codepoint());
			return true;
		}
		return overlay != null || super.charTyped(event);
	}

	/** A key's identity for press/release pairing; keys without a key code are told apart by scancode. */
	private static int keyId(KeyEvent event) {
		return event.key() != -1 ? event.key() : -1000 - event.scancode();
	}

	/**
	 * Ctrl (Cmd on macOS) for shortcuts and word moves, but not with Alt: Windows reports AltGr, which
	 * types characters such as "ą" or "@", as Ctrl+Alt. Also read from the keyboard, as gametest input
	 * carries no modifiers.
	 */
	private static boolean shortcutCtrl(KeyEvent event) {
		Minecraft client = Minecraft.getInstance();
		boolean ctrl = event.hasControlDownWithQuirk() || client.hasControlDown();
		boolean alt = event.hasAltDown() || client.hasAltDown();
		return ctrl && !alt;
	}

	private static boolean shift(KeyEvent event) {
		return (event.modifiers() & MOD_SHIFT) != 0 || Minecraft.getInstance().hasShiftDown();
	}
}
