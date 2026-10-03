package com.k8bas.skyblockutility.ui.widget;

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
 * A screen of our widgets (T2.3a): lays them out every frame, draws them, and routes input to the one
 * under the mouse, topmost first (REQ-UI-03). The widget that took a press gets that button's drag and
 * release. While a text field has the keyboard, every key goes to it and none to the game or other
 * mods (E, the mod's keys, the debug keys; REQ-UI-22); Esc then leaves the field and keeps the screen
 * open.
 * <p>
 * The game checks a few keys (the narrator hotkey) before the screen sees them, and switches the input
 * method (for Chinese or Japanese typing) off each tick, unless a vanilla text box is focused. So while
 * one of our fields has the keyboard, an invisible vanilla box is the screen's focused element; it
 * never draws and never gets input.
 */
public abstract class WidgetScreen extends Screen {
	private static final int KEY_ESCAPE = 256;
	private static final int MOD_SHIFT = 1;

	private final List<Widget> widgets = new ArrayList<>();
	private final Set<Integer> keysTakenByField = new HashSet<>();
	private Widget pressed;
	private int pressedButton = -1;
	private Widget keyboardFocus;
	private EditBox textInputGuard;
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
			setFocused(textInputGuard());
		} else {
			setFocused(null);
			keysTakenByField.clear();
		}
	}

	private EditBox textInputGuard() {
		if (textInputGuard == null) {
			textInputGuard = new EditBox(font, 0, 0, 0, 0, Component.empty());
		}
		return textInputGuard;
	}

	@Override
	public void removed() {
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
		drawContent(graphics, mouseX, mouseY);
		for (Widget widget : widgets) {
			widget.draw(graphics, font, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		boolean overWidget = false;
		for (int i = widgets.size() - 1; i >= 0; i--) {
			Widget widget = widgets.get(i);
			overWidget |= widget.contains(event.x(), event.y());
			if (widget.press(event.x(), event.y(), event.button())) {
				pressed = widget;
				pressedButton = event.button();
				focus(widget);
				return true;
			}
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
		return super.mouseReleased(event);
	}

	/**
	 * One step per wheel event, whatever the scroll sensitivity; small trackpad movements add up to a
	 * step. Shift gives the fine step (on macOS Shift turns the wheel horizontal, which counts too).
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
			for (int i = widgets.size() - 1; i >= 0; i--) {
				if (widgets.get(i).scroll(mouseX, mouseY, notches, fine)) {
					return true;
				}
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (keyboardFocus != null) {
			if (event.key() == KEY_ESCAPE) {
				focus(null);
			} else {
				keysTakenByField.add(event.key());
				keyboardFocus.key(event.key(), shortcutCtrl(event), shift(event));
			}
			// Taken, so the game does not run the key's mapping or debug keys.
			return true;
		}
		return super.keyPressed(event);
	}

	/** Releases of keys the field took are taken too; others reach the game, so no key stays held. */
	@Override
	public boolean keyReleased(KeyEvent event) {
		if (keysTakenByField.remove(event.key())) {
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
		return super.charTyped(event);
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
