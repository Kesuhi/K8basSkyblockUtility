package com.k8bas.skyblockutility.config.store;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFileStoreTest {
	@TempDir
	Path dir;

	private final List<IOException> failures = new ArrayList<>();

	private static void plainMove(Path source, Path target) throws IOException {
		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private AtomicFileStore store(Path file, AtomicFileStore.Mover mover) {
		return new AtomicFileStore(file, 20, mover, failures::add);
	}

	/** AC-CFG-03, for the config file and (AC-CFG-11) for a second file type. */
	@Test
	void anInterruptedSaveLeavesThePreviousCompleteFile() throws IOException {
		for (String name : List.of("k8bas_skyblock_utility.json", "profile-state.json")) {
			Path file = dir.resolve(name);
			Files.writeString(file, "{\"version\":1}");
			AtomicFileStore store = store(file, (source, target) -> {
				assertTrue(Files.exists(source), "the temporary file is complete before the move");
				throw new IOException("killed before the move");
			});
			store.requestSave("{\"version\":2}");
			store.flush();

			assertEquals("{\"version\":1}", Files.readString(file), name);
			assertEquals(1, JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("version").getAsInt());
			assertFalse(Files.exists(dir.resolve(name + ".tmp")), "temporary file removed");
		}
	}

	/** AC-CFG-04, for the config file and (AC-CFG-11) for a second file type. */
	@Test
	void burstsAreMergedAndOnlyOneWriteRunsAtATime() throws Exception {
		for (String name : List.of("k8bas_skyblock_utility.json", "profile-state.json")) {
			Path file = dir.resolve(name);
			AtomicInteger active = new AtomicInteger();
			AtomicInteger maxActive = new AtomicInteger();
			AtomicInteger writes = new AtomicInteger();
			AtomicFileStore store = store(file, (source, target) -> {
				maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
				writes.incrementAndGet();
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				plainMove(source, target);
				active.decrementAndGet();
			});

			Thread other = new Thread(() -> store.requestSave("{\"from\":\"other thread\"}"));
			other.start();
			other.join();
			for (int i = 1; i <= 100; i++) {
				store.requestSave("{\"n\":" + i + "}");
				Thread.sleep(i % 10 == 0 ? 10 : 0);
			}
			store.flush();

			assertEquals(1, maxActive.get(), name);
			assertTrue(writes.get() < 101, "requests merged: " + writes.get() + " writes");
			assertEquals("{\"n\":100}", Files.readString(file), name);
		}
	}

	/** AC-CFG-09 [A]: a single change is on disk well within 2 s, without a flush. */
	@Test
	void aChangeReachesTheDiskWithinTwoSeconds() throws Exception {
		Path file = dir.resolve("k8bas_skyblock_utility.json");
		AtomicFileStore store = new AtomicFileStore(file, failures::add);
		long start = System.nanoTime();
		store.requestSave("{\"enabled\":false}");
		while (!Files.exists(file) && System.nanoTime() - start < 2_000_000_000L) {
			Thread.sleep(10);
		}
		assertEquals("{\"enabled\":false}", Files.readString(file));
		assertTrue(AtomicFileStore.DEFAULT_DELAY_MS <= 2000);
	}

	/** EC-CFG-04: a briefly locked target is retried (at least 3 attempts within 1 s). */
	@Test
	void aLockedTargetIsRetried() throws IOException {
		Path file = dir.resolve("k8bas_skyblock_utility.json");
		AtomicInteger attempts = new AtomicInteger();
		AtomicFileStore store = store(file, (source, target) -> {
			if (attempts.incrementAndGet() < 3) {
				throw new AccessDeniedException(target.toString());
			}
			plainMove(source, target);
		});
		long start = System.nanoTime();
		store.requestSave("{\"ok\":true}");
		store.flush();

		assertEquals(3, attempts.get());
		assertTrue(System.nanoTime() - start < 1_000_000_000L, "retries finish within 1 s");
		assertEquals("{\"ok\":true}", Files.readString(file));
		assertTrue(failures.isEmpty());
	}

	/** EC-CFG-03: a failing disk keeps the previous file, is reported once, and the next change retries. */
	@Test
	void failuresAreReportedOnceAndTheNextChangeRetries() throws IOException {
		Path file = dir.resolve("k8bas_skyblock_utility.json");
		Files.writeString(file, "old");
		boolean[] diskFull = {true};
		AtomicFileStore store = store(file, (source, target) -> {
			if (diskFull[0]) {
				throw new IOException("No space left on device");
			}
			plainMove(source, target);
		});
		store.requestSave("new 1");
		store.flush();
		store.requestSave("new 2");
		store.flush();
		assertEquals("old", Files.readString(file));
		assertEquals(1, failures.size(), "reported once per run of failures");

		diskFull[0] = false;
		store.requestSave("new 3");
		store.flush();
		assertEquals("new 3", Files.readString(file));
	}

	/** EC-CFG-06: a temporary file left by a crash does not disturb saving and is gone afterwards. */
	@Test
	void aLeftoverTemporaryFileIsReplacedAndRemoved() throws IOException {
		Path file = dir.resolve("k8bas_skyblock_utility.json");
		Files.writeString(dir.resolve("k8bas_skyblock_utility.json.tmp"), "{half a fi");
		AtomicFileStore store = store(file, AtomicFileStoreTest::plainMove);
		store.requestSave("{\"complete\":true}");
		store.flush();
		assertEquals("{\"complete\":true}", Files.readString(file));
		assertFalse(Files.exists(dir.resolve("k8bas_skyblock_utility.json.tmp")));
	}
}
