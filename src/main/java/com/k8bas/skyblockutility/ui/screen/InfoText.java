package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/** A value shown, not edited (a fixed NPC's coordinates), right-aligned in the control's place. */
final class InfoText extends Widget {
	private final Supplier<String> value;

	InfoText(Supplier<String> value) {
		this.value = value;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		String shown = Ellipsis.fit(value.get(), width, font::width);
		UiText.rightAligned(graphics, font, shown, x + width, y + (height - font.lineHeight) / 2 + 1, faded(Theme.TEXT_SECONDARY));
	}
}
