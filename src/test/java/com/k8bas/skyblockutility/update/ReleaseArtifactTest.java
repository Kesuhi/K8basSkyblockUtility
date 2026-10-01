package com.k8bas.skyblockutility.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-REL-05 [A] (T1.15): the jar this build produces satisfies the release contract (REQ-REL-06,
 * REQ-REL-07), checked with the updater's own code, so a release the build makes is one the
 * updater can find.
 */
class ReleaseArtifactTest {
	private static final String MINECRAFT = System.getProperty("k8bas.minecraftVersion");
	private static final String MOD_VERSION = System.getProperty("k8bas.modVersion");

	private static Path jar() {
		return Path.of(System.getProperty("k8bas.releaseJar"));
	}

	@Test
	void theJarNameIsOneTheUpdaterSelects() {
		String name = jar().getFileName().toString();
		assertEquals("k8bas_skyblock_utility-" + MOD_VERSION + "+" + MINECRAFT + ".jar", name);
		assertTrue(AssetSelector.JAR.matcher(name).matches(), name);
		SemVer version = SemVer.parse(MOD_VERSION).orElseThrow();
		Release.Asset asset = new Release.Asset(name, "uploaded", "https://github.com/x/" + name, null, 1);
		assertEquals(asset, AssetSelector.select(version, List.of(asset), MINECRAFT).orElseThrow());
	}

	@Test
	void theModVersionIsTheSameEverywhere() throws IOException {
		JsonObject modJson;
		try (ZipFile zip = new ZipFile(jar().toFile()); InputStream in = zip.getInputStream(zip.getEntry("fabric.mod.json"))) {
			modJson = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		String version = modJson.get("version").getAsString();
		assertEquals(MOD_VERSION + "+" + MINECRAFT, version);
		assertEquals("~26.2", modJson.getAsJsonObject("depends").get("minecraft").getAsString());
		// The running version string the updater compares against.
		assertEquals(SemVer.parse(MOD_VERSION), SemVer.parse(version));
	}

	/** REQ-REL-07: 64 lowercase hex, two spaces, the jar name, LF; UTF-8 without a BOM. */
	@Test
	void theSidecarHoldsTheJarsSha256() throws IOException, NoSuchAlgorithmException {
		Path sidecar = jar().resolveSibling(jar().getFileName() + ".sha256");
		byte[] bytes = Files.readAllBytes(sidecar);
		String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar())))
				+ "  " + jar().getFileName() + "\n";
		assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), bytes);
	}
}
