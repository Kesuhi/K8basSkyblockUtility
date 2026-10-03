package com.k8bas.skyblockutility.ui.widget;

/**
 * Where a dropdown's list goes (REQ-UI-06): under its box if all rows fit, otherwise on the side with
 * more room, never off screen, at most {@code maxRows} rows. It is drawn over the cards either way.
 */
public final class DropdownPlacement {
	public record Placement(int x, int y, int width, int height, int rows, boolean upward) {
	}

	private DropdownPlacement() {
	}

	public static Placement place(int anchorX, int anchorY, int anchorWidth, int anchorHeight, int count, int rowHeight, int maxRows,
			int screenWidth, int screenHeight, int margin) {
		int wanted = Math.max(1, Math.min(count, maxRows));
		int below = screenHeight - margin - (anchorY + anchorHeight);
		int above = anchorY - margin;
		boolean upward = wanted * rowHeight > below && above > below;
		int space = upward ? above : below;
		int rows = Math.max(1, Math.min(wanted, space / rowHeight));
		int height = rows * rowHeight;
		int width = Math.min(anchorWidth, screenWidth - 2 * margin);
		int x = Math.max(margin, Math.min(anchorX, screenWidth - margin - width));
		int y = upward ? anchorY - height : anchorY + anchorHeight;
		y = Math.max(margin, Math.min(y, screenHeight - margin - height));
		return new Placement(x, y, width, height, rows, upward);
	}
}
