package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.IconShapes;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.PointerShape;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Supplier;

/**
 * One item of an open checklist (REQ-UI-61): a check box, filled in the accent with the check mark while the item is on and
 * outlined in the off switch's grey while it is off, and the item's label, whose search matches sit on tinted boxes
 * (REQ-UI-51). A click anywhere on the row, or Space or Enter with keyboard focus, flips the item at once, as a toggle card
 * does; its tooltip is the item's description. Placed and clipped by its page like a rule's field.
 */
final class CheckRowWidget extends Widget {
	/** The check box's side and its corner radius, in GUI px. */
	static final int BOX = 10;
	private static final int BOX_RADIUS = 3;
	/** The check box's left edge, and the gap after it, from the row's left edge. */
	private static final int INSET = 6;

	private final Toggle item;
	/** The query as the search sees it; its matches in the label are marked (REQ-UI-51). */
	private final Supplier<String> query;
	private final Runnable clickSound;
	/** The match boxes as drawn last, {x, y, w, h} in GUI px (for gametests). */
	private List<int[]> drawnMarks = List.of();

	CheckRowWidget(Toggle item, Supplier<String> query, Runnable clickSound) {
		this.item = item;
		this.query = query;
		this.clickSound = clickSound;
	}

	Toggle item() {
		return item;
	}

	/** The match boxes as drawn last, {x, y, w, h} in GUI px (for gametests). */
	List<int[]> marksForTests() {
		return drawnMarks;
	}

	/** The check box as drawn, {x, y, side} in GUI px (for gametests). */
	int[] boxForTests() {
		return new int[] {x + INSET, y + (height - BOX) / 2, BOX};
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Space or Enter flips the item, as a click does (REQ-UI-18). */
	@Override
	public boolean activate() {
		if (!enabled) {
			return false;
		}
		flip();
		return true;
	}

	@Override
	public PointerShape cursorAt(double mouseX, double mouseY) {
		return PointerShape.HAND;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		held = true;
		flip();
		return true;
	}

	@Override
	public void release(double mouseX, double mouseY) {
		held = false;
	}

	@Override
	public String tooltipAt(double mouseX, double mouseY) {
		return item.text().description();
	}

	private void flip() {
		clickSound.run();
		item.binding().set(!item.binding().get());
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Theme theme = Theme.current();
		float hover = hoverAmount(enabled && contains(mouseX, mouseY));
		// The eased hover of a list's row (REQ-UI-45), held down mixed toward black (REQ-UI-53).
		if (hover > 0F) {
			int background = pressed(ColorMath.withAlpha(Math.round(255 * hover), Theme.CARD_HOVER), pressLook(mouseX, mouseY));
			Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(background));
		}
		boolean on = item.binding().get();
		int boxX = x + INSET;
		int boxY = y + (height - BOX) / 2;
		if (on) {
			Shapes.roundedRect(graphics, boxX, boxY, BOX, BOX, BOX_RADIUS, faded(enabled ? theme.accent() : Theme.TEXT_DISABLED));
			Shapes.icon(graphics, IconShapes.Icon.CHECK, boxX + 1, boxY + 1, BOX - 2, faded(theme.textOnAccent()));
		} else {
			Shapes.roundedRect(graphics, boxX, boxY, BOX, BOX, BOX_RADIUS, faded(Theme.SIDEBAR));
			Shapes.roundedHairline(graphics, boxX, boxY, BOX, BOX, BOX_RADIUS, faded(ColorMath.lerp(Theme.TOGGLE_OFF, Theme.TEXT_SECONDARY, hover)));
		}
		int textX = boxX + BOX + INSET;
		int textY = y + (height - font.lineHeight) / 2 + 1;
		String title = item.text().title();
		String shown = Ellipsis.fit(title, x + width - INSET - textX, font::width);
		drawnMarks = OptionCard.drawMarks(graphics, font, title, List.of(shown), !shown.equals(title), query.get(), textX, textY, 0,
				faded(OptionCard.markColour(theme.accent())));
		UiText.draw(graphics, font, shown, textX, textY, faded(enabled ? on ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY : Theme.TEXT_DISABLED));
	}
}
