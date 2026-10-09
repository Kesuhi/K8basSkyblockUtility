package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.RuleDatabase;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.Button;
import com.k8bas.skyblockutility.ui.widget.GameClipboard;
import com.k8bas.skyblockutility.ui.widget.Overlay;
import com.k8bas.skyblockutility.ui.widget.OverlayHost;
import com.k8bas.skyblockutility.ui.widget.TextEditModel;
import com.k8bas.skyblockutility.ui.widget.TextField;
import com.k8bas.skyblockutility.ui.widget.VirtualList;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Add from database" (REQ-UI-11): a modal with a search field, the entries in island folders on the
 * virtual list (REQ-UI-20), and an Add button per entry. An entry that already backs a rule is hidden,
 * so one just added leaves the list at once. Each add is written at once; closing never asks about
 * unsaved changes (AC-UI-10). While the list loads, or when it could not be fetched, it says so and
 * nothing can be added (EC-UI-08).
 */
public final class DatabasePickerOverlay extends Overlay {
	static final int ROW_HEIGHT = 18;
	static final int ADD_WIDTH = 38;
	private static final int MAX_WIDTH = 320;
	private static final int MAX_HEIGHT = 320;

	private final RuleDatabase database;
	private final Runnable commit;
	private final Runnable clickSound;
	private final TextField search;
	private final VirtualList list;
	private final Button done;
	private final List<Widget> widgets;
	/** Folders opened by hand, by key. */
	private final Set<String> opened = new HashSet<>();
	/** Folders closed by hand during the current search; a new query starts with all of them open. */
	private final Set<String> closedInSearch = new HashSet<>();
	private List<PickerRows.Row> rows = List.of();
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	/**
	 * @param commit writes the config after an add (a discrete commit) and refreshes the screen's search
	 */
	public DatabasePickerOverlay(RuleDatabase database, OverlayHost host, Runnable commit, Runnable clickSound) {
		this.database = database;
		this.commit = commit;
		this.clickSound = clickSound;
		this.search = TextField.of(new TextEditModel(35, GameClipboard.INSTANCE), "Search " + database.title(), text -> queryChanged());
		this.list = new VirtualList(ROW_HEIGHT, () -> rows.size(), this::drawRow, index -> { });
		list.onRowClick(this::clickRow);
		this.done = new Button("Done", Button.Style.PRIMARY, () -> host.close(this), clickSound);
		this.widgets = List.of(search, list, done);
	}

	/** The rows shown now, for tests. */
	List<PickerRows.Row> rows() {
		return rows;
	}

	TextField searchField() {
		return search;
	}

	VirtualList list() {
		return list;
	}

	Button doneButton() {
		return done;
	}

	@Override
	public boolean modal() {
		return true;
	}

	@Override
	public List<Widget> widgets() {
		return widgets;
	}

