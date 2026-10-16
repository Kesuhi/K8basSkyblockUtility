package com.k8bas.skyblockutility.ui.widget;

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
