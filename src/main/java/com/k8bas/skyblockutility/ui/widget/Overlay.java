package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.render.Shapes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Something drawn over the whole screen's widgets and given all input while open: a dropdown's list or
 * a modal such as the colour picker. A modal dims the screen behind it and stays open on a click
 * outside; a plain overlay closes on one, without applying anything.
 */
public abstract class Overlay {
	/** Places the overlay's widgets for this frame. */
	public abstract void layout(int screenWidth, int screenHeight);

	public abstract List<Widget> widgets();

	public abstract boolean contains(double mouseX, double mouseY);

	public boolean modal() {
		return false;
	}

	/** Draws what lies behind the overlay's widgets (its panel). */
	public void drawFrame(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
	}

	/** Closed without applying (outside click, Esc, the screen closing). */
	public void cancel() {
	}

	/** A key while open (Esc is handled by the screen); true if used. */
	public boolean key(int key, boolean ctrl, boolean shift) {
		return false;
	}

	/**
	 * The box it is drawn in, {left, top, right, bottom} in GUI px, which its open transition scales (REQ-UI-38); null to draw
	 * it at once, unscaled.
	 */
	public int[] bounds() {
		return null;
	}

	/** The corner radius of its box in GUI px, for its shadow (REQ-UI-41): a control's unless it says otherwise. */
	public int radius() {
		return Shapes.RADIUS_CONTROL;
	}

	/**
	 * The region its shadow is cut to, or null for the window: a dropdown list's, which lies in its card's region, so its
	 * shadow stays inside the panel (REQ-UI-02).
	 */
	public ClipRect shadowClip() {
		return null;
	}

	/** The point its open transition scales it about, {x, y} in GUI px: a dialog's centre unless it says otherwise. */
	public float[] anchor() {
		int[] box = bounds();
		return box == null ? null : new float[] {(box[0] + box[2]) / 2F, (box[1] + box[3]) / 2F};
	}

	/** False once what it belongs to is gone from view; the screen then closes it. */
	public boolean stillAnchored() {
		return true;
	}
}
