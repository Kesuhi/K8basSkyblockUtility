package com.k8bas.skyblockutility.config.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The one save path for a file the mod persists (REQ-CFG-04, -05, -10, -12). A caller hands in the
 * complete new content, serialised on its own thread so it is a consistent snapshot; a single writer
 * thread then puts it on disk at most {@link #DEFAULT_DELAY_MS} later. Requests made while a write is
 * pending are merged (the newest content wins) and only one write is ever in progress. Each write
 * goes to a temporary file in the same folder and is then moved over the target in one step, so a
 * crash leaves either the old or the new complete file. A locked target (antivirus, indexer) is
 * retried before the write counts as failed.
 */
public final class AtomicFileStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/store");
	static final long DEFAULT_DELAY_MS = 500;
	static final int MOVE_ATTEMPTS = 3;
	static final long RETRY_PAUSE_MS = 150;

	/** Moves the temporary file over the target; replaceable in tests. */
	public interface Mover {
		void move(Path source, Path target) throws IOException;
	}

	private final Path file;
	private final Path temp;
	private final long delayMs;
	private final Mover mover;
	private final Consumer<IOException> onFailure;
	private final ScheduledExecutorService writer;
	private final Object lock = new Object();
	private String pending;
	private boolean scheduled;
	private boolean failing;

	/** @param onFailure called once per run of failed writes (the next successful write resets it) */
	public AtomicFileStore(Path file, Consumer<IOException> onFailure) {
		this(file, DEFAULT_DELAY_MS, AtomicFileStore::atomicMove, onFailure);
	}

	AtomicFileStore(Path file, long delayMs, Mover mover, Consumer<IOException> onFailure) {
		this.file = file;
		this.temp = file.resolveSibling(file.getFileName() + ".tmp");
		this.delayMs = delayMs;
		this.mover = mover;
		this.onFailure = onFailure;
		this.writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "k8bas-save-" + file.getFileName());
			thread.setDaemon(true);
			return thread;
		});
	}

	public Path file() {
		return file;
	}

	/** Queues the content for writing; returns at once. */
	public void requestSave(String content) {
		synchronized (lock) {
			pending = content;
			if (!scheduled) {
				scheduled = true;
				writer.schedule(this::writePending, delayMs, TimeUnit.MILLISECONDS);
			}
		}
	}

	/** Writes any pending content now and waits for it, e.g. on client shutdown. */
	public void flush() {
		try {
			writer.submit(this::writePending).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			LOGGER.error("Saving {} failed", file.getFileName(), e.getCause());
		}
	}

	private void writePending() {
		String content;
		synchronized (lock) {
			content = pending;
			pending = null;
			scheduled = false;
		}
		if (content == null) {
			return;
		}
		try {
			write(content);
			failing = false;
		} catch (IOException e) {
			if (!failing) {
				failing = true;
				LOGGER.error("Could not save {}; the previous file is kept and the next change retries", file.getFileName(), e);
				onFailure.accept(e);
			}
		}
	}

	private void write(String content) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		// On disk before the move, so a power loss right after it leaves the new content, not an empty file.
		try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE)) {
			ByteBuffer bytes = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
			while (bytes.hasRemaining()) {
				channel.write(bytes);
			}
			channel.force(true);
		}
		try {
			moveWithRetries();
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	private void moveWithRetries() throws IOException {
		for (int attempt = 1; ; attempt++) {
			try {
				mover.move(temp, file);
				return;
			} catch (FileSystemException e) {
				if (attempt >= MOVE_ATTEMPTS) {
					throw e;
				}
				LOGGER.warn("{} is locked (attempt {} of {}), retrying", file.getFileName(), attempt, MOVE_ATTEMPTS);
				try {
					Thread.sleep(RETRY_PAUSE_MS * attempt);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	private static void atomicMove(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
