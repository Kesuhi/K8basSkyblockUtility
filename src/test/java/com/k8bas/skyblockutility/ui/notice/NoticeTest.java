package com.k8bas.skyblockutility.ui.notice;

import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Toggle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-21, AC-UI-19 [A] (T2.3d): notices, their queue and their placement. */
class NoticeTest {
	private static final long SECOND = 1000;

	private static Notice notice(String title) {
		return new Notice(title, List.of("body"));
	}

	/** AC-UI-19: 5 notices within one second; at most 4 show, the oldest is retired first. */
	@Test
	void fiveNoticesInASecondShowFourAndRetireTheOldest() {
		NoticeQueue queue = new NoticeQueue();
		for (int i = 1; i <= 5; i++) {
			queue.post(notice("N" + i), 1000 + i * 150L, 5 * SECOND);
		}
		List<NoticeQueue.Entry> shown = queue.visible(1800);
		assertEquals(List.of("N2", "N3", "N4", "N5"), shown.stream().map(entry -> entry.notice().title()).toList());
		assertTrue(shown.size() <= NoticeQueue.MAX_VISIBLE);
	}

	/** AC-UI-19: each one goes after its duration plus the slide time. */
	@Test
	void eachGoesAfterItsDurationPlusTheSlide() {
		NoticeQueue queue = new NoticeQueue();
		queue.post(notice("short"), 0, 2 * SECOND);
		queue.post(notice("long"), 0, 15 * SECOND);
		long shortGone = 2 * SECOND + NoticeQueue.SLIDE_TIME_MS;
		assertEquals(2, queue.visible(shortGone - 1).size());
		assertEquals(List.of("long"), queue.visible(shortGone).stream().map(entry -> entry.notice().title()).toList());
		assertTrue(queue.visible(15 * SECOND + NoticeQueue.SLIDE_TIME_MS).isEmpty());
	}

	@Test
	void theDurationIsHeldToOneToFifteenSeconds() {
		NoticeQueue queue = new NoticeQueue();
		queue.post(notice("a"), 0, 10);
		queue.post(notice("b"), 0, 60 * SECOND);
		List<NoticeQueue.Entry> entries = queue.visible(0);
		assertEquals(SECOND, entries.get(0).durationMs());
		assertEquals(15 * SECOND, entries.get(1).durationMs());
	}

	@Test
	void slidesInAndOut() {
		NoticeQueue queue = new NoticeQueue();
		queue.post(notice("a"), 0, 3 * SECOND);
		NoticeQueue.Entry entry = queue.visible(0).get(0);
		assertEquals(0F, entry.shown(0), 1e-6, "starts outside");
		assertEquals(1F, entry.shown(NoticeQueue.SLIDE_TIME_MS / 2), 1e-6, "fully in after the slide in");
		assertEquals(1F, entry.shown(3 * SECOND + NoticeQueue.SLIDE_TIME_MS / 2), 1e-6, "fully in for its whole duration");
		float out = entry.shown(3 * SECOND + NoticeQueue.SLIDE_TIME_MS * 3 / 4);
		assertTrue(out > 0F && out < 1F, "sliding out: " + out);
	}

	@Test
	void aNoticeHasATitleAndAtMostTwoBodyLines() {
		new Notice("Update available", List.of("Version 2.1.0 is out.", "Open the settings to update."));
		new Notice("Title only", List.of());
		assertThrows(IllegalArgumentException.class, () -> new Notice("x", List.of("1", "2", "3")));
		assertThrows(IllegalArgumentException.class, () -> new Notice(" ", List.of()));
	}

	@Test
	void everyPositionKeepsTheStackInsideWithoutOverlap() {
		for (NoticePosition position : NoticePosition.values()) {
			for (int[] screen : new int[][] {{1280, 960}, {427, 240}, {320, 240}}) {
				NoticeQueue queue = new NoticeQueue();
				for (int i = 0; i < 4; i++) {
					queue.post(new Notice("Notice " + i, List.of("one", "two")), 0, 5 * SECOND);
				}
				List<NoticeLayout.Placed> placed = NoticeLayout.place(queue.visible(SECOND), position, screen[0], screen[1], SECOND);
				String where = position + " " + screen[0] + "x" + screen[1];
				assertEquals(4, placed.size(), where);
				for (int i = 0; i < placed.size(); i++) {
					NoticeLayout.Placed box = placed.get(i);
					assertTrue(box.x() >= 0 && box.y() >= 0 && box.x() + box.width() <= screen[0] && box.y() + box.height() <= screen[1],
							where + ": " + box);
					for (int j = 0; j < i; j++) {
						NoticeLayout.Placed other = placed.get(j);
						assertFalse(box.y() < other.y() + other.height() && other.y() < box.y() + box.height(), where + ": overlap " + other + " " + box);
					}
				}
			}
		}
	}

