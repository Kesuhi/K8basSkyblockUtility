package com.k8bas.skyblockutility.ui.notice

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.config.GeneralConfig
import com.k8bas.skyblockutility.ui.render.Ellipsis
import com.k8bas.skyblockutility.ui.render.Shapes
import com.k8bas.skyblockutility.ui.render.Theme
import com.k8bas.skyblockutility.ui.render.UiClock
import com.k8bas.skyblockutility.ui.render.UiText
import com.k8bas.skyblockutility.ui.render.UiTime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.function.ToIntFunction

/**
 * Posting and drawing notices (REQ-UI-21). The position and duration come from the General settings
 * at the time of drawing and posting. Drawn over the HUD and every screen; notices are never part of
 * any screen's input, so the mouse and keyboard always reach what lies under them. They belong to no screen, so they
 * run on the game's UI clock source (REQ-UI-39): its time, and with Animations OFF no slide.
 */
object Notices {
	private val QUEUE = NoticeQueue()

	/** The UI clock source notices run on: the game's; a gametest sets a test clock with motion on to step a slide. */
	@JvmStatic
	var time: UiTime = UiClock.GAME

	/** Shows a notice; safe from any thread. */
	@JvmStatic
	fun post(notice: Notice) {
		QUEUE.post(notice, time.millis(), durationMs(general()))
	}

	/** The configured duration (REQ-UI-21). */
	@JvmStatic
	fun durationMs(config: GeneralConfig): Long = config.noticeSeconds() * 1000L

	/** The notices on screen now, for tests. */
	@JvmStatic
	fun visible(): List<NoticeQueue.Entry> = QUEUE.visible(time.millis())

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
		val now = time.millis()
		return NoticeLayout.place(QUEUE.visible(now), general().noticePosition(), screenWidth, screenHeight, now, measure, time.motion(),
				Shapes.guiScale())
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
			// REQ-UI-34: drawn where the slide is on the physical-pixel grid, at most half a GUI px from its whole-px box.
			graphics.pose().pushMatrix()
			try {
				graphics.pose().translate(box.drawnX() - box.x(), box.drawnY() - box.y())
				drawBox(graphics, font, box, theme)
			} finally {
				graphics.pose().popMatrix()
			}
		}
	}

	private fun drawBox(graphics: GuiGraphicsExtractor, font: Font, box: NoticeLayout.Placed, theme: Theme) {
		val notice = box.entry().notice()
		Shapes.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), Shapes.RADIUS_CONTROL, Theme.HEADER)
		Shapes.roundedHairline(graphics, box.x(), box.y(), box.width(), box.height(), Shapes.RADIUS_CONTROL, Theme.SEPARATOR)
		// The accent on the leading edge marks it as the mod's, flush with the hairline outline.
		val line = Shapes.hairlineGui(Shapes.guiScale())
		Shapes.fill(graphics, box.x() + line, box.y() + 3f, box.x() + 3f, (box.y() + box.height() - 3).toFloat(), theme.accent())
		val textX = box.x() + NoticeLayout.PADDING + 2
		val textWidth = box.width() - NoticeLayout.PADDING * 2 - 2
		var lineY = box.y() + NoticeLayout.PADDING
		UiText.draw(graphics, font, Ellipsis.fit(notice.title, textWidth, font::width), textX, lineY, Theme.TEXT_PRIMARY)
		for (line in notice.body) {
			lineY += NoticeLayout.LINE_HEIGHT
			UiText.draw(graphics, font, Ellipsis.fit(line, textWidth, font::width), textX, lineY, Theme.TEXT_SECONDARY)
		}
	}

	private fun general(): GeneralConfig = ConfigManager.general()
}
