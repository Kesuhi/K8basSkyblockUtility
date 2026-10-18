package com.k8bas.skyblockutility.update;

import java.util.List;

/**
 * The fields of a GitHub release the updater uses; also the cached copy in the state file.
 *
 * @param body the release notes as GitHub holds them (Markdown), at most {@link #MAX_BODY_CHARS} characters, or null
 */
public record Release(String tag, String htmlUrl, boolean draft, boolean prerelease, List<Asset> assets, String body) {
	/** More than the confirmation screen shows (REQ-UPD-11: 4,000 plain characters), so Markdown syntax has room. */
	public static final int MAX_BODY_CHARS = 8_000;

	public Release {
		assets = assets == null ? List.of() : List.copyOf(assets);
		body = body == null || body.length() <= MAX_BODY_CHARS ? body : body.substring(0, MAX_BODY_CHARS);
	}

	/** A release without notes. */
	public Release(String tag, String htmlUrl, boolean draft, boolean prerelease, List<Asset> assets) {
		this(tag, htmlUrl, draft, prerelease, assets, null);
	}

	/** A release file. {@code digest} is GitHub's "sha256:…" value, or null. */
	public record Asset(String name, String state, String downloadUrl, String digest, long size) {
	}
}
