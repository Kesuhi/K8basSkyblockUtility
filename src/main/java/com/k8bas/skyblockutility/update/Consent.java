package com.k8bas.skyblockutility.update;

/**
 * The player's one click that allows a download (REQ-UPD-11, D-14, R54): *Download & install on restart* on the
 * confirmation screen; `/ksu update install <version>` only opens that screen. It is honoured only while its version is
 * the candidate on offer, so a click for another version downloads nothing. The screen also passes the SHA-256 it showed:
 * if the release's file changed since, the click is refused rather than fetching bytes the player never saw.
 *
 * @param version the confirmed version as shown, with or without a leading "v"
 * @param sha256  the SHA-256 the confirmation screen showed; null only in tests ({@link #typed})
 */
public record Consent(String version, String sha256) {
	/** A consent that names no checksum, for tests: the game makes consents only on the confirmation screen (R54). */
	public static Consent typed(String version) {
		return new Consent(version, null);
	}
}
