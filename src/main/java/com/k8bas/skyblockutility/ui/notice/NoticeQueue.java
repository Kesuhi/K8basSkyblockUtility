package com.k8bas.skyblockutility.ui.notice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The notices on screen (REQ-UI-21): at most 4, oldest first; posting a fifth retires the oldest at
 * once. Each slides in, stays for its duration (1-15 s) and slides out, so it is gone after its
 * duration plus {@link #SLIDE_TIME_MS}. Times are the game's milliseconds. Posts may come from any
 * thread (the update check runs on its own).
 */
public final class NoticeQueue {
	public static final int MAX_VISIBLE = 4;
	/** In and out together. */
	public static final long SLIDE_TIME_MS = 400;
	public static final long MIN_DURATION_MS = 1000;
	public static final long MAX_DURATION_MS = 15_000;

	private final Deque<Entry> entries = new ArrayDeque<>();

	/** A posted notice; {@code durationMs} is the time it is fully shown. */
	public record Entry(Notice notice, long postedAt, long durationMs) {
		public long goneAt() {
			return postedAt + durationMs + SLIDE_TIME_MS;
		}

		/** How far it is in: 0 outside, 1 fully shown. */
		public float shown(long now) {
			float half = SLIDE_TIME_MS / 2F;
			float in = (now - postedAt) / half;
			float out = (goneAt() - now) / half;
			return Math.max(0F, Math.min(1F, Math.min(in, out)));
		}
	}

	public synchronized void post(Notice notice, long now, long durationMs) {
		entries.addLast(new Entry(notice, now, Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, durationMs))));
		while (entries.size() > MAX_VISIBLE) {
			entries.removeFirst();
		}
	}

	/** The notices still shown at {@code now}, oldest first; the ones gone are dropped. */
	public synchronized List<Entry> visible(long now) {
		entries.removeIf(entry -> entry.goneAt() <= now);
		return new ArrayList<>(entries);
	}

	public synchronized void clear() {
		entries.clear();
	}
}
