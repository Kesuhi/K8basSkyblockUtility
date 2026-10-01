package com.k8bas.skyblockutility.config.migration;

import com.google.gson.JsonObject;

/**
 * One numbered migration of a persisted JSON file (REQ-CFG-09): a pure transformation of the tree,
 * with no I/O and no game state, so every step can be tested against fixtures. The phase that
 * introduces a step takes the next free number.
 */
public interface MigrationStep {
	/** The version the file has after this step (1 for the first step). */
	int number();

	/** What the step changes, for the log. */
	String description();

	void apply(JsonObject root);
}
