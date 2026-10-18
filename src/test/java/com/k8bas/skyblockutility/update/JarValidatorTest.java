package com.k8bas.skyblockutility.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.2 (REQ-UPD-15, AC-UPD-16): the downloaded jar is checked by its fabric.mod.json alone, read from the zip without
 * extracting anything. Each fixture that would not run on this game is rejected with its reason.
 */
class JarValidatorTest {
	private static final String VERSION = "1.2.0+26.2";

	@TempDir
	Path dir;

	private String problem(byte[] jar) throws IOException {
		Path file = dir.resolve("k8bas_skyblock_utility-1.2.0+26.2.jar.part");
		Files.write(file, jar);
		return JarValidator.problem(file, VERSION, "26.2", "0.19.5");
	}

	private String problem(String id, String version, String minecraft, String loader, String environment) throws IOException {
		return problem(TestJars.jar(TestJars.modJson(id, version, minecraft, loader, environment)));
	}

	@Test
	void theModsOwnBuildPasses() throws IOException {
		assertNull(problem(TestJars.mod(VERSION)));
	}

	/** AC-UPD-16: wrong id, wrong version, "~26.1" on 26.2 and a server-only jar are each rejected. */
	@Test
	void eachIncompatibleFixtureIsRejected() throws IOException {
		assertEquals("its id is 'other_mod', not 'k8bas_skyblock_utility'", problem("other_mod", VERSION, "~26.2", ">=0.19.5", "client"));
		assertEquals("its version is '1.2.1+26.2', not '1.2.0+26.2'", problem("k8bas_skyblock_utility", "1.2.1+26.2", "~26.2", ">=0.19.5", "client"));
		assertEquals("it needs Minecraft ~26.1, not 26.2", problem("k8bas_skyblock_utility", VERSION, "~26.1", ">=0.19.5", "client"));
		assertEquals("it is for the environment 'server', not the client", problem("k8bas_skyblock_utility", VERSION, "~26.2", ">=0.19.5", "server"));
	}

	/** AC-UPD-16: an array predicate is an OR, so one entry that includes 26.2 is enough. */
	@Test
	void anArrayPredicateThatIncludes26_2IsAccepted() throws IOException {
		assertNull(problem("k8bas_skyblock_utility", VERSION, "[\"~26.1\", \">=26.2 <26.3\"]", ">=0.19.5", "client"));
		assertEquals("it needs Minecraft [\"~26.1\",\"~26.3\"], not 26.2",
				problem("k8bas_skyblock_utility", VERSION, "[\"~26.1\", \"~26.3\"]", ">=0.19.5", "client"));
	}

	/** REQ-UPD-15: the loader the jar needs must be the running one or older. */
	@Test
	void aJarThatNeedsANewerLoaderIsRejected() throws IOException {
		assertEquals("it needs Fabric Loader >=0.20.0, not 0.19.5", problem("k8bas_skyblock_utility", VERSION, "~26.2", ">=0.20.0", "client"));
	}

	/** Fabric reads a missing environment as "*"; a missing Minecraft or loader dependency cannot be checked, so it is rejected. */
	@Test
	void missingFieldsAreReadAsFabricReadsThem() throws IOException {
		assertNull(problem("k8bas_skyblock_utility", VERSION, "~26.2", ">=0.19.5", null));
		assertNull(problem("k8bas_skyblock_utility", VERSION, "~26.2", ">=0.19.5", "*"));
		assertEquals("it names no Minecraft version", problem("k8bas_skyblock_utility", VERSION, null, ">=0.19.5", "client"));
		assertEquals("it names no Fabric Loader version", problem("k8bas_skyblock_utility", VERSION, "~26.2", null, "client"));
		assertEquals("its id is (missing), not 'k8bas_skyblock_utility'", problem(null, VERSION, "~26.2", ">=0.19.5", "client"));
	}

	/** Only a zip with a fabric.mod.json of at most 64 KiB, holding a JSON object, is read. */
	@Test
	void anythingElseIsRejectedWithoutReadingFurther() throws IOException {
		assertEquals("it has no fabric.mod.json", problem(TestJars.jar("other.json", "{}".getBytes(StandardCharsets.UTF_8))));
		assertEquals("its fabric.mod.json is not a JSON object", problem(TestJars.jar("[1, 2]")));
		assertEquals("its fabric.mod.json is not valid JSON", problem(TestJars.jar("{\"id\": ")));
		String padded = TestJars.modJson("k8bas_skyblock_utility", VERSION, "~26.2", ">=0.19.5", "client")
				.replace("{\"schemaVersion\": 1", "{\"schemaVersion\": 1, \"pad\": \"" + "x".repeat(JarValidator.MAX_MOD_JSON_BYTES) + "\"");
		assertEquals("its fabric.mod.json is larger than 64 KiB", problem(TestJars.jar(padded)));
		String exact = TestJars.modJson("k8bas_skyblock_utility", VERSION, "~26.2", ">=0.19.5", "client");
		String pad = ", \"pad\": \"\"";
		exact = exact.replace("{\"schemaVersion\": 1", "{\"schemaVersion\": 1, \"pad\": \""
				+ "x".repeat(JarValidator.MAX_MOD_JSON_BYTES - exact.length() - pad.length()) + "\"");
		assertEquals(JarValidator.MAX_MOD_JSON_BYTES, exact.getBytes(StandardCharsets.UTF_8).length);
		assertNull(problem(TestJars.jar(exact)), "exactly 64 KiB");
		// A zip may declare a smaller size than it holds; the read is capped whatever the header says.
		assertEquals("its fabric.mod.json is larger than 64 KiB", problem(TestJars.withDeclaredSize(TestJars.jar(padded), "fabric.mod.json", 100)));
		String notAZip = problem("not a zip".getBytes(StandardCharsets.UTF_8));
		assertNotNull(notAZip);
		assertTrue(notAZip.startsWith("it is not a readable jar"), notAZip);
	}

	/**
	 * A fabric.mod.json that declares a small size but inflates to over 2 GiB is read only up to the cap: reading it all
	 * would not fit one array.
	 */
	@Test
	void aZipBombIsReadOnlyToTheCap() throws IOException {
		byte[] bomb = TestJars.withDeclaredSize(TestJars.bomb(2100), "fabric.mod.json", 100);
		long start = System.nanoTime();
		assertEquals("its fabric.mod.json is larger than 64 KiB", problem(bomb));
		assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000, "stopped at the cap");
	}

	/** A predicate Fabric cannot parse is a reason, not a crash. */
	@Test
	void anUnparsablePredicateIsRejected() throws IOException {
		assertEquals("its Minecraft dependency '~' cannot be parsed", problem("k8bas_skyblock_utility", VERSION, "~", ">=0.19.5", "client"));
		assertEquals("its Fabric Loader dependency '<>1' cannot be parsed", problem("k8bas_skyblock_utility", VERSION, "~26.2", "<>1", "client"));
	}
}
