package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Animated;
import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.UiClock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A control of the settings screen (T2.3a), drawn with the render kit. Its bounds are set by the
 * layout every frame, so drawing and hit-testing use the same rectangle (REQ-UI-03). Input arrives as
 * plain values from {@link WidgetScreen}, which keeps the behaviour unit-testable. Written from the
 * spec; no AlpakaAddons code (REQ-UI-24).
 */
public abstract class Widget {
	protected int x;
	protected int y;
	protected int width;
	protected int height;
	/** False: takes no input and is drawn in the disabled colours. */
	protected boolean enabled = true;
	/** True: still works, but drawn faded, as the sub-option of a feature that is off (REQ-UI-05). */
	protected boolean dimmed;
	protected boolean focused;
	private Animated hoverAnimation;
	private String tooltip = "";
	/** The region it may draw in and be hit in (a scrolled list), or null for anywhere. */
	private ClipRect clip;

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
	}

	/**
	 * Limits drawing and input to a region, e.g. a scrolled list's viewport: the part outside is not
	 * drawn, and a press, wheel or hover there does not reach the widget (REQ-UI-02, REQ-UI-03).
	 */
	public void setClip(ClipRect clip) {
		this.clip = clip;
	}

	public ClipRect clip() {
		return clip;
	}

	/** Whether a point lies in the clip region (always, without one). */
	public boolean inClip(double mouseX, double mouseY) {
		return clip == null || clip.contains(mouseX, mouseY);
	}

	/** Whether any of it can show: inside its clip region, if it has one. */
	public boolean showing() {
		return clip == null || clip.overlaps(x, y, width, height);
	}

	/** Whether all of it shows: wholly inside its clip region, if it has one. */
	public boolean fullyShowing() {
		return clip == null || x >= clip.x() && y >= clip.y() && x + width <= clip.right() && y + height <= clip.bottom();
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setDimmed(boolean dimmed) {
		this.dimmed = dimmed;
	}

	public boolean isDimmed() {
		return dimmed;
	}

	/** The text shown on hover (REQ-UI-19); "\n" breaks a line, "" for none. */
	public void setTooltip(String tooltip) {
		this.tooltip = tooltip == null ? "" : tooltip;
	}

	/** The tooltip for the mouse over this widget, asked only while it is the topmost one there. */
	public String tooltipAt(double mouseX, double mouseY) {
		return tooltip;
	}

	public abstract void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY);

	/** A mouse press; true if this widget takes it (and then gets the drag and release). */
	public boolean press(double mouseX, double mouseY, int button) {
		return false;
	}

	public void drag(double mouseX, double mouseY) {
	}

	public void release(double mouseX, double mouseY) {
	}

	/** The scroll wheel over this widget; {@code notches} up is positive. True if it used them. */
	public boolean scroll(double mouseX, double mouseY, int notches, boolean fine) {
		return false;
	}

	/** True for widgets that take the keyboard while focused (text fields, keybind capture). */
	public boolean wantsKeyboard() {
		return false;
	}

	/** True for widgets that type text: the game then keeps its input method (IME) on for them. */
	public boolean usesTextInput() {
		return wantsKeyboard();
	}

	/** True for widgets that capture the next key or mouse button as a binding (keybind capture). */
	public boolean capturesKeys() {
		return false;
	}

	/** The captured key, by its options.txt name; Esc is passed as {@code escape}. */
	public void captureKey(String keyName, boolean escape) {
	}

	/** A mouse button pressed while capturing, with its options.txt name. */
	public void captureMouse(int button, String mouseKeyName) {
	}

	public boolean key(int key, boolean ctrl, boolean shift) {
		return false;
	}

	/**
	 * A stop of keyboard navigation (REQ-UI-18, T2.9b), when also enabled and laid out: a control scrolled out of view
	 * still is one, and Tab scrolls it into view.
	 */
	public boolean focusable() {
		return false;
	}

	/** Space or Enter while it has the keyboard focus and nothing is typing; true if it did something. */
	public boolean activate() {
		return false;
	}

	/**
	 * An arrow, Home/End, Page Up/Down or Delete while it has the keyboard focus and nothing is typing (typing keys go
	 * to {@link #key}); {@code fine} with Shift. True if it used the key.
	 */
	public boolean navKey(NavKey key, boolean fine) {
		return false;
	}

	/** Where the focus ring goes: x, y, width, height into {@code out}; 2 px outside the bounds. */
	public void focusRing(int[] out) {
		out[0] = x - 2;
		out[1] = y - 2;
		out[2] = width + 4;
		out[3] = height + 4;
	}

	/** The focus ring's corner radius. */
	public int focusRadius() {
		return Shapes.RADIUS_CONTROL + 2;
	}

	public boolean typed(int codePoint) {
		return false;
	}

	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	public boolean isFocused() {
		return focused;
	}

	/** How hovered the widget looks, easing to 1 or 0 in 150 ms (REQ-UI-17). Created on the first draw, on the game's clock. */
	protected float hoverAmount(boolean hot) {
		if (hoverAnimation == null) {
			hoverAnimation = new Animated(hot ? 1F : 0F, Animated.DEFAULT_DURATION_MS, UiClock.MILLIS);
		}
		hoverAnimation.animateTo(hot ? 1F : 0F);
		return hoverAnimation.value();
	}

	/** A colour faded for a dimmed widget. */
	protected int faded(int argb) {
		return dimmed ? (argb & 0x00FFFFFF) | ((argb >>> 24) * 45 / 100) << 24 : argb;
	}
}