	@Test
	void theOldestSitsAtTheEdgeAndTopCentreIsCentred() {
		NoticeQueue queue = new NoticeQueue();
		queue.post(notice("old"), 0, 5 * SECOND);
		queue.post(notice("new"), 0, 5 * SECOND);
		List<NoticeLayout.Placed> top = NoticeLayout.place(queue.visible(SECOND), NoticePosition.TOP_RIGHT, 640, 480, SECOND);
		assertTrue(top.get(0).y() < top.get(1).y(), "top: the oldest highest");
		assertEquals(640 - NoticeLayout.MARGIN, top.get(0).x() + top.get(0).width(), "against the right edge");
		List<NoticeLayout.Placed> bottom = NoticeLayout.place(queue.visible(SECOND), NoticePosition.BOTTOM_LEFT, 640, 480, SECOND);
		assertTrue(bottom.get(0).y() > bottom.get(1).y(), "bottom: the oldest lowest");
		assertEquals(NoticeLayout.MARGIN, bottom.get(0).x());
		NoticeLayout.Placed centre = NoticeLayout.place(queue.visible(SECOND), NoticePosition.TOP_CENTRE, 640, 480, SECOND).get(0);
		assertEquals(640 / 2, centre.x() + centre.width() / 2, 1);
	}

	@Test
	void aNoticeSlidingInStartsOutsideTheWindow() {
		NoticeQueue queue = new NoticeQueue();
		queue.post(notice("a"), 0, 5 * SECOND);
		NoticeLayout.Placed right = NoticeLayout.place(queue.visible(0), NoticePosition.TOP_RIGHT, 640, 480, 0).get(0);
		assertTrue(right.x() >= 640, "right: comes in from the right " + right);
		NoticeLayout.Placed left = NoticeLayout.place(queue.visible(0), NoticePosition.BOTTOM_LEFT, 640, 480, 0).get(0);
		assertTrue(left.x() + left.width() <= 0, "left: from the left " + left);
		NoticeLayout.Placed centre = NoticeLayout.place(queue.visible(0), NoticePosition.TOP_CENTRE, 640, 480, 0).get(0);
		assertTrue(centre.y() + centre.height() <= 0, "top-centre: from above " + centre);
	}

	/** While the oldest slides out, the corner stack closes up only once it is out of the way: no two boxes ever cross. */
	@Test
	void cornerBoxesNeverCrossWhileOneSlidesOut() {
		for (NoticePosition position : List.of(NoticePosition.TOP_LEFT, NoticePosition.TOP_RIGHT, NoticePosition.BOTTOM_LEFT,
				NoticePosition.BOTTOM_RIGHT)) {
			NoticeQueue queue = new NoticeQueue();
			queue.post(new Notice("leaves first", List.of()), 0, 2 * SECOND);
			queue.post(new Notice("stays", List.of("one", "two")), 0, 10 * SECOND);
			queue.post(new Notice("stays too", List.of()), 0, 10 * SECOND);
			long previousY = Long.MIN_VALUE;
			for (long now = 2 * SECOND; now <= 2 * SECOND + NoticeQueue.SLIDE_TIME_MS; now += 5) {
				List<NoticeLayout.Placed> placed = NoticeLayout.place(queue.visible(now), position, 640, 480, now);
				for (int i = 0; i < placed.size(); i++) {
					for (int j = 0; j < i; j++) {
						NoticeLayout.Placed a = placed.get(i);
						NoticeLayout.Placed b = placed.get(j);
						boolean crossX = a.x() < b.x() + b.width() && b.x() < a.x() + a.width();
						boolean crossY = a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
						assertFalse(crossX && crossY, position + " at " + now + ": " + a + " crosses " + b);
					}
				}
				// The second notice only ever moves toward the edge.
				NoticeLayout.Placed stays = placed.stream().filter(box -> box.entry().notice().title().equals("stays")).findFirst().orElseThrow();
				long distance = position.top() ? -stays.y() : stays.y();
				assertTrue(distance >= previousY, position + ": closes up steadily");
				previousY = distance;
			}
		}
	}

	@Test
	void theConfiguredDurationIsUsed() {
		GeneralConfig config = new GeneralConfig();
		assertEquals(5 * SECOND, Notices.durationMs(config), "the default");
		config.noticeSeconds = 3;
		assertEquals(3 * SECOND, Notices.durationMs(config));
	}

