package com.k8bas.skyblockutility.ui.notice;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Posting and drawing notices (REQ-UI-21). The position and duration come from the General settings
 * at the time of drawing and posting. Drawn over the HUD and every screen; notices are never part of
 * any screen's input, so the mouse and keyboard always reach what lies under them.
 */
public final class Notices {
	private static final NoticeQueue QUEUE = new NoticeQueue();

	private Notices() {
	}

	/** Shows a notice; safe from any thread. */
	public static void post(Notice notice) {
		QUEUE.post(notice, UiClock.MILLIS.getAsLong(), durationMs(general()));
	}

	/** The configured duration (REQ-UI-21). */
	static long durationMs(GeneralConfig config) {
		return config.noticeSeconds() * 1000L;
	}

	/** The notices on screen now, for tests. */
	public static List<NoticeQueue.Entry> visible() {
		return QUEUE.visible(UiClock.MILLIS.getAsLong());
	}

	/** Drops every notice (a test's clean start). */
	public static void clear() {
		QUEUE.clear();
	}

	/** Where each notice is drawn now, for tests (measured with the game's font, as drawn). */
	public static List<NoticeLayout.Placed> placed(int screenWidth, int screenHeight) {
		return placed(screenWidth, screenHeight, net.minecraft.client.Minecraft.getInstance().font::width);
	}

	private static List<NoticeLayout.Placed> placed(int screenWidth, int screenHeight, ToIntFunction<String> measure) {
		long now = UiClock.MILLIS.getAsLong();
		return NoticeLayout.place(QUEUE.visible(now), general().noticePosition(), screenWidth, screenHeight, now, measure);
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight) {
		List<NoticeLayout.Placed> placed = placed(screenWidth, screenHeight, font::width);
		if (placed.isEmpty()) {
			return;
		}
		Theme theme = Theme.current();
		// Newest first, so one sliding past older notices passes under them.
		for (NoticeLayout.Placed box : placed.reversed()) {
			Notice notice = box.entry().notice();
			Shapes.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), Shapes.RADIUS_CONTROL, Theme.HEADER);
			Shapes.roundedOutline(graphics, box.x(), box.y(), box.width(), box.height(), Shapes.RADIUS_CONTROL, 1, Theme.SEPARATOR);
			// The accent on the leading edge marks it as the mod's.
			graphics.fill(box.x() + 1, box.y() + 3, box.x() + 3, box.y() + box.height() - 3, theme.accent());
			int textX = box.x() + NoticeLayout.PADDING + 2;
			int textWidth = box.width() - NoticeLayout.PADDING * 2 - 2;
			int lineY = box.y() + NoticeLayout.PADDING;
			UiText.draw(graphics, font, Ellipsis.fit(notice.title(), textWidth, font::width), textX, lineY, Theme.TEXT_PRIMARY);
			for (String line : notice.body()) {
				lineY += NoticeLayout.LINE_HEIGHT;
				UiText.draw(graphics, font, Ellipsis.fit(line, textWidth, font::width), textX, lineY, Theme.TEXT_SECONDARY);
			}
		}
	}

	private static GeneralConfig general() {
		return ConfigManager.general();
	}
}
