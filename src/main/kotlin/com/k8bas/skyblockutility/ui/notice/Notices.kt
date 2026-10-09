package com.k8bas.skyblockutility.ui.notice

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.config.GeneralConfig
import com.k8bas.skyblockutility.ui.render.Ellipsis
import com.k8bas.skyblockutility.ui.render.Shapes
import com.k8bas.skyblockutility.ui.render.Theme
import com.k8bas.skyblockutility.ui.render.UiClock
import com.k8bas.skyblockutility.ui.render.UiText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.ToIntFunction

/**
 * Posting and drawing notices (REQ-UI-21). The position and duration come from the General settings
 * at the time of drawing and posting. Drawn over the HUD and every screen; notices are never part of
 * any screen's input, so the mouse and keyboard always reach what lies under them.
 */
object Notices {
	private val QUEUE = NoticeQueue()

	/** Shows a notice; safe from any thread. */
	@JvmStatic
	fun post(notice: Notice) {
		QUEUE.post(notice, UiClock.MILLIS.asLong, durationMs(general()))
	}

	/** The configured duration (REQ-UI-21). */
	@JvmStatic
	fun durationMs(config: GeneralConfig): Long = config.noticeSeconds() * 1000L

	/** The notices on screen now, for tests. */
	@JvmStatic
	fun visible(): List<NoticeQueue.Entry> = QUEUE.visible(UiClock.MILLIS.asLong)

	/** Drops every notice (a test's clean start). */
	@JvmStatic
	fun clear() {
		QUEUE.clear()
	}

	/** Where each notice is drawn now, for tests (measured with the game's font, as drawn). */
	@JvmStatic
	fun placed(screenWidth: Int, screenHeight: Int): List<NoticeLayout.Placed> {
		val font = Minecraft.getInstance().font
		return placed(screenWidth, screenHeight, font::width)
	}

	private fun placed(screenWidth: Int, screenHeight: Int, measure: ToIntFunction<String>): List<NoticeLayout.Placed> {
		val now = UiClock.MILLIS.asLong
		return NoticeLayout.place(QUEUE.visible(now), general().noticePosition(), screenWidth, screenHeight, now, measure)
	}

	@JvmStatic
	fun draw(graphics: GuiGraphicsExtractor, font: Font, screenWidth: Int, screenHeight: Int) {
		val placed = placed(screenWidth, screenHeight, font::width)
		if (placed.isEmpty()) {
			return
		}
		val theme = Theme.current()
		// Newest first, so one sliding past older notices passes under them; a view, so nothing is copied per frame.
		for (box in placed.asReversed()) {
			val notice = box.entry().notice()
			Shapes.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), Shapes.RADIUS_CONTROL, Theme.HEADER)
			Shapes.roundedOutline(graphics, box.x(), box.y(), box.width(), box.height(), Shapes.RADIUS_CONTROL, 1, Theme.SEPARATOR)
			// The accent on the leading edge marks it as the mod's.
			graphics.fill(box.x() + 1, box.y() + 3, box.x() + 3, box.y() + box.height() - 3, theme.accent())
			val textX = box.x() + NoticeLayout.PADDING + 2
			val textWidth = box.width() - NoticeLayout.PADDING * 2 - 2
			var lineY = box.y() + NoticeLayout.PADDING
			UiText.draw(graphics, font, Ellipsis.fit(notice.title, textWidth, font::width), textX, lineY, Theme.TEXT_PRIMARY)
			for (line in notice.body) {
				lineY += NoticeLayout.LINE_HEIGHT
				UiText.draw(graphics, font, Ellipsis.fit(line, textWidth, font::width), textX, lineY, Theme.TEXT_SECONDARY)
			}
		}
	}

	private fun general(): GeneralConfig = ConfigManager.general()
}
