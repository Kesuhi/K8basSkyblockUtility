package com.k8bas.skyblockutility.update;

/**
 * An update staged for install when the game exits (REQ-UPD-19's pending install, written by T4.3): kept in the state file
 * so the next launch can finish, re-arm or clear it (T4.3b).
 *
 * @param version       the staged version, e.g. "1.4.0"
 * @param fileName      the jar's file name
 * @param sha256        its checked SHA-256, lowercase hex
 * @param stagedAtMs    when it was staged, epoch milliseconds
 * @param fromVersion   the version that staged it, as the Loader reports it
 * @param oldJar        the jar the helper replaces
 * @param failedApplies launches in a row on which the helper had run but the old version still ran (REQ-UPD-18)
 */
public record PendingInstall(String version, String fileName, String sha256, long stagedAtMs, String fromVersion, String oldJar, int failedApplies) {
	public PendingInstall(String version, String fileName, String sha256, long stagedAtMs) {
		this(version, fileName, sha256, stagedAtMs, null, null, 0);
	}

	/** This record with {@code count} failed applies. */
	public PendingInstall withFailedApplies(int count) {
		return new PendingInstall(version, fileName, sha256, stagedAtMs, fromVersion, oldJar, count);
	}
}