	@Test
	void aWindowTooShortForTheStackDropsTheNewest() {
		NoticeQueue queue = new NoticeQueue();
		for (int i = 0; i < 4; i++) {
			queue.post(new Notice("N" + i, List.of("one", "two")), 0, 5 * SECOND);
		}
		List<NoticeLayout.Placed> placed = NoticeLayout.place(queue.visible(SECOND), NoticePosition.TOP_LEFT, 320, 100, SECOND);
		assertTrue(placed.size() < 4 && !placed.isEmpty());
		assertEquals("N0", placed.get(0).entry().notice().title());
		for (NoticeLayout.Placed box : placed) {
			assertTrue(box.y() + box.height() <= 100, "inside: " + box);
		}
	}

	/** A long line (a backup file's name) widens its notice, up to a limit and never past the window. */
	@Test
	void aLongLineWidensItsNotice() {
		NoticeQueue queue = new NoticeQueue();
		queue.post(new Notice("Short", List.of("short")), 0, 5 * SECOND);
		queue.post(new Notice("Settings file backed up", List.of("A copy is in the config folder:",
				"k8bas_skyblock_utility.json.broken-20261004-120000.bak")), 0, 5 * SECOND);
		java.util.function.ToIntFunction<String> sixPixels = text -> text.length() * 6;
		List<NoticeLayout.Placed> placed = NoticeLayout.place(queue.visible(SECOND), NoticePosition.TOP_RIGHT, 640, 480, SECOND, sixPixels);
		assertEquals(NoticeLayout.WIDTH, placed.get(0).width(), "short text keeps the usual width");
		int nameWidth = "k8bas_skyblock_utility.json.broken-20261004-120000.bak".length() * 6;
		assertEquals(nameWidth + 2 * NoticeLayout.PADDING + 2, placed.get(1).width(), "it grows to fit the long name");
		queue.post(new Notice("x".repeat(100), List.of()), 0, 5 * SECOND);
		assertEquals(NoticeLayout.MAX_WIDTH, NoticeLayout.place(queue.visible(SECOND), NoticePosition.TOP_RIGHT, 640, 480, SECOND, sixPixels).get(2).width(),
				"up to the cap");
		assertEquals(640 - NoticeLayout.MARGIN, placed.get(1).x() + placed.get(1).width(), "still against the edge");
		NoticeLayout.Placed narrow = NoticeLayout.place(queue.visible(SECOND), NoticePosition.TOP_RIGHT, 200, 480, SECOND, sixPixels).get(1);
		assertTrue(narrow.width() <= 200 - 2 * NoticeLayout.MARGIN, "never wider than the window: " + narrow);
	}

	/** AC-UI-19 [A]: a warning feature's notice channel is OFF on a fresh config. */
	@Test
	void theNoticeChannelHelperDefaultsToOff() {
		boolean[] stored = {false};
		Toggle channel = NoticeChannel.option("hotspot.gone_notice", "hotspot.goneNotice", "the hotspot is gone",
				Binding.of(() -> stored[0], value -> stored[0] = value));
		assertFalse(channel.defaultValue());
		assertFalse(channel.text().title().isBlank());
		assertTrue(channel.text().description().contains("the hotspot is gone"));
	}

	@Test
	void aNoticeDurationOutsideTheRangeIsKeptAndHeldToItWhenUsed() {
		GeneralConfig config = new GeneralConfig();
		assertEquals(NoticePosition.TOP_RIGHT, config.noticePosition());
		assertEquals(5, config.noticeSeconds());
		assertFalse(config.normalize(), "absent settings are not written");
		// REQ-UI-16: a hand-edited value outside the slider's range stays in the file until the slider changes it.
		config.noticeSeconds = 99;
		assertFalse(config.normalize());
		assertEquals(99, config.noticeSeconds);
		NoticeQueue queue = new NoticeQueue();
		queue.post(notice("a"), 0, Notices.durationMs(config));
		assertEquals(NoticeQueue.MAX_DURATION_MS, queue.visible(0).get(0).durationMs(), "used as 15 s");
	}

	/** EC-UI-16: the backup notice shows the whole file name, also with a collision suffix, at a common GUI size. */
	@Test
	void theBackupFileNameFitsItsNotice() {
		java.util.function.ToIntFunction<String> sixPixels = text -> text.length() * 6;
		for (String name : List.of("k8bas_skyblock_utility.json.broken-20261004-120000.bak", "k8bas_skyblock_utility.json.broken-20261004-120000-1.bak")) {
			NoticeQueue queue = new NoticeQueue();
			queue.post(new Notice("Settings file backed up", List.of("A copy is in the config folder:", name)), 0, 5 * SECOND);
			NoticeLayout.Placed box = NoticeLayout.place(queue.visible(SECOND), NoticePosition.TOP_RIGHT, 640, 360, SECOND, sixPixels).get(0);
			int textWidth = box.width() - 2 * NoticeLayout.PADDING - 2;
			assertEquals(name, com.k8bas.skyblockutility.ui.render.Ellipsis.fit(name, textWidth, sixPixels), "not cut: " + box);
		}
	}
}
