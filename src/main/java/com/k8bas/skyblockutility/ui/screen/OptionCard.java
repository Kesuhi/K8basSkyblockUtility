package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionStatus;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.PointerShape;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * One option's card (REQ-UI-05, REQ-UI-07): its title, at most 2 lines of description (or its status
 * when it does not work right now), and the hover look; the control on its right is a widget of its
 * own, drawn over it. A toggle card flips its switch on a click anywhere on it (REQ-UI-03). The full
 * description and the tooltip show on hover. While a search runs, its matches in the title and description sit on tinted
 * boxes (REQ-UI-51).
 */
final class OptionCard extends Widget {
	private final Option option;
	/** The switch a click anywhere on a toggle card flips; null for other cards. */
	private final ToggleSwitch toggle;
	private ConfigLayout.Lines description = new ConfigLayout.Lines(List.of(), false);
	/** How far its row is indented (a rule's field), so its text ends where its control's layout starts. */
	private int indent;
	/** The query as the search sees it; its matches are marked (REQ-UI-51). */
	private final Supplier<String> query;
	/** The match boxes as drawn last, {x, y, w, h} in GUI px (for gametests). */
	private List<int[]> drawnMarks = List.of();

	OptionCard(Option option, ToggleSwitch toggle) {
		this(option, toggle, () -> "");
	}

	OptionCard(Option option, ToggleSwitch toggle, Supplier<String> query) {
		this.option = option;
		this.toggle = toggle;
		this.query = query;
	}

	/** The match boxes as drawn last, {x, y, w, h} in GUI px (for gametests). */
	List<int[]> marksForTests() {
		return drawnMarks;
	}

	/**
	 * REQ-UI-51: draws the tinted boxes behind the matches of {@code query} in {@code lines}, the drawn lines of {@code full}
	 * (the first at {@code y}, each {@code step} below the one before; the last cut with the ellipsis if {@code cut}), below the
	 * text; returns the boxes drawn.
	 */
	static List<int[]> drawMarks(GuiGraphicsExtractor graphics, Font font, String full, List<String> lines, boolean cut, String query, int x, int y,
			int step, int colour) {
		if (query.isEmpty() || lines.isEmpty()) {
			return List.of();
		}
		List<int[]> drawn = new ArrayList<>();
		List<List<int[]>> marks = SearchMarks.inLines(full, lines, query, cut);
		for (int i = 0; i < lines.size(); i++) {
			for (int[] box : SearchMarks.boxes(lines.get(i), marks.get(i), x, y + i * step, font.lineHeight, font::width)) {
				if (box[2] <= 0) {
					continue;
				}
				Shapes.roundedRect(graphics, box[0], box[1], box[2], box[3], Math.min(MARK_RADIUS, Math.min(box[2], box[3]) / 2), colour);
				drawn.add(box);
			}
		}
		return drawn;
	}

	/** A match box's corner radius in GUI px (REQ-UI-51). */
	static final int MARK_RADIUS = 2;

	/** A match box's colour: the accent at 35 % alpha (REQ-UI-51). */
	static int markColour(int accent) {
		return ColorMath.withAlpha(Math.round(0.35F * 255F), accent & 0xFFFFFF);
	}

	Option option() {
		return option;
	}

	void setIndent(int indent) {
		this.indent = indent;
	}

	/** The hand on a toggle card, where a click anywhere flips its switch; the arrow on other cards. */
	@Override
	public PointerShape cursorAt(double mouseX, double mouseY) {
		return toggle != null ? PointerShape.HAND : PointerShape.DEFAULT;
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (toggle == null || !enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		toggle.toggle();
		return true;
	}

	@Override
	public String tooltipAt(double mouseX, double mouseY) {
		String tooltip = option.text().tooltip();
		String fullDescription = option.text().description();
		if (description.cut() && !tooltip.isBlank()) {
			return fullDescription + "\n\n" + tooltip;
		}
		if (description.cut()) {
			return fullDescription;
		}
		return tooltip;
	}

	/**
	 * Fits the description to the card's text area (at most 2 lines, 1 when a status needs the second):
	 * done with the layout, so the tooltip knows before the first draw whether the text was cut.
	 */
	void fitText(ToIntFunction<String> measure) {
		ConfigLayout.Rect text = ConfigLayout.text(new ConfigLayout.Rect(x, y, width, height), option, indent);
		int maxLines = option.status().state() == OptionStatus.State.OK ? 2 : 1;
		description = ConfigLayout.fitLines(option.text().description(), text.w(), maxLines, measure);
	}

	/** The status line's text: its message, or a word for its state. */
	static String statusText(OptionStatus status) {
		if (!status.message().isBlank()) {
			return status.message();
		}
		return switch (status.state()) {
			case LOADING -> "Loading...";
			case PARTIAL -> "Partly available";
			case UNAVAILABLE -> "Unavailable";
			case OK -> "";
		};
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float hover = hoverAmount(enabled && contains(mouseX, mouseY));
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CARD, faded(ColorMath.lerp(Theme.CARD, Theme.CARD_HOVER, hover)));
		ConfigLayout.Rect text = ConfigLayout.text(new ConfigLayout.Rect(x, y, width, height), option, indent);
		if (text.w() <= 0) {
			return;
		}
		String title = Ellipsis.fit(option.text().title(), text.w(), font::width);
		List<String> lines = description.lines();
		// REQ-UI-51: the match boxes under the text.
		List<int[]> marks = new ArrayList<>(drawMarks(graphics, font, option.text().title(), List.of(title), !title.equals(option.text().title()),
				query.get(), text.x(), y + 8, 0, faded(markColour(Theme.current().accent()))));
		marks.addAll(drawMarks(graphics, font, option.text().description(), lines, description.cut(), query.get(), text.x(), y + 20, 10,
				faded(markColour(Theme.current().accent()))));
		drawnMarks = marks;
		UiText.draw(graphics, font, title, text.x(), y + 8, faded(Theme.TEXT_PRIMARY));
		for (int i = 0; i < lines.size(); i++) {
			UiText.draw(graphics, font, lines.get(i), text.x(), y + 20 + i * 10, faded(Theme.TEXT_SECONDARY));
		}
		OptionStatus status = option.status();
		if (status.state() != OptionStatus.State.OK) {
			int colour = switch (status.state()) {
				case UNAVAILABLE -> Theme.ERROR;
				case PARTIAL -> Theme.CONFLICT_TEXT;
				default -> Theme.TEXT_SECONDARY;
			};
			UiText.draw(graphics, font, Ellipsis.fit(statusText(status), text.w(), font::width), text.x(), y + 30, faded(colour));
		}
	}
}
