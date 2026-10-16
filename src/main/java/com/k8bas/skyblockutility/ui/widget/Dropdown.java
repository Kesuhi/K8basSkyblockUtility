package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.IconShapes;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * A single-choice dropdown (REQ-UI-06): a box with the current choice; a click opens the list over
 * everything, a pick applies at once and closes it, a click outside closes it unchanged. Choices are
 * never faked with a slider. Near the bottom the list opens upward; long lists scroll.
 */
public final class Dropdown<E> extends Widget {
	public static final int ROW_HEIGHT = 14;
	/** The chevron's and the check mark's size in GUI px (REQ-UI-32). */
	static final int ICON = 8;
	public static final int MAX_ROWS = 8;

	private final Choice<E> choice;
	private final OverlayHost host;
	private final Runnable clickSound;
	private OpenList list;

	public Dropdown(Choice<E> choice, OverlayHost host, Runnable clickSound) {
		this.choice = choice;
		this.host = host;
		this.clickSound = clickSound;
	}

	public boolean isOpen() {
		return list != null && list.open;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	/** Space or Enter opens the list; the screen has scrolled the box into view first. */
	@Override
	public boolean activate() {
		if (!enabled) {
			return false;
		}
		list = new OpenList();
		host.open(list);
		clickSound.run();
		return true;
	}

	/** On the closed box the arrows, Home and End pick the neighbouring (first, last) choice at once. */
	@Override
	public boolean navKey(NavKey key, boolean fine) {
		if (!enabled) {
			return false;
		}
		List<E> values = choice.values();
		int current = values.indexOf(choice.binding().get());
		int target = FocusCycle.listStep(current, values.size(), key);
		if (target < 0) {
			return false;
		}
		if (target != current) {
			choice.binding().set(values.get(target));
			clickSound.run();
		}
		return true;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		// Half scrolled out of view it does not open: its list would have nothing to hang from.
		if (!enabled || button != 0 || !contains(mouseX, mouseY) || !fullyShowing()) {
			return false;
		}
		list = new OpenList();
		host.open(list);
		clickSound.run();
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Theme theme = Theme.current();
		boolean hot = enabled && contains(mouseX, mouseY);
		float hover = hoverAmount(hot);
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(Theme.SIDEBAR));
		int outline = !enabled ? Theme.CARD_HOVER : isOpen() ? theme.accent() : ColorMath.lerp(Theme.SEPARATOR, Theme.TEXT_DISABLED, hover);
		Shapes.roundedHairline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(outline));
		int textColor = faded(enabled ? Theme.TEXT_PRIMARY : Theme.TEXT_DISABLED);
		String shown = Ellipsis.fit(choice.label().apply(choice.binding().get()), width - 18, font::width);
		UiText.draw(graphics, font, shown, x + 5, y + (height - font.lineHeight) / 2 + 1, textColor);
		// REQ-UI-32: the kit's chevron, pointing down, or up while open.
		Shapes.icon(graphics, isOpen() ? IconShapes.Icon.CHEVRON_UP : IconShapes.Icon.CHEVRON_DOWN, x + width - 12, y + (height - ICON) / 2, ICON,
				textColor);
	}

	/** The open list, drawn over everything. */
	private final class OpenList extends Overlay {
		private final DropdownModel model = new DropdownModel(choice.values().size(), MAX_ROWS);
		private final Rows rows = new Rows();
		private boolean open = true;
		private DropdownPlacement.Placement placement;

		OpenList() {
			model.open(choice.values().indexOf(choice.binding().get()));
		}

		@Override
		public void layout(int screenWidth, int screenHeight) {
			ClipRect area = clip();
			if (area == null) {
				placement = DropdownPlacement.place(x, y, width, height, choice.values().size(), ROW_HEIGHT, MAX_ROWS, screenWidth, screenHeight, 4);
			} else {
				// Inside a scrolled region (a settings card) the list stays in that region, so it never leaves the panel.
				DropdownPlacement.Placement local = DropdownPlacement.place(x - area.x(), y - area.y(), width, height, choice.values().size(),
						ROW_HEIGHT, MAX_ROWS, area.width(), area.height(), 2);
				placement = new DropdownPlacement.Placement(local.x() + area.x(), local.y() + area.y(), local.width(), local.height(), local.rows(),
						local.upward());
			}
			model.setVisible(placement.rows());
			rows.setBounds(placement.x(), placement.y(), placement.width(), placement.height());
		}

		@Override
		public List<Widget> widgets() {
			return List.of(rows);
		}

		@Override
		public boolean contains(double mouseX, double mouseY) {
			return rows.contains(mouseX, mouseY);
		}

		@Override
		public void cancel() {
			open = false;
		}

		@Override
		public boolean key(int key, boolean ctrl, boolean shift) {
			switch (key) {
				case 265 -> model.move(-1);
				case 264 -> model.move(1);
				case 257, 335, 32 -> pick(model.highlighted());
				default -> {
					return false;
				}
			}
			return true;
		}

		@Override
		public boolean stillAnchored() {
			// Scrolled or resized even partly out of its region, the box no longer anchors the list.
			return width > 0 && height > 0 && fullyShowing();
		}

		void pick(int index) {
			choice.binding().set(choice.values().get(index));
			clickSound.run();
			open = false;
			host.close(this);
		}

		/** The rows: a click picks, the wheel scrolls, the mouse highlights. */
		private final class Rows extends Widget {
			@Override
			public boolean press(double mouseX, double mouseY, int button) {
				if (button != 0 || !contains(mouseX, mouseY)) {
					return false;
				}
				int index = model.rowAt(mouseY - y, ROW_HEIGHT);
				if (index >= 0) {
					pick(index);
				}
				return true;
			}

			@Override
			public boolean scroll(double mouseX, double mouseY, int notches, boolean fine) {
				model.scrollBy(-notches);
				return true;
			}

			private int lastMouseX = Integer.MIN_VALUE;
			private int lastMouseY = Integer.MIN_VALUE;

			@Override
			public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
				// The mouse highlights a row only when it moves, so the arrow keys keep theirs under a still pointer.
				if ((mouseX != lastMouseX || mouseY != lastMouseY) && contains(mouseX, mouseY)) {
					model.highlight(model.rowAt(mouseY - y, ROW_HEIGHT));
				}
				lastMouseX = mouseX;
				lastMouseY = mouseY;
				Theme theme = Theme.current();
				Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, Theme.CARD);
				Shapes.roundedHairline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, Theme.SEPARATOR);
				E current = choice.binding().get();
				int shownRows = height / ROW_HEIGHT;
				// The row fills meet the hairline outline flush (T3.22): one hairline in from the box's edges, 1 GUI px between rows.
				float line = Shapes.hairlineGui(Shapes.guiScale());
				float left = x + line;
				float right = x + width - line;
				for (int row = 0; row < shownRows; row++) {
					int index = model.scroll() + row;
					if (index >= choice.values().size()) {
						break;
					}
					E value = choice.values().get(index);
					int rowY = y + row * ROW_HEIGHT;
					float top = row == 0 ? y + line : rowY + 1;
					float bottom = rowY + ROW_HEIGHT == y + height ? y + height - line : rowY + ROW_HEIGHT - 1;
					boolean selected = value.equals(current);
					if (selected) {
						Shapes.fill(graphics, left, top, right, bottom, theme.accentBackground());
						Shapes.fill(graphics, left, top, x + 3, bottom, theme.accent());
					} else if (index == model.highlighted()) {
						Shapes.fill(graphics, left, top, right, bottom, Theme.CARD_HOVER);
					}
					// The selected entry has the kit's check mark at its right end (REQ-UI-32), so its label keeps that free.
					String label = Ellipsis.fit(choice.label().apply(value), width - (selected ? 26 : 14), font::width);
					UiText.draw(graphics, font, label, x + 7, rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1,
							selected ? theme.accent() : Theme.TEXT_PRIMARY);
					if (selected) {
						Shapes.icon(graphics, IconShapes.Icon.CHECK, x + width - 15, rowY + (ROW_HEIGHT - ICON) / 2, ICON, theme.accent());
					}
				}
				int count = choice.values().size();
				if (count > shownRows) {
					// A thin scroll thumb on the right.
					int thumbHeight = Math.max(6, height * shownRows / count);
					int thumbY = y + (height - thumbHeight) * model.scroll() / Math.max(1, count - shownRows);
					Shapes.fill(graphics, x + width - 3, thumbY + 1, right, thumbY + thumbHeight - 1, Theme.TEXT_DISABLED);
				}
			}
		}
	}
}
