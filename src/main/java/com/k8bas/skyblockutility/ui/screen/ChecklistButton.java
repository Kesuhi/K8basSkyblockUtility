package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.Checklist;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.IconShapes;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.NavKey;
import com.k8bas.skyblockutility.ui.widget.PointerShape;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.BooleanSupplier;

/**
 * A checklist's button (REQ-UI-61): the items on out of all ("6/10") and the kit's chevron (REQ-UI-32), drawn as a closed
 * dropdown is, pointing down while the list is closed and up, with the accent outline, while it is open. A click, or Space or
 * Enter with keyboard focus, opens or closes the list under its card; as on a rule header, Right opens it and Left closes it.
 */
final class ChecklistButton extends Widget {
	/** The chevron's size in GUI px, a dropdown's. */
	private static final int ICON = 8;

	private final Checklist list;
	private final Runnable toggle;
	private final BooleanSupplier open;
	private final Runnable clickSound;

	ChecklistButton(Checklist list, Runnable toggle, BooleanSupplier open, Runnable clickSound) {
		this.list = list;
		this.toggle = toggle;
		this.open = open;
		this.clickSound = clickSound;
	}

	/** Its text as drawn: the items on out of all. */
	String label() {
		return list.summary();
	}

	boolean isOpen() {
		return open.getAsBoolean();
	}

	/** The chevron it draws: down while the list is closed, up while it is open (REQ-UI-61). */
	IconShapes.Icon chevron() {
		return isOpen() ? IconShapes.Icon.CHEVRON_UP : IconShapes.Icon.CHEVRON_DOWN;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Space or Enter opens or closes the list, as a click does. */
	@Override
	public boolean activate() {
		if (!enabled) {
			return false;
		}
		flip();
		return true;
	}

	/** As on a rule header: Right opens a closed list, Left closes an open one. */
	@Override
	public boolean navKey(NavKey key, boolean fine) {
		if (key == NavKey.RIGHT || key == NavKey.LEFT) {
			if (enabled && isOpen() == (key == NavKey.LEFT)) {
				flip();
			}
			return true;
		}
		return false;
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

	private void flip() {
		clickSound.run();
		toggle.run();
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Theme theme = Theme.current();
		float hover = hoverAmount(enabled && contains(mouseX, mouseY));
		boolean open = isOpen();
		// REQ-UI-53: held down, mixed toward black.
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(pressed(Theme.SIDEBAR, pressLook(mouseX, mouseY))));
		int outline = !enabled ? Theme.CARD_HOVER : open ? theme.accent() : ColorMath.lerp(Theme.SEPARATOR, Theme.TEXT_DISABLED, hover);
		Shapes.roundedHairline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(outline));
		int textColor = faded(enabled ? Theme.TEXT_PRIMARY : Theme.TEXT_DISABLED);
		String shown = Ellipsis.fit(label(), width - 18, font::width);
		UiText.draw(graphics, font, shown, x + 5, y + (height - font.lineHeight) / 2 + 1, textColor);
		Shapes.icon(graphics, chevron(), x + width - 12, y + (height - ICON) / 2, ICON, textColor);
	}
}
