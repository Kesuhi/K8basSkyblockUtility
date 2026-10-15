package com.k8bas.skyblockutility.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Licence enforcement for bundled data (T3.0j): AC-DATA-03 (each case fails) and AC-XC-03 (validator part:
 * an MIT file without a notice, any NC/SA licence). The set is synthetic: one CC0 table and one MIT table
 * from the approved NEU-REPO upstream, with its notice and licence text; the approved upstreams are the
 * repository's own data-schemas/upstreams.json.
 */
class DataLicenceTest {
	@TempDir
	Path root;
	private Path data;
	private Path schemas;

	private static final String CC0 = """
			{"table": "test_own", "schemaVersion": 1, "dataVersion": 1, "gameVersion": "0.27.1",
			 "generatedAt": "2026-10-09T12:00:00Z", "license": "CC0-1.0",
			 "sources": {"api": {"title": "Hypixel API", "url": "https://api.hypixel.net/v2/resources/skyblock/skills", "license": "official_api", "access": "api"}},
			 "rows": [{"id": "a"}]}
			""";
	private static final String MIT = """
			{"table": "test_rng", "schemaVersion": 1, "dataVersion": 1, "gameVersion": "0.27.1",
			 "generatedAt": "2026-10-09T12:00:00Z", "license": "MIT",
			 "sources": {"neu": {"title": "NotEnoughUpdates-REPO", "url": "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO",
			   "revision": "24564a1ad2", "license": "MIT", "notice": "neu-repo", "access": "git"}},
			 "rows": [{"id": "b"}]}
			""";
	private static final String SCHEMA = """
			{"$schema": "https://json-schema.org/draft/2020-12/schema", "x-keys": {"/rows": "id"},
			 "properties": {"table": {"const": "%s"}, "rows": {"type": "array"}}}
			""";
	private static final String NOTICES = """
			# Third-party notices

			## NotEnoughUpdates-REPO

			- Licence: MIT
			- Files: `thirdparty/neu-repo/rng.json`
			""";

