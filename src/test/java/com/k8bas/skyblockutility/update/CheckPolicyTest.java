package com.k8bas.skyblockutility.update;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.k8bas.skyblockutility.update.CheckPolicy.DAY;
import static com.k8bas.skyblockutility.update.CheckPolicy.HOUR;
import static com.k8bas.skyblockutility.update.CheckPolicy.MINUTE;
import static com.k8bas.skyblockutility.update.CheckPolicy.SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UPD-04 (automatic rows) and AC-UPD-09 (except the manual-check row) with a fake clock [A] (T1.4a). */
class CheckPolicyTest {
	private static final long T0 = 1_800_000_000_000L;

	private static GitHubReleaseSource.Result status(int status, String... headerPairs) {
		Map<String, String> headers = new java.util.HashMap<>();
		for (int i = 0; i + 1 < headerPairs.length; i += 2) {
			headers.put(headerPairs[i], headerPairs[i + 1]);
		}
		return GitHubReleaseSource.Result.failure(GitHubReleaseSource.Kind.HTTP_ERROR, status, headers, "HTTP " + status);
	}

	private static final GitHubReleaseSource.Result OK = new GitHubReleaseSource.Result(GitHubReleaseSource.Kind.OK, 200,
			List.of(new Release("v1.2.0", "u", false, false, List.of())), "\"e1\"", Map.of(), null);
	private static final GitHubReleaseSource.Result NOT_MODIFIED = new GitHubReleaseSource.Result(
			GitHubReleaseSource.Kind.NOT_MODIFIED, 304, List.of(), "\"e1\"", Map.of(), null);

	private static long waitAfter(UpdateState state, GitHubReleaseSource.Result result, long now) {
		CheckPolicy.apply(state, result, now);
		return state.nextAllowedMs - now;
	}

	@Test
	void aFreshStateIsDue() {
		assertTrue(CheckPolicy.automaticDue(new UpdateState(), T0));
	}

	@Test
	void afterA200TheNextRequestWaitsSixHours() {
		UpdateState state = new UpdateState();
		CheckPolicy.recordAutomatic(state, T0);
		CheckPolicy.apply(state, OK, T0);
		assertEquals("\"e1\"", state.etag);
		assertEquals(1, state.releases.size());
		assertFalse(CheckPolicy.automaticDue(state, T0 + HOUR));
		assertFalse(CheckPolicy.automaticDue(state, T0 + 6 * HOUR - 1));
		assertTrue(CheckPolicy.automaticDue(state, T0 + 6 * HOUR));
	}

	@Test
	void a304KeepsTheCachedListAndCountsAsSuccess() {
		UpdateState state = new UpdateState();
		CheckPolicy.apply(state, OK, T0);
		CheckPolicy.apply(state, NOT_MODIFIED, T0 + 7 * HOUR);
		assertEquals(1, state.releases.size());
		assertEquals(T0 + 7 * HOUR, state.lastSuccessMs);
	}

	@Test
	void retryAfterAndTheResetTimeAreHonoured() {
		assertEquals(120 * SECOND, waitAfter(new UpdateState(), status(403, "retry-after", "120"), T0));
		long reset = T0 / 1000 + 3600;
		assertEquals(reset * 1000 - T0, waitAfter(new UpdateState(), status(403, "x-ratelimit-remaining", "0", "x-ratelimit-reset", String.valueOf(reset)), T0));
		// A reset 3 days ahead is clamped to 24 h.
		long far = T0 / 1000 + 3 * 24 * 3600;
		assertEquals(DAY, waitAfter(new UpdateState(), status(403, "x-ratelimit-remaining", "0", "x-ratelimit-reset", String.valueOf(far)), T0));
		// Waits are at least 60 s.
		assertEquals(MINUTE, waitAfter(new UpdateState(), status(429, "retry-after", "5"), T0));
	}

	@Test
	void secondaryLimitsDoubleFromOneMinute() {
		UpdateState state = new UpdateState();
		assertEquals(MINUTE, waitAfter(state, status(403), T0));
		assertEquals(2 * MINUTE, waitAfter(state, status(403), T0));
		assertEquals(4 * MINUTE, waitAfter(state, status(429), T0));
		CheckPolicy.apply(state, OK, T0);
		assertEquals(MINUTE, waitAfter(state, status(403), T0));
	}

	@Test
	void serverErrorsDoubleUpToAnHourAndA404WaitsADay() {
		UpdateState state = new UpdateState();
		assertEquals(MINUTE, waitAfter(state, status(500), T0));
		assertEquals(2 * MINUTE, waitAfter(state, status(503), T0));
		assertEquals(4 * MINUTE, waitAfter(state, GitHubReleaseSource.Result.failure(GitHubReleaseSource.Kind.NO_RESPONSE, 0, Map.of(), "x"), T0));
		for (int i = 0; i < 10; i++) {
			CheckPolicy.apply(state, status(500), T0);
		}
		assertEquals(HOUR, state.nextAllowedMs - T0);
		assertEquals(DAY, waitAfter(new UpdateState(), status(404), T0));
		assertEquals(MINUTE, waitAfter(new UpdateState(), GitHubReleaseSource.Result.failure(GitHubReleaseSource.Kind.MALFORMED, 200, Map.of(), "x"), T0));
	}

	@Test
	void aWaitBlocksAutomaticRequests() {
		UpdateState state = new UpdateState();
		CheckPolicy.apply(state, status(403, "retry-after", "120"), T0);
		assertFalse(CheckPolicy.automaticDue(state, T0 + 119 * SECOND));
		assertTrue(CheckPolicy.automaticDue(state, T0 + 120 * SECOND));
	}

	@Test
	void aWrongClockCountsAsDue() {
		UpdateState future = new UpdateState();
		future.lastSuccessMs = T0 + 30 * DAY;
		assertTrue(CheckPolicy.automaticDue(future, T0));
		UpdateState farWait = new UpdateState();
		farWait.nextAllowedMs = T0 + 30 * DAY;
		assertTrue(CheckPolicy.automaticDue(farWait, T0));
		UpdateState futureRequests = new UpdateState();
		for (int i = 1; i <= 4; i++) {
			futureRequests.automaticRequestsMs.add(T0 + i * DAY);
		}
		assertTrue(CheckPolicy.automaticDue(futureRequests, T0));
	}

	/** 24 h of 5xx with a due check attempted every minute → at most 4 automatic requests. */
	@Test
	void aDayOfServerErrorsSendsAtMostFourRequests() {
		UpdateState state = new UpdateState();
		int requests = 0;
		for (long now = T0; now < T0 + DAY; now += MINUTE) {
			if (CheckPolicy.automaticDue(state, now)) {
				CheckPolicy.recordAutomatic(state, now);
				CheckPolicy.apply(state, status(500), now);
				requests++;
			}
		}
		assertEquals(4, requests);
	}
}
