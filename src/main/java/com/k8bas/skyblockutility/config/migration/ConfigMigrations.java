package com.k8bas.skyblockutility.config.migration;

import java.util.List;

/**
 * The migration steps of k8bas_skyblock_utility.json, in order. Numbers are taken in build order
 * (PLAN §5): step 1 is the Dungeon Hub split (T1.9b), step 2 the updater keys (T1.4).
 */
public final class ConfigMigrations {
	public static final String VERSION_FIELD = "configVersion";
	public static final Migrator MIGRATOR = new Migrator(VERSION_FIELD, List.of());

	private ConfigMigrations() {
	}
}
