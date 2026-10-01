package com.k8bas.skyblockutility.update;

import java.util.List;

/** The fields of a GitHub release the updater uses; also the cached copy in the state file. */
public record Release(String tag, String htmlUrl, boolean draft, boolean prerelease, List<Asset> assets) {
	public Release {
		assets = assets == null ? List.of() : List.copyOf(assets);
	}

	/** A release file. {@code digest} is GitHub's "sha256:…" value, or null. */
	public record Asset(String name, String state, String downloadUrl, String digest, long size) {
	}
}
