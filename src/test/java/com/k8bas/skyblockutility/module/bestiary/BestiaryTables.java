package com.k8bas.skyblockutility.module.bestiary;

import com.k8bas.skyblockutility.data.DataRegistry;
import com.k8bas.skyblockutility.data.TableHandle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The bestiary table for the bestiary tests: the shipped one, read as the game reads it, or a small fixture table. */
final class BestiaryTables {
	private BestiaryTables() {
	}

	private static byte[] resource(String path) {
		try (InputStream in = BestiaryTables.class.getResourceAsStream("/assets/k8bas_skyblock_utility/data/" + path)) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The shipped table, through the registry; it must load without a warning. */
	static BestiaryFamilies shipped() {
		List<String> warnings = new ArrayList<>();
		DataRegistry registry = new DataRegistry(BestiaryTables::resource, Runnable::run, Runnable::run, warnings::add);
		TableHandle<BestiaryFamilies> handle = registry.register(BestiaryTable.SPEC);
		registry.start();
		assertEquals(List.of(), warnings, "the table loads without a warning");
		assertNotNull(handle.value(), "loaded");
		return handle.value();
	}

	/** A one-file registry holding {@code table} as the bestiary table, with its index entry; warnings go to {@code warnings}. */
	static BestiaryFamilies fixture(String table, List<String> warnings) {
		byte[] bytes = table.getBytes(StandardCharsets.UTF_8);
		String index = "{\"indexVersion\": 1, \"tables\": {\"bestiary\": {\"path\": \"bestiary.json\", \"sha256\": \"" + sha256(bytes)
				+ "\", \"rows\": 1, \"schemaVersion\": 1, \"dataVersion\": 1, \"gameVersion\": \"0.27.1\"}}}";
		Map<String, byte[]> files = Map.of("index.json", index.getBytes(StandardCharsets.UTF_8), "bestiary.json", bytes);
		DataRegistry registry = new DataRegistry(files::get, Runnable::run, Runnable::run, warnings::add);
		TableHandle<BestiaryFamilies> handle = registry.register(BestiaryTable.SPEC);
		registry.start();
		assertNotNull(handle.value(), "loaded: " + warnings);
		return handle.value();
	}

	/** A table with the bracket {@code x} (10, 20, 30, 40), the category {@code hub} and the given rows. */
	static String table(String rows) {
		return table("\"hub\": {\"name\": \"Hub\"}", rows);
	}

	/** A table with the bracket {@code x} (10, 20, 30, 40), the given categories (JSON members) and rows. */
	static String table(String categories, String rows) {
		return "{\"table\": \"bestiary\", \"schemaVersion\": 1, \"dataVersion\": 1, \"gameVersion\": \"0.27.1\", \"generatedAt\": \"2026-10-18T18:00:00Z\","
				+ " \"license\": \"MIT\", \"sources\": {}, \"brackets\": {\"x\": [10, 20, 30, 40]}, \"categories\": {" + categories + "},"
				+ " \"rows\": [" + rows + "]}";
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
