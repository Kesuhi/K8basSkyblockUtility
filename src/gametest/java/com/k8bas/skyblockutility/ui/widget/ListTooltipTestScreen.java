package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.TooltipLayout;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * T2.3c's test screen, for gametests only: a button in the bottom-right corner with a 6-line tooltip,
 * and a list of 5,000 rows.
 */
public final class ListTooltipTestScreen extends WidgetScreen {
	static final int ROWS = 5000;
	static final int ROW_HEIGHT = 18;

	final Button corner;
	final VirtualList list;
	volatile int clicked = -1;

	public ListTooltipTestScreen() {
		super(Component.literal("List and tooltip test"));
		corner = add(new Button("?", Button.Style.NORMAL, () -> { }, () -> { }));
		corner.setTooltip("§eSix lines\nThe second line\nA third, somewhat longer line of text\nFour\nFive\nAnd the sixth");
		list = add(new VirtualList(ROW_HEIGHT, () -> ROWS, (graphics, font, index, x, y, w, h, hovered) -> {
			paintingRows++;
			graphics.fill(x, y, x + w, y + h - 1, hovered ? Theme.CARD_HOVER : index % 2 == 0 ? Theme.CARD : Theme.SIDEBAR);
			UiText.draw(graphics, font, "Row " + index, x + 4, y + 5, Theme.TEXT_PRIMARY);
		}, index -> clicked = index));
		list.setRowTooltip(index -> "Row " + index + " in full");
	}

	/** Rows painted in the frame being drawn, and in the last whole frame. */
	private int paintingRows;
	volatile int paintedRows;

	static final int LABEL_X = 200;
	static final int LABEL_Y = 8;

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		paintedRows = paintingRows;
		paintingRows = 0;
		// A label that is not a widget, with its tooltip asked for while hovered.
		UiText.draw(graphics, font, "Label", LABEL_X, LABEL_Y, Theme.TEXT_SECONDARY);
		if (mouseX >= LABEL_X && mouseX < LABEL_X + font.width("Label") && mouseY >= LABEL_Y && mouseY < LABEL_Y + font.lineHeight) {
			showTooltip("The whole label");
		}
	}

	int[] label() {
		return new int[] {windowX(LABEL_X + 3), windowY(LABEL_Y + 3)};
	}

	@Override
	protected void layout() {
		corner.setBounds(width - 20, height - 20, 16, 16);
		list.setBounds(8, 8, 160, Math.min(200, height - 40));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, Theme.PANEL);
	}

	TooltipLayout.Box tooltip() {
		return lastTooltip();
	}

	/** A widget's centre in window pixels. */
	int[] centre(Widget widget) {
		return new int[] {windowX(widget.x + widget.width / 2), windowY(widget.y + widget.height / 2)};
	}

	/** The centre of visible row {@code row} counted from the first visible one, in window pixels. */
	int[] visibleRow(int row) {
		int index = list.rows().visible().first() + row;
		return new int[] {windowX(list.x + 40), windowY(list.y + list.rows().rowTop(index) + ROW_HEIGHT / 2)};
	}

	/** A point on the list's thumb track, by a fraction of its height, in window pixels. */
	int[] track(double fraction) {
		return new int[] {windowX(list.x + list.width - 2), windowY(list.y + (int) Math.round(fraction * (list.height - 1)))};
	}

	int[] thumbCentre() {
		VirtualRows.Thumb thumb = list.rows().thumb(list.height);
		return new int[] {windowX(list.x + list.width - 2), windowY(list.y + thumb.offset() + thumb.length() / 2)};
	}

	static int windowX(int guiX) {
		var window = Minecraft.getInstance().getWindow();
		return (int) Math.round((guiX + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth());
	}

	static int windowY(int guiY) {
		var window = Minecraft.getInstance().getWindow();
		return (int) Math.round((guiY + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight());
	}
}
