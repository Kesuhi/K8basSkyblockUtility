package com.k8bas.skyblockutility.config.migration;

import com.google.gson.JsonObject;

/**
 * Step 2 (T1.4, REQ-UPD-19): the updater only notifies now, so the 1.0.x "download automatically"
 * flag is dropped and never maps to anything. "autoUpdateCheckEnabled" keeps its name and value, so
 * an opt-out stays an opt-out. "updateChannel" is not written: absent means the default channel.
 */
final class DropAutoDownload implements MigrationStep {
	@Override
	public int number() {
		return 2;
	}

	@Override
	public String description() {
		return "the automatic-download flag is dropped; the update check only notifies";
	}

	@Override
	public void apply(JsonObject root) {
		if (root.get("general") instanceof JsonObject general) {
			general.remove("autoUpdateDownloadEnabled");
		}
	}
}
