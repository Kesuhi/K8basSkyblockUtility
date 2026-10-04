package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A rule's header (REQ-UI-11): a chevron, the rule's colour dot and its label, cut with "..." when long
 * and "(unnamed)" when empty (EC-UI-07). A rule that does nothing has a warning sign before its label
 * (REQ-GLOW-10). A click anywhere on it opens or closes the rule.
 */
final class RuleHeaderWidget extends Widget {
	static final String UNNAMED = "(unnamed)";
	static final String WARNING = "⚠ ";
	private static final int DOT = 8;

	private final RuleGroup rule;
	private final Runnable toggle;
	private boolean expanded;

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

	/** The label as the header shows it, before any cut. */
	String title() {
		String label = rule.label().get();
		return label == null || label.isBlank() ? UNNAMED : label.strip();
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		toggle.run();
		return true;
	}

	@Override
	public String tooltipAt(double mouseX, double mouseY) {
		String problem = rule.problem().get();
		return problem == null ? "" : ConfigLayout.problemText(problem);
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float hover = hoverAmount(contains(mouseX, mouseY));
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CARD, faded(ColorMath.lerp(Theme.CARD, Theme.CARD_HOVER, hover)));
		int midY = y + height / 2;
		drawChevron(graphics, x + 10, midY);
		Shapes.roundedRect(graphics, x + 22, midY - DOT / 2, DOT, DOT, DOT / 2, faded(0xFF000000 | rule.colour().getAsInt()));
		int textX = x + 22 + DOT + 6;
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int room = x + width - 8 - textX;
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
