package com.k8bas.skyblockutility.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled-data registry (T3.0c): REQ-DATA-02 envelope, REQ-DATA-07 fail-safe loading (AC-DATA-05 [A]),
 * REQ-DATA-08 BOM and CRLF (AC-DATA-06), REQ-DATA-09 loading state (AC-DATA-07 [A]), REQ-DATA-11 listing
 * (AC-DATA-09, listing part), EC-DATA-05 (loaded once). Tables are synthetic: no bundled table exists yet.
 */
class DataRegistryTest {
	/** A test table: rows of {id, name}; the decoded value is the list of names. */
	private static final TableSpec<List<String>> NAMES = new TableSpec<>("test_names", 1,
			table -> table.rows(table.getBody().getAsJsonArray("rows"), row -> row.get("id").getAsString(), row -> row.get("name").getAsString()));

	private final Map<String, byte[]> files = new LinkedHashMap<>();
	private final Map<String, String> index = new LinkedHashMap<>();
	private final List<String> warnings = new ArrayList<>();
	private final List<Runnable> queue = new ArrayList<>();
	private final AtomicInteger reads = new AtomicInteger();

	private DataRegistry registry() {
		files.put("index.json", ("{\"indexVersion\":1,\"tables\":{" + String.join(",", index.values()) + "}}").getBytes(StandardCharsets.UTF_8));
		return new DataRegistry(path -> {
			reads.incrementAndGet();
			return files.get(path);
		}, queue::add, Runnable::run, warnings::add);
	}

	private void runQueue() {
		while (!queue.isEmpty()) {
			queue.remove(0).run();
		}
	}

	private static String envelope(String table, int schemaVersion, String rows) {
		return "{\"table\":\"" + table + "\",\"schemaVersion\":" + schemaVersion + ",\"dataVersion\":3,\"gameVersion\":\"0.27.1\","
				+ "\"generatedAt\":\"2026-10-09T12:00:00Z\",\"license\":\"CC0-1.0\",\"sources\":{\"own\":{\"title\":\"test\"}},\"rows\":" + rows + "}";
	}

	/** Adds a table file and its index entry; the entry's checksum matches [bytes] unless [sha] is given. */
	private void table(String id, String path, byte[] bytes, int rows, int schemaVersion, String sha) {
		files.put(path, bytes);
		index.put(id, "\"" + id + "\":{\"path\":\"" + path + "\",\"sha256\":\"" + (sha != null ? sha : sha256(bytes)) + "\",\"rows\":" + rows
				+ ",\"schemaVersion\":" + schemaVersion + ",\"dataVersion\":3,\"gameVersion\":\"0.27.1\"}");
	}

	private void names(String rows, int count) {
		table("test_names", "test/names.json", envelope("test_names", 1, rows).getBytes(StandardCharsets.UTF_8), count, 1, null);
	}

