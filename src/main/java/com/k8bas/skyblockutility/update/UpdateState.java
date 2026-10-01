package com.k8bas.skyblockutility.update;

import java.util.ArrayList;
import java.util.List;

/**
 * The updater's runtime state, kept in its own file so saving the settings never touches it
 * (REQ-UPD-19, REQ-CFG-12). Times are epoch milliseconds.
 */
public final class UpdateState {
	public int stateVersion = UpdateStateStore.MIGRATOR.currentVersion();
	/** From the last 200; sent as If-None-Match. */
	public String etag;
	/** The release list of the last 200, reused after a 304. */
	public List<Release> releases = new ArrayList<>();
	/** The last 200 or 304. */
	public long lastSuccessMs;
	/** No request before this (back-off). */
	public long nextAllowedMs;
	/** Consecutive secondary rate limits (403/429 without wait headers). */
	public int secondaryStreak;
	/** Consecutive failures without a usable answer (5xx, no response, malformed body). */
	public int errorStreak;
	/** When automatic requests were sent, for the rolling 24 h cap. */
	public List<Long> automaticRequestsMs = new ArrayList<>();

	/** Repairs what Gson leaves null in a hand-edited or partial file. */
	void normalize() {
		if (releases == null) {
			releases = new ArrayList<>();
		}
		releases.removeIf(release -> release == null || release.tag() == null);
		if (automaticRequestsMs == null) {
			automaticRequestsMs = new ArrayList<>();
		}
		automaticRequestsMs.removeIf(time -> time == null);
		secondaryStreak = Math.max(0, secondaryStreak);
		errorStreak = Math.max(0, errorStreak);
	}
}
