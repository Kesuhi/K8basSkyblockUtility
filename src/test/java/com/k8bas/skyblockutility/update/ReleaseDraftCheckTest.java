package com.k8bas.skyblockutility.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-REL-09 [A] (T1.17): a synthetic draft fixture passes the pre-publish checks, and each way a
 * draft breaks the release contract (REQ-REL-07, REQ-REL-11) fails with its own problem. The fixture
 * is hand-written in the shape of GitHub's release object, with placeholder ids; it is not recorded
 * from a real draft. A recorded one is saved during the AC-REL-14 dry run (RELEASING.md). The jar and
 * the sidecar are built here, so the test does not depend on the real build; the fixture's digests
 * and sizes are filled in from them.
 */
class ReleaseDraftCheckTest {
	private static final String VERSION = "1.1.0";
	private static final String MC = "26.2";
	private static final String JAR = "k8bas_skyblock_utility-1.1.0+26.2.jar";
	private static final String SIDECAR = JAR + ".sha256";
	/** The fixture's body, as scripts/changelog-lint.sh --extract prints it (trailing newline, a Markdown line break). */
	private static final String NOTES = """
			**Requirements:** Minecraft 26.2 (Fabric) with Java 25.

			**Upgrading from 1.0.1:** replace the jar. Settings migrate on the first start.

			### Changed

			- **Behaviour change: the outline is visible-only.** \s
			  Blocks hide the outline.

			### Fixed

			- A fix.
			""";

	@TempDir
	Path dir;
	private Path jar;
	private Path sidecar;

	@BeforeEach
	void buildTheJarAndSidecar() throws IOException {
		jar = jar(JAR, modJson("k8bas_skyblock_utility", "1.1.0+26.2", "~26.2"));
		sidecar = sidecar(SIDECAR, sha256(jar) + "  " + JAR + "\n");
	}

	@Test
	void theSyntheticDraftPasses() throws IOException {
		assertEquals(List.of(), check(draft(jar, sidecar)));
	}

