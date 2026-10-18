package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Supplier;

/**
 * A value shown, not edited (a fixed NPC's coordinates), right-aligned in the control's place; a wide one (an update
 * check's answer, T4.4b) left-aligned under the title, in at most two lines like a description, the whole text in its
 * tooltip when that cuts it.
 */
final class InfoText extends Widget {
	private final Supplier<String> value;
	private final boolean wide;
	private String fittedFor;
	private int fittedWidth = -1;
	private List<String> lines = List.of();

	InfoText(Supplier<String> value) {
		this(value, false);
	}

	InfoText(Supplier<String> value, boolean wide) {
		this.value = value;
		this.wide = wide;
	}

	/** The lines drawn last (for gametests). */
	List<String> linesForTests() {
		return lines;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		String text = value.get();
		if (wide) {
			// Fitted again only when the text or the width changed, not every frame.
			if (!text.equals(fittedFor) || width != fittedWidth) {
				fittedFor = text;
				fittedWidth = width;
				ConfigLayout.Lines fitted = ConfigLayout.fitLines(text, width, ConfigLayout.DESCRIPTION_LINES, font::width);
				lines = fitted.lines();
				setTooltip(fitted.cut() ? text : "");
			}
			for (int i = 0; i < lines.size(); i++) {
				UiText.draw(graphics, font, lines.get(i), x, y + i * 10, faded(Theme.TEXT_PRIMARY));
			}
			return;
		}
		String shown = Ellipsis.fit(text, width, font::width);
		lines = List.of(shown);
		UiText.rightAligned(graphics, font, shown, x + width, y + (height - font.lineHeight) / 2 + 1, faded(Theme.TEXT_SECONDARY));
	}
}
