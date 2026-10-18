package com.k8bas.skyblockutility.update;

/**
 * The player's one click that allows a download (REQ-UPD-11, D-14): *Download & install on restart* on the confirmation
 * screen, or `/ksu update install <version>`. It is honoured only while its version is the candidate on offer, so a
 * click for another version downloads nothing. The screen also passes the SHA-256 it showed: if the release's file
 * changed since, the click is refused rather than fetching bytes the player never saw.
 *
 * @param version the confirmed version as shown or typed, with or without a leading "v"
 * @param sha256  the SHA-256 the confirmation screen showed, or null for a typed command
 */
public record Consent(String version, String sha256) {
	/** A typed `/ksu update install <version>`, which names no checksum. */
	public static Consent typed(String version) {
		return new Consent(version, null);
	}
}
