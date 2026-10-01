package com.k8bas.skyblockutility.update;

/** Which releases the updater considers (REQ-UPD-07). */
public enum UpdateChannel {
	/** Never offers or mentions pre-releases. */
	STABLE,
	/** Pre-releases and stable releases; the highest version wins. */
	BETA;

	/** The user's choice if there is one (stored), else STABLE, or BETA on a pre-release build. */
	public static UpdateChannel effective(UpdateChannel stored, SemVer running) {
		if (stored != null) {
			return stored;
		}
		return running != null && running.isPreRelease() ? BETA : STABLE;
	}

	/** GitHub's flag or a pre-release part in the version makes a release a pre-release. */
	public static boolean isPreRelease(Release release, SemVer version) {
		return release.prerelease() || version.isPreRelease();
	}
}
