package com.k8bas.skyblockutility.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `./gradlew check` runs the bundled-data validator on the mod's own tables (T3.0i, REQ-DATA-04): every
 * table valid against the envelope and its schema, consistent with the index; and the jar's data folder at
 * most 1 MB compressed (REQ-DATA-12, AC-DATA-10).
 */
class BundledDataValidationTest {
	private static final Path PROJECT = Path.of(System.getProperty("k8bas.projectDir", "."));
	private static final long MAX_COMPRESSED = 1024 * 1024;

	@Test
	void theBundledTablesAreValid() {
		DataValidator.Report report = DataValidator.validate(PROJECT.resolve(DataIndexer.DATA), PROJECT.resolve(DataIndexer.SCHEMAS));
		report.getBytes().forEach((table, bytes) -> System.out.println("bundled table " + table + ": " + bytes + " bytes"));
		assertEquals(List.of(), report.getProblems());
	}

	@Test
	void theJarsDataFolderIsAtMostOneMegabyteCompressed() throws IOException {
		String jar = System.getProperty("k8bas.releaseJar");
		long compressed = 0;
		int entries = 0;
		try (ZipFile zip = new ZipFile(jar)) {
			Enumeration<? extends ZipEntry> all = zip.entries();
			while (all.hasMoreElements()) {
				ZipEntry entry = all.nextElement();
				if (entry.getName().startsWith("assets/k8bas_skyblock_utility/data/") && !entry.isDirectory()) {
					compressed += entry.getCompressedSize();
					entries++;
				}
			}
		}
		System.out.println("jar data folder: " + entries + " file(s), " + compressed + " bytes compressed");
		assertTrue(entries >= 1, "the index ships in the jar");
		assertTrue(compressed <= MAX_COMPRESSED, "the data folder adds at most 1 MB: " + compressed);
	}
}