	@BeforeEach
	void validSet() throws IOException {
		Path project = Path.of(System.getProperty("k8bas.projectDir", "."));
		data = Files.createDirectories(root.resolve("data"));
		schemas = Files.createDirectories(root.resolve("data-schemas"));
		for (String file : List.of("envelope.schema.json", "upstreams.json")) {
			Files.copy(project.resolve("data-schemas").resolve(file), schemas.resolve(file));
		}
		write(schemas.resolve("test_own.schema.json"), SCHEMA.formatted("test_own"));
		write(schemas.resolve("test_rng.schema.json"), SCHEMA.formatted("test_rng"));
		write(data.resolve("own.json"), CC0);
		write(data.resolve("thirdparty/neu-repo/rng.json"), MIT);
		write(root.resolve("THIRD_PARTY_NOTICES.md"), NOTICES);
		write(root.resolve("licenses/neu-repo.txt"), "MIT License\n\nCopyright (c) 2020 Moulberry\n");
		DataIndexer.writeIndex(data, schemas);
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	private void edit(Path file, UnaryOperator<String> change) throws IOException {
		write(file, change.apply(Files.readString(file)));
		DataIndexer.writeIndex(data, schemas);
	}

	private void fails(String table, String key, String message) {
		List<DataValidator.Problem> problems = DataValidator.validate(data, schemas).getProblems();
		assertTrue(problems.stream().anyMatch(p -> p.getTable().equals(table) && p.getKey().contains(key) && p.getMessage().contains(message)),
				"expected " + table + " / " + key + " / " + message + " in " + problems);
	}

	@Test
	void theValidSetPassesAndNamesItsUpstreams() {
		DataValidator.Report report = DataValidator.validate(data, schemas);
		assertEquals(List.of(), report.getProblems());
		assertEquals(Set.of("neu-repo"), report.getUpstreams(), "the jar must carry these licence texts");
	}

	@Test
	void anMitTableOutsideTheThirdPartyFolderFails() throws IOException {
		Files.move(data.resolve("thirdparty/neu-repo/rng.json"), data.resolve("rng.json"));
		DataIndexer.writeIndex(data, schemas);
		fails("test_rng", "license", "thirdparty");
	}

	@Test
	void anApacheTableOutsideTheThirdPartyFolderFails() throws IOException {
		edit(data.resolve("own.json"), text -> text.replace("\"CC0-1.0\"", "\"Apache-2.0\""));
		fails("test_own", "license", "thirdparty");
	}

	@Test
	void anUpstreamNotApprovedFails() throws IOException {
		Files.createDirectories(data.resolve("thirdparty/some-mod"));
		Files.move(data.resolve("thirdparty/neu-repo/rng.json"), data.resolve("thirdparty/some-mod/rng.json"));
		DataIndexer.writeIndex(data, schemas);
		fails("test_rng", "thirdparty", "some-mod");
	}

	@Test
	void aLicenceOtherThanTheUpstreamsFails() throws IOException {
		edit(data.resolve("thirdparty/neu-repo/rng.json"), text -> text.replace("\"license\": \"MIT\",\n", "\"license\": \"Apache-2.0\",\n"));
		fails("test_rng", "license", "MIT");
	}

	@Test
	void anMitTableWithoutNoticeFails() throws IOException {
		write(root.resolve("THIRD_PARTY_NOTICES.md"), "# Third-party notices\n");
		fails("test_rng", "THIRD_PARTY_NOTICES", "NotEnoughUpdates-REPO");
	}

	@Test
	void aNoticeThatDoesNotNameTheFileFails() throws IOException {
		write(root.resolve("THIRD_PARTY_NOTICES.md"), NOTICES.replace("`thirdparty/neu-repo/rng.json`", "`thirdparty/neu-repo/other.json`"));
		fails("test_rng", "THIRD_PARTY_NOTICES", "thirdparty/neu-repo/rng.json");
	}

	@Test
	void anUpstreamLicenceTextMissingFails() throws IOException {
		Files.delete(root.resolve("licenses/neu-repo.txt"));
		fails("test_rng", "licenses", "neu-repo.txt");
	}

	@Test
	void anNcOrSaLicenceFails() throws IOException {
		edit(data.resolve("own.json"), text -> text.replace("\"license\": \"official_api\"", "\"license\": \"CC-BY-NC-SA-3.0\""));
		fails("test_own", "/sources/api/license", "NC");
		edit(data.resolve("own.json"), text -> text.replace("CC-BY-NC-SA-3.0", "CC-BY-SA-4.0"));
		fails("test_own", "/sources/api/license", "SA");
	}

	@Test
	void georgeIsNeverBundled() throws IOException {
		// NEU's george.json is CC BY-NC-SA (PLAN §2).
		write(data.resolve("thirdparty/neu-repo/george.json"), MIT.replace("test_rng", "test_george"));
		write(schemas.resolve("test_george.schema.json"), SCHEMA.formatted("test_george"));
		write(root.resolve("THIRD_PARTY_NOTICES.md"), NOTICES + "- Files: `thirdparty/neu-repo/george.json`\n");
		DataIndexer.writeIndex(data, schemas);
		fails("test_george", "path", "george.json");
	}

	@Test
	void anMitSourceNeedsAnApprovedNotice() throws IOException {
		edit(data.resolve("thirdparty/neu-repo/rng.json"), text -> text.replace("\"notice\": \"neu-repo\"", "\"notice\": \"some-mod\""));
		fails("test_rng", "/sources/neu/notice", "some-mod");
		edit(data.resolve("thirdparty/neu-repo/rng.json"), text -> text.replace("\"notice\": \"some-mod\", ", ""));
		fails("test_rng", "/sources/neu/notice", "MIT");
	}

	@Test
	void aWikiOrForumSourceMustBeAPageAPersonCited() throws IOException {
		// D-16, D-28: wiki, Fandom, hypixel.net and Reddit pages only as citations a person supplied.
		for (String url : List.of("https://hypixelskyblock.minecraft.wiki/w/Bestiary", "https://hypixel-skyblock.fandom.com/wiki/Scatha",
				"https://hypixel.net/threads/5786151/", "https://www.reddit.com/r/HypixelSkyblock/comments/x")) {
			setUp();
			edit(data.resolve("own.json"), text -> text.replace("https://api.hypixel.net/v2/resources/skyblock/skills", url)
					.replace("\"official_api\"", "\"facts\"").replace("\"access\": \"api\"", "\"access\": \"git\""));
			fails("test_own", "/sources/api/access", "user_supplied");
			edit(data.resolve("own.json"), text -> text.replace("\"access\": \"git\"", "\"access\": \"user_supplied\""));
			assertEquals(List.of(), DataValidator.validate(data, schemas).getProblems(), url);
		}
	}

	@Test
	void anNcOrSaLicenceAnywhereFails() throws IOException {
		// A row's own licence, and a licence written out in full.
		edit(data.resolve("own.json"), text -> text.replace("{\"id\": \"a\"}", "{\"id\": \"a\", \"license\": \"CC-BY-NC-SA-3.0\"}"));
		fails("test_own", "/rows/0/license", "NC");
		setUp();
		edit(data.resolve("own.json"), text -> text.replace("\"license\": \"official_api\"", "\"license\": \"Attribution-NonCommercial-ShareAlike 3.0 Unported\""));
		fails("test_own", "/sources/api/license", "NonCommercial");
	}

	@Test
	void aWikiSourceWithoutAUsableUrlStillNeedsAPerson() throws IOException {
		// No url, but a wiki cross-check; an unparsable url; a Reddit short link.
		edit(data.resolve("own.json"), text -> text.replace("\"url\": \"https://api.hypixel.net/v2/resources/skyblock/skills\", ", "\"kind\": \"wiki_crosscheck\", ")
				.replace("\"access\": \"api\"", "\"access\": \"git\""));
		fails("test_own", "/sources/api/access", "user_supplied");
		setUp();
		edit(data.resolve("own.json"), text -> text.replace("https://api.hypixel.net/v2/resources/skyblock/skills", "https://hypixelskyblock.minecraft.wiki/w/Scatha Pet"));
		fails("test_own", "/sources/api/url", "absolute");
		setUp();
		edit(data.resolve("own.json"), text -> text.replace("https://api.hypixel.net/v2/resources/skyblock/skills", "https://redd.it/abc123"));
		fails("test_own", "/sources/api/access", "redd.it");
	}

	@Test
	void citingGeorgeFails() throws IOException {
		edit(data.resolve("thirdparty/neu-repo/rng.json"), text -> text.replace("https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO\"",
				"https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/blob/master/constants/george.json\""));
		fails("test_rng", "/sources/neu", "george.json");
	}

	private void setUp() throws IOException {
		for (Path path : List.of(data, schemas, root.resolve("licenses"))) {
			try (var walk = Files.walk(path)) {
				walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
		Files.deleteIfExists(root.resolve("THIRD_PARTY_NOTICES.md"));
		validSet();
	}
}