	@Test
	void aBodyWithCrlfAndTrailingWhitespacePasses() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.addProperty("body", NOTES.replace("\n", "\r\n") + "\r\n  \r\n");
		assertEquals(List.of(), check(draft));
	}

	@Test
	void aPublishedReleaseFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.addProperty("draft", false);
		assertOnly(check(draft), "is not a draft");
	}

	@Test
	void aWrongTitleFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.addProperty("name", "K8bas Skyblock Utility 1.1.0");
		assertOnly(check(draft), "release title is 'K8bas Skyblock Utility 1.1.0'");
	}

	@Test
	void aWrongTagFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.addProperty("tag_name", "v1.1.0+26.2");
		assertOnly(check(draft), "tag_name is 'v1.1.0+26.2'");
		draft.addProperty("tag_name", "1.1.0");
		assertOnly(check(draft), "tag_name is '1.1.0'");
	}

	@Test
	void aMissingSidecarAssetFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		removeAsset(draft, SIDECAR);
		assertOnly(check(draft), "sidecar asset " + SIDECAR + " is missing");
	}

	@Test
	void aMissingJarAssetFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		removeAsset(draft, JAR);
		assertOnly(check(draft), "jar asset " + JAR + " is missing", "would not offer");
	}

	@Test
	void anExtraSourcesJarFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.getAsJsonArray("assets").add(asset("k8bas_skyblock_utility-1.1.0+26.2-sources.jar"));
		assertOnly(check(draft), "unexpected asset k8bas_skyblock_utility-1.1.0+26.2-sources.jar: only the release jar");
	}

	@Test
	void anyOtherAssetFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.getAsJsonArray("assets").add(asset("k8bas_skyblock_utility-1.1.0+26.1.jar"));
		draft.getAsJsonArray("assets").add(asset("notes.txt"));
		assertOnly(check(draft), "unexpected asset k8bas_skyblock_utility-1.1.0+26.1.jar", "unexpected asset notes.txt");
	}

	@Test
	void anAssetStillUploadingFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		asset(draft, JAR).addProperty("state", "open");
		// The updater's own selector skips the jar too.
		assertOnly(check(draft), "asset " + JAR + " has state 'open'", "would not offer");
		draft = draft(jar, sidecar);
		asset(draft, SIDECAR).addProperty("state", "open");
		assertOnly(check(draft), "asset " + SIDECAR + " has state 'open'");
	}

	@Test
	void aDigestMismatchFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		asset(draft, JAR).addProperty("digest", "sha256:" + sha256("another build".getBytes(StandardCharsets.UTF_8)));
		assertOnly(check(draft), "jar asset's digest");
		draft = draft(jar, sidecar);
		asset(draft, SIDECAR).addProperty("digest", "sha256:" + sha256("another sidecar".getBytes(StandardCharsets.UTF_8)));
		assertOnly(check(draft), "sidecar asset's digest");
	}

	@Test
	void aMissingOrMalformedDigestFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		asset(draft, JAR).remove("digest");
		assertOnly(check(draft), "jar asset has no digest");
		draft = draft(jar, sidecar);
		asset(draft, JAR).addProperty("digest", "sha256:" + sha256(jar).toUpperCase());
		assertOnly(check(draft), "is not 'sha256:<64 lowercase hex>'");
	}

	@Test
	void aSizeMismatchFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		asset(draft, JAR).addProperty("size", 1);
		assertOnly(check(draft), "jar asset's size is 1 bytes");
	}

	@Test
	void aSidecarWithABomFails() throws IOException {
		sidecar = sidecar(SIDECAR, ReleaseDraftCheck.BOM + sha256(jar) + "  " + JAR + "\n");
		assertOnly(check(draft(jar, sidecar)), "starts with a UTF-8 BOM");
	}

	@Test
	void aSidecarWithCrlfFails() throws IOException {
		sidecar = sidecar(SIDECAR, sha256(jar) + "  " + JAR + "\r\n");
		assertOnly(check(draft(jar, sidecar)), "has a CR (CRLF line ending");
	}

	@Test
	void aSidecarInAnotherFormatFails() throws IOException {
		// What Git Bash's sha256sum writes on Windows: one space and the binary-mode marker.
		sidecar = sidecar(SIDECAR, sha256(jar) + " *" + JAR + "\n");
		assertOnly(check(draft(jar, sidecar)), "is not '<64 lowercase hex><two spaces><jar name><LF>'");
		sidecar = sidecar(SIDECAR, sha256(jar) + "  " + JAR);
		assertOnly(check(draft(jar, sidecar)), "is not '<64 lowercase hex><two spaces><jar name><LF>'");
	}

	@Test
	void aSidecarForAnotherJarFails() throws IOException {
		sidecar = sidecar(SIDECAR, sha256("another build".getBytes(StandardCharsets.UTF_8)) + "  " + JAR + "\n");
		assertOnly(check(draft(jar, sidecar)), "the sidecar's hash");
		sidecar = sidecar(SIDECAR, sha256(jar) + "  k8bas_skyblock_utility-1.1.0%2B26.2.jar\n");
		assertOnly(check(draft(jar, sidecar)), "the sidecar names k8bas_skyblock_utility-1.1.0%2B26.2.jar");
	}

	@Test
	void aBodyThatDiffersFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.addProperty("body", NOTES.replace("- A fix.", "- Another fix."));
		assertOnly(check(draft), "release body differs from the CHANGELOG section at line 12");
		// Only CRLF and trailing whitespace at the very end are ignored: a lost line break is a difference.
		assertTrue(NOTES.contains(".**  \n"));
		draft.addProperty("body", NOTES.replace(".**  \n", ".**\n"));
		assertOnly(check(draft), "release body differs from the CHANGELOG section at line 7");
		draft.addProperty("body", "\n" + NOTES);
		assertOnly(check(draft), "at line 1");
		draft.remove("body");
		assertOnly(check(draft), "at line 1");
	}

	@Test
	void aWrongPreReleaseFlagFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.addProperty("prerelease", true);
		// On the STABLE channel the updater would not offer it either.
		assertOnly(check(draft), "pre-release flag is set, but 1.1.0 has no pre-release part", "would not offer this release on the STABLE channel");
	}

	@Test
	void aPreReleaseNeedsTheFlag() throws IOException {
		String version = "1.2.0-beta.1";
		String name = "k8bas_skyblock_utility-" + version + "+26.2.jar";
		Path betaJar = jar(name, modJson("k8bas_skyblock_utility", version + "+26.2", "~26.2"));
		Path betaSidecar = sidecar(name + ".sha256", sha256(betaJar) + "  " + name + "\n");
		JsonObject draft = draft(betaJar, betaSidecar);
		draft.addProperty("tag_name", "v" + version);
		draft.addProperty("name", "K8bas Skyblock Utility v" + version);
		asset(draft, JAR).addProperty("name", name);
		asset(draft, SIDECAR).addProperty("name", name + ".sha256");
		draft.addProperty("prerelease", true);
		assertEquals(List.of(), ReleaseDraftCheck.check(draft.toString(), betaJar, betaSidecar, NOTES, version, MC));
		draft.addProperty("prerelease", false);
		assertOnly(ReleaseDraftCheck.check(draft.toString(), betaJar, betaSidecar, NOTES, version, MC),
				"pre-release flag is not set, but 1.2.0-beta.1 has a pre-release part");
	}

	@Test
	void aWrongModJsonVersionFails() throws IOException {
		jar = jar(JAR, modJson("k8bas_skyblock_utility", "1.1.0", "~26.2"));
		sidecar = sidecar(SIDECAR, sha256(jar) + "  " + JAR + "\n");
		assertOnly(check(draft(jar, sidecar)), "fabric.mod.json version is '1.1.0', expected '1.1.0+26.2'");
	}

	@Test
	void aWrongMinecraftRangeFails() throws IOException {
		jar = jar(JAR, modJson("k8bas_skyblock_utility", "1.1.0+26.2", "~26.1"));
		sidecar = sidecar(SIDECAR, sha256(jar) + "  " + JAR + "\n");
		assertOnly(check(draft(jar, sidecar)), "depends.minecraft \"~26.1\" does not accept Minecraft 26.2");
	}

	@Test
	void aWrongModIdFails() throws IOException {
		jar = jar(JAR, modJson("k8bas_skyblock_utilities", "1.1.0+26.2", "~26.2"));
		sidecar = sidecar(SIDECAR, sha256(jar) + "  " + JAR + "\n");
		assertOnly(check(draft(jar, sidecar)), "fabric.mod.json id is 'k8bas_skyblock_utilities'");
	}

	@Test
	void theCommandLineReportsAndExits() throws IOException {
		Path json = dir.resolve("draft.json");
		Path notes = dir.resolve("notes.md");
		Files.writeString(json, draft(jar, sidecar).toString());
		Files.writeString(notes, NOTES);
		String[] args = {json.toString(), jar.toString(), sidecar.toString(), notes.toString(), VERSION, MC};

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		assertEquals(0, run(args, out, err));
		assertTrue(out.toString(StandardCharsets.UTF_8).contains("PASS for the v1.1.0 draft"), out.toString(StandardCharsets.UTF_8));
		assertTrue(out.toString(StandardCharsets.UTF_8).contains(JAR + " sha256:" + sha256(jar)));

		JsonObject wrong = draft(jar, sidecar);
		wrong.addProperty("name", "wrong");
		Files.writeString(json, wrong.toString());
		err.reset();
		assertEquals(1, run(args, out, err));
		assertTrue(err.toString(StandardCharsets.UTF_8).contains("FAIL, 1 problem(s)"), err.toString(StandardCharsets.UTF_8));
		assertTrue(err.toString(StandardCharsets.UTF_8).contains("  - the release title is 'wrong'"));

		assertEquals(2, run(Arrays.copyOf(args, 5), out, err));
		args[3] = dir.resolve("missing.md").toString();
		assertEquals(2, run(args, out, err));
	}

	@Test
	void aReleaseListInsteadOfOneReleaseFails() throws IOException {
		JsonArray list = new JsonArray();
		list.add(draft(jar, sidecar));
		assertOnly(ReleaseDraftCheck.check(list.toString(), jar, sidecar, NOTES, VERSION, MC), "not one release object");
	}

	@Test
	void anUnparseableDraftJsonFails() throws IOException {
		assertOnly(ReleaseDraftCheck.check("{\"tag_name\": \"v1.1.0\",", jar, sidecar, NOTES, VERSION, MC), "the draft JSON cannot be parsed");
		assertOnly(ReleaseDraftCheck.check("", jar, sidecar, NOTES, VERSION, MC), "not one release object");
		// A BOM in front of the JSON is not a problem of the draft.
		assertEquals(List.of(), ReleaseDraftCheck.check(ReleaseDraftCheck.BOM + draft(jar, sidecar).toString(), jar, sidecar, NOTES, VERSION, MC));
	}

	@Test
	void aReleaseWithoutHtmlUrlFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.remove("html_url");
		assertOnly(check(draft), "the updater skips this release (it needs tag_name and html_url)");
	}

	@Test
	void anAssetAttachedTwiceFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		draft.getAsJsonArray("assets").add(asset(draft, SIDECAR).deepCopy());
		assertOnly(check(draft), "the asset " + SIDECAR + " is attached twice");
	}

	@Test
	void anAssetWithoutANameFails() throws IOException {
		JsonObject draft = draft(jar, sidecar);
		JsonObject nameless = asset("nameless.txt");
		nameless.remove("name");
		draft.getAsJsonArray("assets").add(nameless);
		assertOnly(check(draft), "an asset has no name");
		draft = draft(jar, sidecar);
		draft.getAsJsonArray("assets").add("not an asset object");
		assertOnly(check(draft), "an asset has no name");
	}

	@Test
	void localFilesWithTheWrongNameFail() throws IOException {
		Path renamedJar = Files.copy(jar, dir.resolve("k8bas_skyblock_utility-1.1.0.jar"));
		assertOnly(ReleaseDraftCheck.check(draft(jar, sidecar).toString(), renamedJar, sidecar, NOTES, VERSION, MC),
				"the local jar is named k8bas_skyblock_utility-1.1.0.jar, expected " + JAR);
		Path renamedSidecar = Files.copy(sidecar, dir.resolve("sha256.txt"));
		assertOnly(ReleaseDraftCheck.check(draft(jar, sidecar).toString(), jar, renamedSidecar, NOTES, VERSION, MC),
				"the local sidecar is named sha256.txt, expected " + SIDECAR);
	}

	@Test
	void aSidecarThatIsNotUtf8Fails() throws IOException {
		Files.write(sidecar, new byte[] {(byte) 0xff, (byte) 0xfe, 'a', 0});
		assertOnly(check(draft(jar, sidecar)), "the sidecar is not valid UTF-8");
	}

	@Test
	void aJarWithoutModJsonFails() throws IOException {
		jar = zip(JAR, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");
		sidecar = sidecar(SIDECAR, sha256(jar) + "  " + JAR + "\n");
		assertOnly(check(draft(jar, sidecar)), "the jar has no fabric.mod.json");
	}

	@Test
	void aModJsonWithoutAMinecraftDependencyFails() throws IOException {
		jar = zip(JAR, "fabric.mod.json", """
				{"schemaVersion": 1, "id": "k8bas_skyblock_utility", "version": "1.1.0+26.2", "depends": {"fabricloader": ">=0.19.5"}}
				""");
		sidecar = sidecar(SIDECAR, sha256(jar) + "  " + JAR + "\n");
		assertOnly(check(draft(jar, sidecar)), "fabric.mod.json has no depends.minecraft");
	}

	@Test
	void aMinecraftDependencyListPassesIfOneEntryAccepts() throws IOException {
		jar = zip(JAR, "fabric.mod.json", """
				{"schemaVersion": 1, "id": "k8bas_skyblock_utility", "version": "1.1.0+26.2", "depends": {"minecraft": ["~26.2", "~26.1"]}}
				""");
		sidecar = sidecar(SIDECAR, sha256(jar) + "  " + JAR + "\n");
		assertEquals(List.of(), check(draft(jar, sidecar)));
	}

	@Test
	void aBodyDifferenceNamesTheCodePoints() throws IOException {
		String notes = NOTES.replace("- A fix.", "- A fix (slider 0\u2013128).");
		JsonObject draft = draft(jar, sidecar);
		draft.addProperty("body", notes.replace('\u2013', '-'));
		assertOnly(ReleaseDraftCheck.check(draft.toString(), jar, sidecar, notes, VERSION, MC),
				"at line 12: got '- A fix (slider 0-128).', expected '- A fix (slider 0\u2013128).' (column 18: got U+002D, expected U+2013)");
	}

	@Test
	void unreadableInputsAreUsageErrorsThatNameTheFile() throws IOException {
		Path json = dir.resolve("draft.json");
		Path notes = dir.resolve("notes.md");
		// What Windows PowerShell 5.1's ">" writes: UTF-16LE with a BOM.
		Files.write(json, (ReleaseDraftCheck.BOM + draft(jar, sidecar).toString()).getBytes(StandardCharsets.UTF_16LE));
		Files.writeString(notes, NOTES);
		String[] args = {json.toString(), jar.toString(), sidecar.toString(), notes.toString(), VERSION, MC};
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		assertEquals(2, run(args, out, err));
		assertTrue(err.toString(StandardCharsets.UTF_8).contains("cannot read the draft JSON " + json + " as UTF-8 (MalformedInputException)"),
				err.toString(StandardCharsets.UTF_8));

		Files.writeString(json, draft(jar, sidecar).toString());
		args[3] = dir.resolve("missing.md").toString();
		err.reset();
		assertEquals(2, run(args, out, err));
		assertTrue(err.toString(StandardCharsets.UTF_8).contains("cannot read the notes file " + args[3] + " (NoSuchFileException)"),
				err.toString(StandardCharsets.UTF_8));
	}

	// --- helpers ---

	private List<String> check(JsonObject draft) {
		return ReleaseDraftCheck.check(draft.toString(), jar, sidecar, NOTES, VERSION, MC);
	}

	private static int run(String[] args, ByteArrayOutputStream out, ByteArrayOutputStream err) {
		return ReleaseDraftCheck.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
	}

	/** Every problem matches one of the fragments, and every fragment matches a problem. */
	private static void assertOnly(List<String> problems, String... fragments) {
		for (String fragment : fragments) {
			assertTrue(problems.stream().anyMatch(problem -> problem.contains(fragment)), "no problem with '" + fragment + "' in " + problems);
		}
		for (String problem : problems) {
			assertTrue(Arrays.stream(fragments).anyMatch(problem::contains), "unexpected problem '" + problem + "'");
		}
	}

	/** The synthetic fixture, with the digests and sizes of the given local files. */
	private static JsonObject draft(Path jar, Path sidecar) throws IOException {
		JsonObject draft;
		try (InputStream in = ReleaseDraftCheckTest.class.getResourceAsStream("/fixtures/release-draft-v1.1.0.json")) {
			draft = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		for (JsonElement element : draft.getAsJsonArray("assets")) {
			JsonObject asset = element.getAsJsonObject();
			Path local = asset.get("name").getAsString().endsWith(".sha256") ? sidecar : jar;
			asset.addProperty("digest", "sha256:" + sha256(local));
			asset.addProperty("size", Files.size(local));
		}
		return draft;
	}

	private static JsonObject asset(JsonObject draft, String name) {
		for (JsonElement element : draft.getAsJsonArray("assets")) {
			if (element.getAsJsonObject().get("name").getAsString().equals(name)) {
				return element.getAsJsonObject();
			}
		}
		throw new AssertionError("no asset " + name);
	}

	private static JsonObject asset(String name) {
		JsonObject asset = new JsonObject();
		asset.addProperty("name", name);
		asset.addProperty("state", "uploaded");
		asset.addProperty("size", 10);
		asset.addProperty("digest", "sha256:" + "1".repeat(64));
		asset.addProperty("content_type", "application/octet-stream");
		asset.addProperty("browser_download_url", "https://github.com/Kesuhi/K8basSkyblockUtility/releases/download/untagged-0123456789abcdef0123/"
				+ name.replace("+", "%2B"));
		return asset;
	}

	private static void removeAsset(JsonObject draft, String name) {
		draft.getAsJsonArray("assets").remove(asset(draft, name));
	}

	private Path jar(String name, String modJson) throws IOException {
		return zip(name, "fabric.mod.json", modJson);
	}

	/** A jar named {@code name} with one entry. */
	private Path zip(String name, String entry, String content) throws IOException {
		Path path = dir.resolve(name);
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
			zip.putNextEntry(new ZipEntry(entry));
			zip.write(content.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return path;
	}

	private static String modJson(String id, String version, String minecraft) {
		return """
				{"schemaVersion": 1, "id": "%s", "version": "%s", "depends": {"fabricloader": ">=0.19.5", "minecraft": "%s"}}
				""".formatted(id, version, minecraft);
	}

	private Path sidecar(String name, String content) throws IOException {
		Path path = dir.resolve(name);
		Files.write(path, content.getBytes(StandardCharsets.UTF_8));
		return path;
	}

	private static String sha256(Path file) throws IOException {
		return sha256(Files.readAllBytes(file));
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
