package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionStatus;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * One option's card (REQ-UI-05, REQ-UI-07): its title, at most 2 lines of description (or its status
 * when it does not work right now), and the hover look; the control on its right is a widget of its
 * own, drawn over it. A toggle card flips its switch on a click anywhere on it (REQ-UI-03). The full
 * description and the tooltip show on hover.
 */
final class OptionCard extends Widget {
	private final Option option;
	/** The switch a click anywhere on a toggle card flips; null for other cards. */
	private final ToggleSwitch toggle;
	private ConfigLayout.Lines description = new ConfigLayout.Lines(List.of(), false);

	OptionCard(Option option, ToggleSwitch toggle) {
		this.option = option;
		this.toggle = toggle;
	}

	Option option() {
		return option;
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
		ConfigLayout.Rect text = ConfigLayout.text(new ConfigLayout.Rect(x, y, width, height), option);
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
		ConfigLayout.Rect text = ConfigLayout.text(new ConfigLayout.Rect(x, y, width, height), option);
		if (text.w() <= 0) {
			return;
		}
		UiText.draw(graphics, font, Ellipsis.fit(option.text().title(), text.w(), font::width), text.x(), y + 8, faded(Theme.TEXT_PRIMARY));
		List<String> lines = description.lines();
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
