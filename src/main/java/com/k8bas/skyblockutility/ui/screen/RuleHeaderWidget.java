package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.NavKey;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A rule's header (REQ-UI-11): a chevron, the rule's colour dot and its label, cut with "..." when long
 * and "(unnamed)" when empty (EC-UI-07). A rule that does nothing has a warning sign before its label
 * (REQ-GLOW-10). A click anywhere on it opens or closes the rule, except on its Remove button, which the
 * page places over its right end (R31); the label stops before that button.
 */
final class RuleHeaderWidget extends Widget {
	static final String UNNAMED = "(unnamed)";
	static final String WARNING = "⚠ ";
	private static final int DOT = 8;

	private final RuleGroup rule;
	private final Runnable toggle;
	private boolean expanded;
	/** The width kept free at the right end for the Remove button, 0 when there is none. */
	private int actionRoom;

	RuleHeaderWidget(RuleGroup rule, Runnable toggle) {
		this.rule = rule;
		this.toggle = toggle;
	}

	void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	boolean expanded() {
		return expanded;
	}

	void setActionRoom(int room) {
		actionRoom = Math.max(0, room);
	}

	/** Where the label must end: before the Remove button, or 8 px before the right edge without one. */
	int textRight() {
		return x + width - 8 - actionRoom;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Space or Enter opens or closes the rule, as a click does (REQ-UI-18, T2.9b). */
	@Override
	public boolean activate() {
		toggle.run();
		return true;
	}

	/** As in a tree: Right opens a closed rule, Left closes an open one. */
	@Override
	public boolean navKey(NavKey key, boolean fine) {
		if (key == NavKey.RIGHT) {
			if (!expanded) {
				toggle.run();
			}
			return true;
		}
		if (key == NavKey.LEFT) {
			if (expanded) {
				toggle.run();
			}
			return true;
		}
		return false;
	}

	/** Around the header, not its Remove button (a stop of its own). */
	@Override
	public void focusRing(int[] out) {
		out[0] = x - 2;
		out[1] = y - 2;
		out[2] = width - actionRoom + 4;
		out[3] = height + 4;
	}

	@Override
	public int focusRadius() {
		return Shapes.RADIUS_CARD + 2;
	}

	/** Whether the point is in the Remove button's area, which is the button's alone: no toggle, hover or tooltip here. */
	boolean overAction(double mouseX) {
		return actionRoom > 0 && mouseX >= x + width - actionRoom;
	}

	/** The label as the header shows it, before any cut. */
	String title() {
		String label = rule.label().get();
		return label == null || label.isBlank() ? UNNAMED : label.strip();
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY) || overAction(mouseX)) {
			return false;
		}
		toggle.run();
		return true;
	}

	@Override
	public String tooltipAt(double mouseX, double mouseY) {
		String problem = rule.problem().get();
		return problem == null || overAction(mouseX) ? "" : ConfigLayout.problemText(problem);
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float hover = hoverAmount(contains(mouseX, mouseY) && !overAction(mouseX));
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CARD, faded(ColorMath.lerp(Theme.CARD, Theme.CARD_HOVER, hover)));
		int midY = y + height / 2;
		drawChevron(graphics, x + 10, midY);
		Shapes.roundedRect(graphics, x + 22, midY - DOT / 2, DOT, DOT, DOT / 2, faded(0xFF000000 | rule.colour().getAsInt()));
		int textX = x + 22 + DOT + 6;
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int room = textRight() - textX;
		if (rule.problem().get() != null) {
			UiText.draw(graphics, font, WARNING, textX, textY, faded(Theme.ERROR));
			int warning = font.width(WARNING);
			textX += warning;
			room -= warning;
		}
		String title = title();
		UiText.draw(graphics, font, Ellipsis.fit(title, room, font::width), textX, textY,
				faded(title.equals(UNNAMED) ? Theme.TEXT_SECONDARY : Theme.TEXT_PRIMARY));
	}

	/** A small triangle: pointing right while closed, down while open. */
	private void drawChevron(GuiGraphicsExtractor graphics, int cx, int cy) {
		int colour = faded(Theme.TEXT_SECONDARY);
		for (int i = 0; i < 4; i++) {
			if (expanded) {
				graphics.fill(cx - 3 + i, cy - 2 + i, cx + 4 - i, cy - 1 + i, colour);
			} else {
				graphics.fill(cx - 2 + i, cy - 3 + i, cx - 1 + i, cy + 4 - i, colour);
			}
		}
	}
}
