package com.k8bas.skyblockutility.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.migration.Migrator;
import com.k8bas.skyblockutility.config.store.AtomicFileStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Loads and saves the updater state file through the mod's one save path (AtomicFileStore) with
 * its own version field. The state is only a cache: a corrupt or newer file is reset with one
 * warning, never a crash (REQ-UPD-19).
 */
final class UpdateStateStore {
	static final String FILE_NAME = "k8bas_skyblock_utility-update-state.json";
	/** No steps yet; the next change to the state file's shape adds step 1. */
	static final Migrator MIGRATOR = new Migrator("stateVersion", List.of());
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final AtomicFileStore store;
	private final Consumer<String> warn;

	UpdateStateStore(Path file, Consumer<String> warn) {
		this.file = file;
		this.warn = warn;
		this.store = new AtomicFileStore(file, e -> warn.accept("Couldn't save the update state (" + e.getMessage() + ")"));
	}

	UpdateState load() {
		if (!Files.exists(file)) {
			return new UpdateState();
		}
		try {
			JsonElement tree = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!(tree instanceof JsonObject object)) {
				throw new IllegalStateException("not a JSON object");
			}
			if (MIGRATOR.migrate(object).newerThanKnown()) {
				warn.accept(FILE_NAME + " is from a newer version of the mod; it was reset");
				return new UpdateState();
			}
			UpdateState state = GSON.fromJson(object, UpdateState.class);
			state.normalize();
			return state;
		} catch (IOException | RuntimeException e) {
			warn.accept(FILE_NAME + " could not be read (" + e.getClass().getSimpleName() + "); it was reset");
			return new UpdateState();
		}
	}

	void save(UpdateState state) {
		store.requestSave(GSON.toJson(state));
	}

	void flush() {
		store.flush();
	}
}
