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
	/** When the last manual request was sent, for the 60 s cooldown (REQ-UPD-04). */
	public long lastManualMs;
	/** Why {@link #nextAllowedMs} holds requests back: {@link #WAIT_RATE_LIMIT}, {@link #WAIT_ERROR}, {@link #WAIT_NOT_FOUND} or null. */
	public String waitKind;
	/** The version the player chose to skip: no notice for it, marked "skipped" in a manual check (REQ-UPD-08). */
	public String skippedVersion;
	/** An update staged for install, or null (T4.3). */
	public PendingInstall pendingInstall;
	/** The jar an applied update replaced that could not be deleted yet; tried again at exit and at the next launch (T4.3b). */
	public String leftoverJar;
	/** Files the player was already warned about (T4.3b), so each is named once. */
	public List<String> warnedFiles = new ArrayList<>();

	/** A 403 or 429: manual checks wait too (REQ-UPD-09). */
	public static final String WAIT_RATE_LIMIT = "rate-limit";
	/** A 5xx, no response, a malformed body or a bad redirect. */
	public static final String WAIT_ERROR = "error";
	/** A 404. */
	public static final String WAIT_NOT_FOUND = "not-found";

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
		if (warnedFiles == null) {
			warnedFiles = new ArrayList<>();
		}
		warnedFiles.removeIf(name -> name == null);
		secondaryStreak = Math.max(0, secondaryStreak);
		errorStreak = Math.max(0, errorStreak);
	}
}
