package com.k8bas.skyblockutility.update;

import java.util.ArrayList;
import java.util.Map;

/**
 * When the updater may send a request, and how a response changes that (REQ-UPD-04, REQ-UPD-09).
 * Pure functions of the state and a clock reading, so every rule is testable with a fake clock.
 */
final class CheckPolicy {
	static final long SECOND = 1000;
	static final long MINUTE = 60 * SECOND;
	static final long HOUR = 60 * MINUTE;
	static final long DAY = 24 * HOUR;
	/** After a 200 or 304, the next automatic request waits at least this long. */
	static final long INTERVAL = 6 * HOUR;
	static final int MAX_AUTOMATIC_PER_DAY = 4;
	static final long MIN_WAIT = MINUTE;
	static final long MAX_WAIT = DAY;
	static final long MAX_DOUBLING_WAIT = HOUR;

	private CheckPolicy() {
	}

	/** Whether an automatic check may send a request now. A last success in the future (a wrong
	 *  clock) counts as due, so the clock can neither disable checks nor flood them. */
	static boolean automaticDue(UpdateState state, long now) {
		if (waiting(state, now) || automaticInLastDay(state, now) >= MAX_AUTOMATIC_PER_DAY) {
			return false;
		}
		return state.lastSuccessMs > now || now - state.lastSuccessMs >= INTERVAL;
	}

	/** Inside a back-off wait. A wait ending more than 24 h from now cannot be one the policy set
	 *  (the clock moved back), so it does not count. */
	static boolean waiting(UpdateState state, long now) {
		return state.nextAllowedMs > now && state.nextAllowedMs - now <= MAX_WAIT;
	}

	static int automaticInLastDay(UpdateState state, long now) {
		return (int) state.automaticRequestsMs.stream().filter(time -> time <= now && now - time < DAY).count();
	}

	/** Notes an automatic request sent now; entries outside the window are dropped. */
	static void recordAutomatic(UpdateState state, long now) {
		state.automaticRequestsMs.removeIf(time -> time > now || now - time >= DAY);
		state.automaticRequestsMs.add(now);
	}

	/** Applies a response: caches the list after a 200, sets the next allowed time after an error. */
	static void apply(UpdateState state, GitHubReleaseSource.Result result, long now) {
		switch (result.kind()) {
			case OK -> {
				state.releases = new ArrayList<>(result.releases());
				state.etag = result.etag();
				succeeded(state, now);
			}
			case NOT_MODIFIED -> succeeded(state, now);
			case HTTP_ERROR -> {
				int status = result.status();
				if (status == 403 || status == 429) {
					waitFor(state, now, rateLimitWait(state, result.headers(), now));
				} else if (status == 404) {
					waitFor(state, now, DAY);
				} else {
					waitFor(state, now, doubling(state.errorStreak++));
				}
			}
			case NO_RESPONSE, MALFORMED, BAD_REDIRECT -> waitFor(state, now, doubling(state.errorStreak++));
		}
	}

	private static void succeeded(UpdateState state, long now) {
		state.lastSuccessMs = now;
		state.nextAllowedMs = 0;
		state.secondaryStreak = 0;
		state.errorStreak = 0;
	}

	/** retry-after, else the reset time when no requests remain, else the secondary-limit doubling. */
	private static long rateLimitWait(UpdateState state, Map<String, String> headers, long now) {
		Long retryAfter = number(headers.get("retry-after"));
		if (retryAfter != null) {
			return retryAfter * SECOND;
		}
		Long reset = number(headers.get("x-ratelimit-reset"));
		if ("0".equals(headers.get("x-ratelimit-remaining")) && reset != null) {
			return reset * SECOND - now;
		}
		return doubling(state.secondaryStreak++);
	}

	/** 1 min, doubling with each consecutive occurrence, at most 1 h. */
	static long doubling(int previous) {
		return Math.min(MAX_DOUBLING_WAIT, MINUTE << Math.min(previous, 10));
	}

	private static void waitFor(UpdateState state, long now, long wait) {
		state.nextAllowedMs = now + Math.clamp(wait, MIN_WAIT, MAX_WAIT);
	}

	private static Long number(String value) {
		if (value == null) {
			return null;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