	/** The index checksum: SHA-256 of the text with a BOM removed and CRLF as LF, so Windows and CI agree. */
	private static String sha256(byte[] bytes) {
		String text = new String(bytes, StandardCharsets.UTF_8);
		if (text.startsWith("\uFEFF")) {
			text = text.substring(1);
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void aTableLoadsThroughTheIndex() {
		names("[{\"id\":\"a\",\"name\":\"Alpha\"},{\"id\":\"b\",\"name\":\"Beta\"}]", 2);
		DataRegistry registry = registry();
		TableHandle<List<String>> handle = registry.register(NAMES);
		registry.start();
		runQueue();
		TableState.Loaded<List<String>> loaded = assertInstanceOf(TableState.Loaded.class, handle.state());
		assertEquals(List.of("Alpha", "Beta"), loaded.getValue());
		assertEquals(List.of("Alpha", "Beta"), handle.value());
		assertEquals(3, loaded.getEntry().dataVersion());
		assertEquals("0.27.1", loaded.getEntry().gameVersion());
		assertEquals(List.of(), warnings);
	}

	@Test
	void aMalformedRowIsSkippedWithOneWarning() {
		// AC-DATA-05: one row lacks a required field; every other row loads, one WARN names table and row.
		names("[{\"id\":\"a\",\"name\":\"Alpha\"},{\"id\":\"b\"},{\"id\":\"c\",\"name\":\"Gamma\"}]", 3);
		DataRegistry registry = registry();
		TableHandle<List<String>> handle = registry.register(NAMES);
		registry.start();
		runQueue();
		assertEquals(List.of("Alpha", "Gamma"), handle.value());
		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("test_names") && warnings.get(0).contains("row b"), warnings.get(0));
	}

	@Test
	void bomAndCrlfReadTheSame() {
		// AC-DATA-06: the same table as LF without a BOM and as CRLF with a BOM.
		String lf = "{\n\"table\": \"test_names\",\n\"schemaVersion\": 1,\n\"dataVersion\": 3,\n\"gameVersion\": \"0.27.1\",\n"
				+ "\"generatedAt\": \"2026-10-09T12:00:00Z\",\n\"license\": \"CC0-1.0\",\n\"sources\": {},\n"
				+ "\"rows\": [\n{\"id\": \"a\", \"name\": \"Alpha\"}\n]\n}\n";
		table("test_names", "test/names.json", lf.getBytes(StandardCharsets.UTF_8), 1, 1, null);
		DataRegistry plain = registry();
		TableHandle<List<String>> a = plain.register(NAMES);
		plain.start();
		runQueue();

		// The index keeps the checksum of the LF file: a CRLF checkout (core.autocrlf on Windows) still matches it.
		String lfSha = sha256(lf.getBytes(StandardCharsets.UTF_8));
		byte[] crlf = ("\uFEFF" + lf.replace("\n", "\r\n")).getBytes(StandardCharsets.UTF_8);
		table("test_names", "test/names.json", crlf, 1, 1, lfSha);
		DataRegistry bom = registry();
		TableHandle<List<String>> b = bom.register(NAMES);
		bom.start();
		runQueue();
		assertEquals(List.of("Alpha"), a.value());
		assertEquals(a.value(), b.value());
	}

	@Test
	void aConsumerAskingEarlyGetsLoadingThenTheTable() {
		// AC-DATA-07: a query before loading completes returns "loading", later the full table; never empty.
		names("[{\"id\":\"a\",\"name\":\"Alpha\"}]", 1);
		DataRegistry registry = registry();
		TableHandle<List<String>> handle = registry.register(NAMES);
		List<List<String>> heard = new ArrayList<>();
		handle.onLoaded(heard::add);
		registry.start();
		assertSame(TableState.Loading.INSTANCE, handle.state());
		assertNull(handle.value());
		runQueue();
		assertEquals(List.of("Alpha"), handle.value());
		assertEquals(List.of(List.of("Alpha")), heard);
		// A listener added later hears the loaded table at once.
		handle.onLoaded(heard::add);
		assertEquals(2, heard.size());
	}

	@Test
	void eachFailureDisablesOnlyItsTable() {
		// REQ-DATA-07: missing, unreadable, bad checksum, unknown schemaVersion: that table is unavailable,
		// the others load, and no exception escapes.
		names("[{\"id\":\"a\",\"name\":\"Alpha\"}]", 1);
		table("t_missing", "test/missing.json", new byte[0], 0, 1, null);
		files.remove("test/missing.json");
		table("t_checksum", "test/checksum.json", envelope("t_checksum", 1, "[]").getBytes(StandardCharsets.UTF_8), 0, 1, "00".repeat(32));
		table("t_schema", "test/schema.json", envelope("t_schema", 2, "[]").getBytes(StandardCharsets.UTF_8), 0, 2, null);
		table("t_json", "test/json.json", "{not json".getBytes(StandardCharsets.UTF_8), 0, 1, null);
		table("t_id", "test/id.json", envelope("other_id", 1, "[]").getBytes(StandardCharsets.UTF_8), 0, 1, null);
		table("t_throws", "test/throws.json", envelope("t_throws", 1, "[]").getBytes(StandardCharsets.UTF_8), 0, 1, null);
		table("t_escape", "../outside.json", envelope("t_escape", 1, "[]").getBytes(StandardCharsets.UTF_8), 0, 1, null);
		DataRegistry registry = registry();
		TableHandle<List<String>> names = registry.register(NAMES);
		Map<String, TableHandle<String>> handles = new LinkedHashMap<>();
		for (String id : List.of("t_missing", "t_checksum", "t_schema", "t_json", "t_id", "t_escape", "t_absent")) {
			handles.put(id, registry.register(new TableSpec<>(id, 1, table -> "ok")));
		}
		handles.put("t_throws", registry.register(new TableSpec<>("t_throws", 1, table -> {
			throw new IllegalStateException("bad shape");
		})));
		registry.start();
		runQueue();
		assertEquals(List.of("Alpha"), names.value());
		Map<String, String> reasons = Map.of("t_missing", "missing", "t_checksum", "checksum", "t_schema", "schemaVersion",
				"t_json", "unreadable", "t_id", "table id", "t_escape", "path", "t_absent", "not in the index", "t_throws", "could not be read");
		reasons.forEach((id, reason) -> {
			TableState.Unavailable state = assertInstanceOf(TableState.Unavailable.class, handles.get(id).state(), id);
			assertTrue(state.getReason().contains(reason), id + ": " + state.getReason());
		});
		assertEquals(8, warnings.size(), "one warning per unavailable table: " + warnings);
	}

	@Test
	void aMissingIndexMakesEveryTableUnavailable() {
		DataRegistry registry = new DataRegistry(path -> null, queue::add, Runnable::run, warnings::add);
		TableHandle<List<String>> handle = registry.register(NAMES);
		registry.start();
		runQueue();
		assertTrue(((TableState.Unavailable) handle.state()).getReason().contains("index"));
	}

	@Test
	void aTableIsLoadedOnceAndSharedByItsConsumers() {
		// EC-DATA-05: the same table asked for twice is loaded once; both get the same instance.
		names("[{\"id\":\"a\",\"name\":\"Alpha\"}]", 1);
		DataRegistry registry = registry();
		TableHandle<List<String>> first = registry.register(NAMES);
		TableHandle<List<String>> second = registry.register(NAMES);
		assertSame(first, second);
		registry.start();
		runQueue();
		assertSame(first.value(), second.value());
		assertEquals(2, reads.get(), "the index and the table, each read once");
	}

	@Test
	void aTableRegisteredAfterStartIsLoadedToo() {
		names("[{\"id\":\"a\",\"name\":\"Alpha\"}]", 1);
		DataRegistry registry = registry();
		registry.start();
		runQueue();
		TableHandle<List<String>> late = registry.register(NAMES);
		runQueue();
		assertEquals(List.of("Alpha"), late.value());
	}

	@Test
	void theListingShowsEveryIndexedTable() {
		// AC-DATA-09 (listing): every indexed table with its dataVersion and gameVersion, and its state.
		names("[{\"id\":\"a\",\"name\":\"Alpha\"}]", 1);
		table("t_unused", "test/unused.json", envelope("t_unused", 1, "[]").getBytes(StandardCharsets.UTF_8), 0, 1, null);
		DataRegistry registry = registry();
		registry.register(NAMES);
		registry.start();
		runQueue();
		assertEquals(List.of("test_names: data v3, SkyBlock 0.27.1, loaded", "t_unused: data v3, SkyBlock 0.27.1, not used"), registry.describe());
	}

	@Test
	void theRowHelperReadsOnlyObjects() {
		names("[{\"id\":\"a\",\"name\":\"Alpha\"},\"not a row\",{\"id\":\"c\",\"name\":\"Gamma\"}]", 3);
		DataRegistry registry = registry();
		TableHandle<List<String>> handle = registry.register(NAMES);
		registry.start();
		runQueue();
		assertEquals(List.of("Alpha", "Gamma"), handle.value());
		assertTrue(warnings.get(0).contains("row #1"), warnings.toString());
	}

	@Test
	void eachRowArrayLogsItsOwnSkippedRows() {
		// Two arrays of one table each skip their row #0: both are logged, each with its array's label.
		table("test_pair", "test/pair.json", ("{\"table\":\"test_pair\",\"schemaVersion\":1,\"dataVersion\":1,\"gameVersion\":\"0.27.1\","
				+ "\"generatedAt\":\"2026-10-09T12:00:00Z\",\"license\":\"CC0-1.0\",\"sources\":{},"
				+ "\"tasks\":[\"bad\",{\"name\":\"ok\"}],\"generators\":[7,{\"name\":\"ok\"}]}").getBytes(StandardCharsets.UTF_8), 4, 1, null);
		DataRegistry registry = registry();
		TableHandle<Integer> handle = registry.register(new TableSpec<>("test_pair", 1, table ->
				table.rows("tasks", table.getBody().getAsJsonArray("tasks"), row -> null, row -> row.get("name").getAsString()).size()
						+ table.rows("generators", table.getBody().getAsJsonArray("generators"), row -> null, row -> row.get("name").getAsString()).size()));
		registry.start();
		runQueue();
		assertEquals(2, handle.value());
		assertEquals(List.of("Data table test_pair: tasks row #0 skipped (not an object)",
				"Data table test_pair: generators row #0 skipped (not an object)"), warnings);
	}

	@Test
	void aDecoderErrorMakesOnlyItsTableUnavailable() {
		// A StackOverflowError (deeply nested data) is not a RuntimeException; the table must not stay "loading".
		names("[{\"id\":\"a\",\"name\":\"Alpha\"}]", 1);
		table("t_deep", "test/deep.json", envelope("t_deep", 1, "[]").getBytes(StandardCharsets.UTF_8), 0, 1, null);
		DataRegistry registry = registry();
		TableHandle<String> deep = registry.register(new TableSpec<>("t_deep", 1, table -> {
			throw new StackOverflowError();
		}));
		TableHandle<List<String>> names = registry.register(NAMES);
		registry.start();
		runQueue();
		assertTrue(((TableState.Unavailable) deep.state()).getReason().contains("StackOverflowError"));
		assertEquals(List.of("Alpha"), names.value(), "the next table still loads");
	}

	@Test
	void aListenerIsCalledOnceWhetherAddedBeforeOrAfterLoading() {
		names("[{\"id\":\"a\",\"name\":\"Alpha\"}]", 1);
		DataRegistry registry = registry();
		TableHandle<List<String>> handle = registry.register(NAMES);
		List<String> calls = new ArrayList<>();
		handle.onLoaded(value -> calls.add("before"));
		registry.start();
		runQueue();
		handle.onLoaded(value -> calls.add("after"));
		assertEquals(List.of("before", "after"), calls);
	}

	@Test
	void theTableContextExposesTheEnvelope() {
		names("[]", 0);
		List<JsonObject> sources = new ArrayList<>();
		DataRegistry registry = registry();
		TableHandle<String> handle = registry.register(new TableSpec<>("test_names", 1, table -> {
			sources.add(table.getBody().getAsJsonObject("sources"));
			return table.getEntry().table() + " " + table.getBody().get("license").getAsString();
		}));
		registry.start();
		runQueue();
		assertEquals("test_names CC0-1.0", handle.value());
		assertTrue(sources.get(0).has("own"));
	}
}
