package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.render.Clip;
import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.IconShapes;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiClock;
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
	/** The open list's padding at its top and bottom, so its first and last rows stay inside its corners (REQ-UI-40). */
	public static final int LIST_PADDING = 4;

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

	/** The hand; half scrolled out of view, where a press does not open it, the widget below decides. */
	@Override
	public PointerShape cursorAt(double mouseX, double mouseY) {
		return fullyShowing() ? PointerShape.HAND : null;
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

	/** For tests: the open list's scrollbar thumb, {top, length} in GUI px from the screen's top; null when closed or short. */
	int[] openThumb() {
		if (list == null || !list.open || !list.overflows()) {
			return null;
		}
		return new int[] {Math.round(list.rows.y + LIST_PADDING + list.thumbTop()), list.thumbLength()};
	}

	/**
	 * Where an open list from (x, y), w × h, scales in about (REQ-UI-38): the middle of the side that meets its field, so it
	 * grows out of the field, down from its top or up from its bottom.
	 */
	static float[] anchor(int x, int y, int w, int h, boolean upward) {
		return new float[] {x + w / 2F, upward ? y + h : y};
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
				placement = DropdownPlacement.place(x, y, width, height, choice.values().size(), ROW_HEIGHT, MAX_ROWS, screenWidth, screenHeight, 4,
						LIST_PADDING);
			} else {
				// Inside a scrolled region (a settings card) the list stays in that region, so it never leaves the panel.
				DropdownPlacement.Placement local = DropdownPlacement.place(x - area.x(), y - area.y(), width, height, choice.values().size(),
						ROW_HEIGHT, MAX_ROWS, area.width(), area.height(), 2, LIST_PADDING);
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
		public int[] bounds() {
			return new int[] {rows.x, rows.y, rows.x + rows.width, rows.y + rows.height};
		}

		@Override
		public ClipRect shadowClip() {
			return clip();
		}

		@Override
		public float[] anchor() {
			return Dropdown.anchor(rows.x, rows.y, rows.width, rows.height, placement != null && placement.upward());
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

		private boolean overflows() {
			return choice.values().size() > shownRows();
		}

		private int shownRows() {
			return listHeight() / ROW_HEIGHT;
		}

		/** The height the rows have: the list less its padding at each end. */
		private int listHeight() {
			return Math.max(0, rows.height - 2 * LIST_PADDING);
		}

		/** The thumb's length in GUI px: the share of the entries in view, never shorter than a list's thumb. */
		private int thumbLength() {
			return Math.min(listHeight(), Math.max(VirtualRows.MIN_THUMB, listHeight() * shownRows() / choice.values().size()));
		}

		/** The thumb's top from the list's, in GUI px, where the list is shown now. */
		private float thumbTop() {
			return (listHeight() - thumbLength()) * model.shownRows() / Math.max(1, choice.values().size() - shownRows());
		}

		/** The rows: a click picks, the wheel scrolls, the mouse highlights. */
		private final class Rows extends Widget {
			private final ScrollbarPainter scrollbar = new ScrollbarPainter();
			/** The highlighted row's look, eased in and out as a card's hover (REQ-UI-45). */
			private final RowHover highlight = new RowHover(UiClock.SOURCE);
			/** While the thumb is dragged: where it was grabbed, from its top; else -1. */
			private int grab = -1;

			/** The hand over an entry and the scrollbar; the arrow over the padding at the list's ends. */
			@Override
			public PointerShape cursorAt(double mouseX, double mouseY) {
				if (overflows() && ScrollbarPainter.inHitArea(x, width, mouseX)) {
					return PointerShape.HAND;
				}
				return model.rowAt(mouseY - y - LIST_PADDING, ROW_HEIGHT) >= 0 ? PointerShape.HAND : PointerShape.DEFAULT;
			}

			/** While its thumb is dragged, the vertical-resize pointer (REQ-UI-43). */
			@Override
			public PointerShape dragCursor() {
				return grab >= 0 ? PointerShape.RESIZE_VERTICAL : null;
			}

			@Override
			public boolean press(double mouseX, double mouseY, int button) {
				grab = -1;
				if (button != 0 || !contains(mouseX, mouseY)) {
					return false;
				}
				// The scrollbar (REQ-UI-36): the thumb is dragged, a press on the track puts its middle there.
				if (overflows() && ScrollbarPainter.inHitArea(x, width, mouseX)) {
					int along = (int) Math.floor(mouseY - y - LIST_PADDING);
					int top = Math.round(thumbTop());
					grab = along >= top && along < top + thumbLength() ? along - top : thumbLength() / 2;
					dragTo(along);
					return true;
				}
				int index = model.rowAt(mouseY - y - LIST_PADDING, ROW_HEIGHT);
				if (index >= 0) {
					pick(index);
				}
				return true;
			}

			@Override
			public void drag(double mouseX, double mouseY) {
				if (grab >= 0) {
					dragTo((int) Math.floor(mouseY - y - LIST_PADDING));
				}
			}

			@Override
			public void release(double mouseX, double mouseY) {
				grab = -1;
			}

			/** While its thumb is dragged the wheel goes nowhere (EC-UI-25). */
			@Override
			public boolean blocksWheel() {
				return grab >= 0;
			}

			/** The list at the row whose thumb top lies at {@code along} less the grab, in whole rows. */
			private void dragTo(int along) {
				int track = listHeight() - thumbLength();
				int range = choice.values().size() - shownRows();
				model.scrollTo(track <= 0 ? 0 : Math.round((float) (along - grab) * range / track));
			}

			@Override
			public boolean scroll(double mouseX, double mouseY, int notches, boolean fine) {
				// While the thumb is dragged it follows the pointer, and the wheel is ignored (EC-UI-25).
				if (grab < 0) {
					model.scrollBy(-notches);
				}
				return true;
			}

			private int lastMouseX = Integer.MIN_VALUE;
			private int lastMouseY = Integer.MIN_VALUE;

			@Override
			public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
				int scale = Shapes.guiScale();
				model.setScale(scale);
				// The mouse highlights a row only when it moves, so the arrow keys keep theirs under a still pointer.
				if ((mouseX != lastMouseX || mouseY != lastMouseY) && contains(mouseX, mouseY)) {
					model.highlight(model.rowAt(mouseY - y - LIST_PADDING, ROW_HEIGHT));
				}
				lastMouseX = mouseX;
				lastMouseY = mouseY;
				highlight.hover(model.highlighted());
				Theme theme = Theme.current();
				Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, Theme.CARD);
				E current = choice.binding().get();
				// The row fills meet the hairline outline flush (T3.22): one hairline in from the box's edges, 1 GUI px between rows.
				float line = Shapes.hairlineGui(scale);
				float left = x + line;
				float right = x + width - line;
				int count = choice.values().size();
				// Laid out at whole GUI px, where clicks meet the rows; the rest of a glide moves them on the physical-pixel grid
				// (REQ-UI-35), and a row in part at either end is cut at the box.
				int laidOut = model.laidOutPx(ROW_HEIGHT);
				float shift = model.drawShift(ROW_HEIGHT, scale);
				try (Clip clip = Clip.push(graphics, x, y + LIST_PADDING, width, listHeight())) {
					if (clip.visible()) {
						graphics.pose().pushMatrix();
						try {
							graphics.pose().translate(0F, shift);
							for (int index = Math.max(0, laidOut / ROW_HEIGHT - 1); index < count; index++) {
								int rowY = y + LIST_PADDING + index * ROW_HEIGHT - laidOut;
								if (rowY >= y + height + ROW_HEIGHT) {
									break;
								}
								drawRow(graphics, font, theme, index, rowY, line, left, right, current);
							}
						} finally {
							graphics.pose().popMatrix();
						}
						// REQ-UI-56: the rows fade into the list at an edge with more beyond, below its scrollbar.
						EdgeFade.draw(graphics, x, y + LIST_PADDING, width, listHeight(), model.shownRows() * ROW_HEIGHT,
								Math.max(0, count * ROW_HEIGHT - listHeight()), Theme.CARD);
					}
				}
				Shapes.roundedHairline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, Theme.SEPARATOR);
				if (overflows()) {
					// The kit's scrollbar (REQ-UI-36), wide while the pointer is over it or its thumb is dragged.
					boolean hot = grab >= 0 || contains(mouseX, mouseY) && ScrollbarPainter.inHitArea(x, width, mouseX);
					scrollbar.draw(graphics, x, y + LIST_PADDING, width, listHeight(), thumbTop(), thumbLength(), hot, argb -> argb);
				}
			}

			private void drawRow(GuiGraphicsExtractor graphics, Font font, Theme theme, int index, int rowY, float line, float left, float right,
					E current) {
				E value = choice.values().get(index);
				// 1 GUI px between rows; the padding keeps the first and last inside the list's corners.
				float top = rowY + 1;
				float bottom = rowY + ROW_HEIGHT - 1;
				boolean selected = value.equals(current);
				if (selected) {
					Shapes.fill(graphics, left, top, right, bottom, theme.accentBackground());
					Shapes.fill(graphics, left, top, x + 3, bottom, theme.accent());
				} else {
					float amount = highlight.amount(index);
					if (amount > 0F) {
						Shapes.fill(graphics, left, top, right, bottom, ColorMath.withAlpha(Math.round(255 * amount), Theme.CARD_HOVER));
					}
				}
				// The selected entry has the kit's check mark at its right end (REQ-UI-32), so its label keeps that free.
				String label = Ellipsis.fit(choice.label().apply(value), width - (selected ? 26 : 14), font::width);
				UiText.draw(graphics, font, label, x + 7, rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1, selected ? theme.accent() : Theme.TEXT_PRIMARY);
				if (selected) {
					Shapes.icon(graphics, IconShapes.Icon.CHECK, x + width - 15, rowY + (ROW_HEIGHT - ICON) / 2, ICON, theme.accent());
				}
			}
		}
	}
}
