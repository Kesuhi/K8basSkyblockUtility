package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-profile store (T3.0d, REQ-GS-09, AC-GS-09): one folder per (account, profile), one file per
 * consumer, written through config-store's atomic facility (a save killed before the move keeps the previous
 * file: AtomicFileStoreTest.anInterruptedSaveLeavesThePreviousCompleteFile); nothing written while the
 * profile is unknown; a known name with a new profile id archives the old state.
 */
class ProfileStoreTest {
	private static final UUID ACCOUNT = new UUID(0L, 1L);

	@TempDir
	Path root;
	private final List<String> warnings = new ArrayList<>();

	private static Profile profile(String name, String id) {
		return new Profile(ACCOUNT, ProfileText.key(name), name, ProfileType.NORMAL, id);
	}

	private int files() throws IOException {
		try (Stream<Path> walk = Files.walk(root)) {
			return (int) walk.filter(Files::isRegularFile).count();
		}
	}

	@Test
	void nothingIsWrittenWhileTheProfileIsUnknown() throws IOException {
		ProfileStore store = new ProfileStore(root, warnings::add);
		store.write("odds", "{\"kills\": 1}");
		store.flush();
		assertEquals(0, files());
		assertNull(store.read("odds"));
	}

	@Test
	void eachProfileHasItsOwnFolderAndFiles() throws IOException {
		ProfileStore store = new ProfileStore(root, warnings::add);
		store.use(profile("Raspberry", "id-a"));
		store.write("odds", "{\"kills\": 1}");
		store.write("bestiary", "{\"zombie\": 5}");
		store.flush();
		Path folder = root.resolve(ACCOUNT.toString()).resolve("raspberry");
		assertEquals("{\"kills\": 1}", Files.readString(folder.resolve("odds.json")));
		assertEquals("{\"zombie\": 5}", Files.readString(folder.resolve("bestiary.json")));
		assertEquals("{\"kills\": 1}", store.read("odds"));
	}

	@Test
	void aSwitchAwayAndBackRestoresTheState() {
		// AC-GS-09: A -> B -> A restores A's state.
		ProfileStore store = new ProfileStore(root, warnings::add);
		store.use(profile("Raspberry", "id-a"));
		store.write("odds", "{\"kills\": 1}");
		store.use(profile("Strawberry", "id-b"));
		assertNull(store.read("odds"));
		store.write("odds", "{\"kills\": 9}");
		store.use(profile("Raspberry", "id-a"));
		assertEquals("{\"kills\": 1}", store.read("odds"));
	}

	@Test
	void aNameWithANewProfileIdArchivesTheOldState() throws IOException {
		// AC-GS-09: name `strawberry` with a new profile id: the old state is archived, never merged.
		ProfileStore store = new ProfileStore(root, warnings::add);
		store.use(profile("Strawberry", "id-old"));
		store.write("odds", "{\"kills\": 3}");
		store.use(profile("Strawberry", "id-new"));
		assertNull(store.read("odds"), "the new profile starts empty");
		Path archive = root.resolve(ACCOUNT.toString()).resolve("archive");
		try (Stream<Path> archived = Files.list(archive)) {
			Path old = archived.findFirst().orElseThrow();
			assertTrue(old.getFileName().toString().startsWith("strawberry-"), old.toString());
			assertEquals("{\"kills\": 3}", Files.readString(old.resolve("odds.json")));
		}
		store.flush();
		assertTrue(Files.readString(root.resolve(ACCOUNT.toString()).resolve("strawberry").resolve(ProfileStore.META)).contains("id-new"));
	}

	@Test
	void anIdSeenLaterDoesNotArchive() {
		// The id often arrives after the name: no id yet, then the first id, is the same profile.
		ProfileStore store = new ProfileStore(root, warnings::add);
		store.use(profile("Raspberry", null));
		store.write("odds", "{\"kills\": 2}");
		store.use(profile("Raspberry", "id-a"));
		assertEquals("{\"kills\": 2}", store.read("odds"));
		assertFalse(Files.exists(root.resolve(ACCOUNT.toString()).resolve("archive")));
	}

	@Test
	void aRecreatedProfileSeenBeforeItsIdTouchesNothing() throws IOException {
		// Strawberry (id-old) was deleted and recreated; the new one is seen by name before its id arrives.
		ProfileStore store = new ProfileStore(root, warnings::add);
		store.use(profile("Strawberry", "id-old"));
		store.write("odds", "{\"kills\": 3}");
		store.use(profile("Strawberry", null));
		assertNull(store.read("odds"), "not knowing which profile it is, it reads nothing");
		store.write("odds", "{\"kills\": 99}");
		store.use(profile("Strawberry", "id-new"));
		assertNull(store.read("odds"), "the new profile starts empty");
		Path archive = root.resolve(ACCOUNT.toString()).resolve("archive");
		try (Stream<Path> archived = Files.list(archive)) {
			assertEquals("{\"kills\": 3}", Files.readString(archived.findFirst().orElseThrow().resolve("odds.json")), "the old state is not merged");
		}
	}

	@Test
	void aFailedArchiveIsRetriedNotMerged() throws IOException {
		// The folder cannot be moved (e.g. a scanner holds a file): the new profile neither reads nor writes the
		// old state, and the next use tries again.
		boolean[] locked = {true};
		ProfileStore store = new ProfileStore(root, warnings::add, (from, to) -> {
			if (locked[0]) {
				throw new java.nio.file.AccessDeniedException(from.toString());
			}
			Files.move(from, to);
		});
		store.use(profile("Strawberry", "id-old"));
		store.write("odds", "{\"kills\": 3}");
		store.use(profile("Strawberry", "id-new"));
		assertNull(store.read("odds"));
		store.write("odds", "{\"kills\": 99}");
		store.flush();
		assertTrue(Files.readString(root.resolve(ACCOUNT.toString()).resolve("strawberry").resolve(ProfileStore.META)).contains("id-old"),
				"the old folder keeps its id");
		assertFalse(warnings.isEmpty(), "the failure is reported");
		locked[0] = false;
		store.use(profile("Strawberry", "id-new"));
		assertNull(store.read("odds"));
		try (Stream<Path> archived = Files.list(root.resolve(ACCOUNT.toString()).resolve("archive"))) {
			assertEquals("{\"kills\": 3}", Files.readString(archived.findFirst().orElseThrow().resolve("odds.json")));
		}
	}

	@Test
	void consumerNamesAreFileNamesOnly() {
		ProfileStore store = new ProfileStore(root, warnings::add);
		store.use(profile("Raspberry", "id-a"));
		assertThrows(IllegalArgumentException.class, () -> store.write("../odds", "{}"));
		assertThrows(IllegalArgumentException.class, () -> store.read("Odds"));
	}

	@Test
	void anOddProfileNameIsAFolderNameStill() {
		assertEquals("my_fruit", ProfileStore.folder("my/fruit"));
		assertEquals("_", ProfileStore.folder(".."));
	}
}
