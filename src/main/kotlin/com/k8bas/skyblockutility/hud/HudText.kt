package com.k8bas.skyblockutility.hud

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.IntPredicate

/**
 * Multi-line text content (REQ-HUD-12): the feature supplies its lines and its options say which show.
 * Its bounds are the drawn text, shadow included, plus [MARGIN] on every side; a feature whose lines change makes a
 * new one, so the bounds follow the content. Lines may carry § formatting.
 *
 * A class rather than a data class: the lines are copied on construction. Java sees `lines()`.
 */
class HudText(lines: List<String>) : HudContent {
	@get:JvmName("lines")
	val lines: List<String> = java.util.List.copyOf(lines)

	override fun width(text: TextMeasure): Int {
		var widest = 0
		for (line in lines) {
			widest = maxOf(widest, text.width(line))
		}
		return widest + 2 * MARGIN
	}

	/** Each line's glyphs and their shadow fill its line height: a descender's shadow reaches the last row. */
	override fun height(text: TextMeasure): Int = lines.size * text.lineHeight() + 2 * MARGIN

	override fun draw(graphics: GuiGraphicsExtractor, font: Font) {
		for (i in lines.indices) {
			graphics.text(font, lines[i], MARGIN, MARGIN + i * font.lineHeight, TEXT, true)
		}
	}

	override fun equals(other: Any?): Boolean = other is HudText && lines == other.lines

	override fun hashCode(): Int = lines.hashCode()

	override fun toString(): String = "HudText[lines=$lines]"

	companion object {
		const val MARGIN: Int = 2
		private const val TEXT: Int = -0x1 // 0xFFFFFFFF, opaque white

		/** The lines whose index [shown] accepts, or null (nothing to draw) when none is. */
		@JvmStatic
		fun of(lines: List<String>, shown: IntPredicate): HudText? =
			lines.filterIndexed { index, _ -> shown.test(index) }.takeIf { it.isNotEmpty() }?.let(::HudText)
	}
}
