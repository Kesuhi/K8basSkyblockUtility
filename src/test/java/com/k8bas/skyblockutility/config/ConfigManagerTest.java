package com.k8bas.skyblockutility.config;

import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterization of 1.0.1 config loading; the baseline the migrations of AC-CFG-01 build on. */
class ConfigManagerTest {
	@TempDir
	Path dir;

	private Path fixture() throws IOException {
		Path file = dir.resolve("k8bas_skyblock_utility.json");
		try (InputStream in = getClass().getResourceAsStream("/fixtures/config-1.0.1-shaped.json")) {
			Files.copy(in, file);
		}
		return file;
	}

	private static MobHighlighterConfig mobs() {
		return ConfigManager.getModuleSection("mob_highlighter", MobHighlighterConfig.class, MobHighlighterConfig::new);
	}

	private static NpcSearchConfig npcs() {
		return ConfigManager.getModuleSection("npc_search", NpcSearchConfig.class, NpcSearchConfig::new);
	}

	@Test
	void loadsAConfigShapedLikeTheUsers101Config() throws IOException {
		ConfigManager.load(fixture());

		assertTrue(ConfigManager.general().autoUpdateCheckEnabled);
		assertTrue(ConfigManager.general().autoUpdateDownloadEnabled);
		assertEquals(128, ConfigManager.general().mobScanRangeBlocks);
		assertEquals(5, mobs().rules.size());
		assertEquals(5, npcs().rules.size());
		assertEquals(3, npcs().rules.stream().filter(r -> !r.fixed && "Catacombs".equals(r.island)).count());
		assertEquals("\\[Lv\\d+\\] Ghoul", mobs().rules.get(4).namePattern);
	}

	@Test
	void saveAndReloadKeepsEveryValue() throws IOException {
		Path file = fixture();
		ConfigManager.load(file);
		ConfigManager.save();
		ConfigManager.flush();
		String saved = Files.readString(file);

		ConfigManager.load(file);
		ConfigManager.save();
		ConfigManager.flush();
		assertEquals(saved, Files.readString(file));
		assertEquals(128, ConfigManager.general().mobScanRangeBlocks);
		assertEquals(5, npcs().rules.size());
	}

	@Test
	void missingFileGivesDefaultsAndCreatesIt() {
		Path file = dir.resolve("new.json");
		ConfigManager.load(file);
		ConfigManager.flush();

		assertTrue(Files.exists(file));
		assertEquals(64, ConfigManager.general().mobScanRangeBlocks);
		assertTrue(mobs().rules.isEmpty());
	}

	@Test
	void fileWithUtf8BomLoads() throws IOException {
		Path file = fixture();
		Files.writeString(file, "﻿" + Files.readString(file), StandardCharsets.UTF_8);
		ConfigManager.load(file);

		assertEquals(128, ConfigManager.general().mobScanRangeBlocks);
		assertEquals(5, mobs().rules.size());
	}
}