	@Override
	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= panelX && mouseY >= panelY && mouseX < panelX + panelWidth && mouseY < panelY + panelHeight;
	}

	@Override
	public void layout(int screenWidth, int screenHeight) {
		refresh();
		panelWidth = Math.max(1, Math.min(MAX_WIDTH, screenWidth - 16));
		panelHeight = Math.max(1, Math.min(MAX_HEIGHT, screenHeight - 16));
		panelX = (screenWidth - panelWidth) / 2;
		panelY = (screenHeight - panelHeight) / 2;
		search.setBounds(panelX + 8, panelY + 24, panelWidth - 16, 16);
		list.setBounds(panelX + 8, panelY + 46, panelWidth - 16, Math.max(0, panelHeight - 46 - 30));
		done.setBounds(panelX + panelWidth - 8 - 64, panelY + panelHeight - 24, 64, 16);
	}

	/** The rows from the database as it is now; also right after a click, so a second one finds them current. */
	private void refresh() {
		rows = database.state() == RuleDatabase.State.READY
				? PickerRows.rows(database.entries(), database.used(), search.model().text(), opened, closedInSearch)
				: List.of();
	}

	/** A new query shows its matches from the top, every folder open. */
	private void queryChanged() {
		closedInSearch.clear();
		refresh();
		list.rows().setScroll(0);
	}

	/** An entry's Add button, from its row's top left: x, y, width, height. Drawing and clicks both use it. */
	static int[] addButton(int rowWidth, int rowHeight) {
		return new int[] {rowWidth - ADD_WIDTH - 4, 2, ADD_WIDTH, rowHeight - 5};
	}

	private void clickRow(int index, double x, double y, int width, int height) {
		if (index >= rows.size()) {
			return;
		}
		PickerRows.Row row = rows.get(index);
		if (row.entry() == null) {
			// A folder: open or close it; during a search, only for that search.
			Set<String> toggled = search.model().text().isBlank() ? opened : closedInSearch;
			if (!toggled.remove(row.folderKey())) {
				toggled.add(row.folderKey());
			}
			refresh();
			clickSound.run();
			return;
		}
		int[] add = addButton(width, height);
		if (x >= add[0] && x < add[0] + add[2] && y >= add[1] && y < add[1] + add[3]) {
			database.add(row.entry());
			commit.run();
			refresh();
			clickSound.run();
		}
	}

	@Override
	public void drawFrame(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Shapes.roundedRect(graphics, panelX, panelY, panelWidth, panelHeight, Shapes.RADIUS_CARD, Theme.PANEL);
		Shapes.roundedOutline(graphics, panelX, panelY, panelWidth, panelHeight, Shapes.RADIUS_CARD, 1, Theme.SEPARATOR);
		UiText.draw(graphics, font, Ellipsis.fit(database.title(), panelWidth - 16, font::width), panelX + 8, panelY + 8, Theme.TEXT_PRIMARY);
		String message = message();
		if (message != null) {
			String shown = Ellipsis.fit(message, panelWidth - 24, font::width);
			UiText.centred(graphics, font, shown, panelX + panelWidth / 2, panelY + 70, Theme.TEXT_SECONDARY);
		}
	}

	/** What the list area says instead of rows: loading, unavailable, or nothing (left) to add. */
	String message() {
		return switch (database.state()) {
			case LOADING -> "Loading the database...";
			case UNAVAILABLE -> "Database unavailable: it could not be fetched";
			case READY -> !rows.isEmpty() ? null
					: database.entries().isEmpty() ? "The database is empty"
					: search.model().text().isBlank() ? "Every entry already has a rule" : "No entry matches";
		};
	}

	private void drawRow(GuiGraphicsExtractor graphics, Font font, int index, int x, int y, int width, int height, boolean hovered) {
		if (index >= rows.size()) {
			return;
		}
		PickerRows.Row row = rows.get(index);
		if (hovered) {
			graphics.fill(x, y, x + width, y + height - 1, Theme.CARD_HOVER);
		}
		int textY = y + (height - font.lineHeight) / 2 + 1;
		int indent = x + 4 + row.depth() * 12;
		if (row.entry() == null) {
			drawChevron(graphics, indent + 3, y + height / 2, row.open());
			String count = Integer.toString(row.count());
			UiText.rightAligned(graphics, font, count, x + width - 6, textY, Theme.TEXT_SECONDARY);
			UiText.draw(graphics, font, Ellipsis.fit(row.label(), x + width - 12 - font.width(count) - (indent + 12), font::width), indent + 12, textY,
					Theme.TEXT_PRIMARY);
			return;
		}
		int[] add = addButton(width, height);
		int addX = x + add[0];
		UiText.draw(graphics, font, Ellipsis.fit(row.label(), addX - 6 - (indent + 12), font::width), indent + 12, textY, Theme.TEXT_PRIMARY);
		Theme theme = Theme.current();
		Shapes.roundedRect(graphics, addX, y + add[1], add[2], add[3], Shapes.RADIUS_CONTROL, hovered ? theme.accent() : ColorMath.lerp(Theme.CARD, theme.accent(), 0.6F));
		UiText.centred(graphics, font, "Add", addX + add[2] / 2, textY, theme.textOnAccent());
	}

	private static void drawChevron(GuiGraphicsExtractor graphics, int cx, int cy, boolean open) {
		for (int i = 0; i < 4; i++) {
			if (open) {
				graphics.fill(cx - 3 + i, cy - 2 + i, cx + 4 - i, cy - 1 + i, Theme.TEXT_SECONDARY);
			} else {
				graphics.fill(cx - 2 + i, cy - 3 + i, cx - 1 + i, cy + 4 - i, Theme.TEXT_SECONDARY);
			}
		}
	}
}
