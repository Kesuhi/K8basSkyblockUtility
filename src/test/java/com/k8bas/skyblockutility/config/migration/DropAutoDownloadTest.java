package com.k8bas.skyblockutility.config.migration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Migration step 2 (T1.4): AC-UPD-20 [A] config rows. */
class DropAutoDownloadTest {
	@TempDir
	Path dir;

	private static JsonObject migrate(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		new DropAutoDownload().apply(root);
		return root;
	}

	@Test
	void theDownloadFlagIsDroppedAndTheCheckFlagKept() {
		JsonObject on = migrate("{\"general\": {\"autoUpdateCheckEnabled\": true, \"autoUpdateDownloadEnabled\": true, \"mobScanRangeBlocks\": 64}}");
		assertEquals(JsonParser.parseString("{\"general\": {\"autoUpdateCheckEnabled\": true, \"mobScanRangeBlocks\": 64}}"), on);
		JsonObject off = migrate("{\"general\": {\"autoUpdateCheckEnabled\": false, \"autoUpdateDownloadEnabled\": true}}");
		assertFalse(off.getAsJsonObject("general").get("autoUpdateCheckEnabled").getAsBoolean());
		assertFalse(off.getAsJsonObject("general").has("updateChannel"));
	}

	@Test
	void aConfigWithoutAGeneralSectionIsLeftAlone() {
		assertEquals(JsonParser.parseString("{\"modules\": {}}"), migrate("{\"modules\": {}}"));
		assertEquals(JsonParser.parseString("{\"general\": 5}"), migrate("{\"general\": 5}"));
	}

	/** The user's 1.0.1-shaped config with the check turned off: an opt-out stays an opt-out. */
	@Test
	void aMigratedOptOutStaysOffAndNothingDownloadRelatedIsWritten() throws IOException {
		Path file = dir.resolve("k8bas_skyblock_utility.json");
		try (InputStream in = getClass().getResourceAsStream("/fixtures/config-1.0.1-shaped.json")) {
			String original = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			Files.writeString(file, original.replace("\"autoUpdateCheckEnabled\": true", "\"autoUpdateCheckEnabled\": false"));
		}
		ConfigManager.load(file);
		ConfigManager.flush();

		assertFalse(ConfigManager.general().autoUpdateCheckEnabled);
		String saved = Files.readString(file);
		JsonObject general = JsonParser.parseString(saved).getAsJsonObject().getAsJsonObject("general");
		assertFalse(general.get("autoUpdateCheckEnabled").getAsBoolean());
		assertFalse(saved.contains("autoUpdateDownloadEnabled"));
		assertFalse(saved.contains("updateChannel"));
		assertEquals(2, JsonParser.parseString(saved).getAsJsonObject().get("configVersion").getAsInt());
		assertTrue(Files.exists(dir.resolve("k8bas_skyblock_utility.json.v0.bak")));
	}
}
