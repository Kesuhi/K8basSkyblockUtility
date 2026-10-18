package com.k8bas.skyblockutility.update.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Puts a staged update into the mods folder after the game has exited (REQ-UPD-17). The game cannot do it itself: on
 * Windows the running jar stays locked until its process ends. This class runs in its own JVM, started by the game's
 * shutdown hook, and uses only the JDK: no Minecraft, no Fabric, no network, and no nested class, so it is one class file
 * that the game copies out of its own jar into {@code .k8bas_update/helper/}.
 *
 * <p>Order, so that the folder never holds zero copies of the mod and never a partial {@code .jar}: wait (at most 120 s)
 * for the game's process to end; check the staged jar's SHA-256; move it in under its asset name (atomically, or through
 * a non-{@code .jar} temporary name when the staging folder is on another drive, EC-UPD-07); flush it to disk and check
 * the SHA-256 again; only then delete the old jar. Each move or delete is retried for at most 60 s; on a timeout the old
 * jar stays, and the next launch finishes or reports it (REQ-UPD-18). Every step goes to the log.
 *
 * <p>Arguments: {@code --parent-pid <pid> [--parent-start <epoch ms>] --staged <file> --dest <file> --old <file>
 * --sha256 <hex> --log <file>}, and for tests {@code --wait-ms <ms>}, {@code --retry-ms <ms>} and {@code --copy} (take
 * the other-drive path). The start time tells the game from a later process that got its PID. Exit codes: 0 applied,
 * 3 applied but the old jar stays, 1 not applied, 2 bad arguments.
 */
public final class UpdateHelper {
	public static final int APPLIED = 0;
	public static final int NOT_APPLIED = 1;
	public static final int BAD_ARGUMENTS = 2;
	public static final int OLD_JAR_STAYS = 3;
	/** REQ-UPD-17: the longest wait for the game to exit. */
	public static final long MAX_WAIT_MS = 120_000;
	/** REQ-UPD-17: the longest a move or a delete is retried. */
	public static final long MAX_RETRY_MS = 60_000;
	/** The other-drive copy's name until it is complete: not a {@code .jar}, so Fabric never loads it. */
	public static final String TEMPORARY_SUFFIX = ".k8bas-part";
	private static final long POLL_MS = 100;

	private UpdateHelper() {
	}

	public static void main(String[] args) {
		System.exit(run(args));
	}

	/** The whole run; returns the exit code. */
	static int run(String[] args) {
		return run(args, null);
	}

	/** @param afterMove for tests: runs once the new jar is in, before it is checked again */
	static int run(String[] args, Runnable afterMove) {
		long parentPid = -1;
		long parentStart = -1;
		Path staged = null;
		Path dest = null;
		Path old = null;
		String sha256 = null;
		Path log = null;
		long waitMs = MAX_WAIT_MS;
		long retryMs = MAX_RETRY_MS;
		boolean copy = false;
		try {
			for (int i = 0; i < args.length; i++) {
				switch (args[i]) {
					case "--parent-pid" -> parentPid = Long.parseLong(args[++i]);
					case "--parent-start" -> parentStart = Long.parseLong(args[++i]);
					case "--staged" -> staged = Path.of(args[++i]).toAbsolutePath().normalize();
					case "--dest" -> dest = Path.of(args[++i]).toAbsolutePath().normalize();
					case "--old" -> old = Path.of(args[++i]).toAbsolutePath().normalize();
					case "--sha256" -> sha256 = args[++i].toLowerCase(Locale.ROOT);
					case "--log" -> log = Path.of(args[++i]).toAbsolutePath().normalize();
					case "--wait-ms" -> waitMs = Long.parseLong(args[++i]);
					case "--retry-ms" -> retryMs = Long.parseLong(args[++i]);
					case "--copy" -> copy = true;
					default -> {
						return BAD_ARGUMENTS;
					}
				}
			}
		} catch (RuntimeException e) {
			return BAD_ARGUMENTS;
		}
		if (staged == null || dest == null || old == null || sha256 == null || log == null || !sha256.matches("[0-9a-f]{64}")) {
			return BAD_ARGUMENTS;
		}
		log(log, "helper started: parent " + parentPid + ", staged " + staged + ", dest " + dest + ", old " + old
				+ ", waits at most " + waitMs + " ms, retries at most " + retryMs + " ms");
		String refusal = refusal(staged, dest, old);
		if (refusal != null) {
			log(log, "refused: " + refusal);
			return NOT_APPLIED;
		}
		if (!waitForParent(parentPid, parentStart, waitMs, log)) {
			return NOT_APPLIED;
		}
		if (!sha256.equals(sha256(staged))) {
			log(log, "the staged jar's SHA-256 is not " + sha256 + "; nothing changed");
			return NOT_APPLIED;
		}
		if (Files.exists(dest)) {
			if (sha256.equals(sha256(dest))) {
				log(log, "the new jar is already in place");
			} else {
				log(log, "another file already has the name " + dest.getFileName() + "; nothing changed");
				return NOT_APPLIED;
			}
		} else if (!moveIn(staged, dest, copy, retryMs, log)) {
			return NOT_APPLIED;
		}
		if (afterMove != null) {
			afterMove.run();
		}
		// The old jar goes only after the new one's bytes are on disk, so a power cut cannot leave a jar of zeros alone.
		flush(dest, log);
		if (!sha256.equals(sha256(dest))) {
			log(log, "the moved jar's SHA-256 is not " + sha256 + "; the old jar stays");
			if (delete(dest, retryMs, log)) {
				log(log, "removed the moved jar again");
			} else {
				log(log, "the moved jar could not be removed; the next launch reports it");
			}
			return NOT_APPLIED;
		}
		log(log, "moved in: " + dest);
		try {
			if (Files.isSameFile(dest, old)) {
				log(log, "the new and the old jar are one file; nothing is deleted");
				return APPLIED;
			}
		} catch (IOException e) {
			// the old jar is already gone, or unreadable: the delete below says which
		}
		if (!delete(old, retryMs, log)) {
			log(log, "the old jar stays; the next launch deletes it");
			return OLD_JAR_STAYS;
		}
		log(log, "deleted the old jar: " + old);
		return APPLIED;
	}

	/** Why the paths may not be used, or null: dest and old share a folder, dest is a new .jar name, staged exists. */
	static String refusal(Path staged, Path dest, Path old) {
		if (dest.getParent() == null || !dest.getParent().equals(old.getParent())) {
			return "the new jar would not go into the old jar's folder";
		}
		String name = dest.getFileName().toString();
		if (!name.endsWith(".jar") || name.startsWith(".")) {
			return "the new jar's name " + name + " is not a jar name";
		}
		// NTFS compares names without case.
		if (name.equalsIgnoreCase(old.getFileName().toString())) {
			return "the new jar would replace the running jar in place";
		}
		if (!Files.isRegularFile(staged)) {
			return "the staged jar is missing";
		}
		return null;
	}

	private static boolean waitForParent(long pid, long start, long waitMs, Path log) {
		Optional<ProcessHandle> parent = pid < 0 ? Optional.empty() : ProcessHandle.of(pid);
		// A process that started at another time only reuses the game's PID; the game has ended.
		if (parent.isPresent() && start >= 0) {
			Optional<Instant> started = parent.get().info().startInstant();
			if (started.isPresent() && Math.abs(started.get().toEpochMilli() - start) > 1_000) {
				parent = Optional.empty();
			}
		}
		if (parent.isEmpty()) {
			log(log, "the game's process has already ended");
			return true;
		}
		long begin = System.nanoTime();
		try {
			parent.get().onExit().get(waitMs, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			log(log, "the game's process did not end within " + waitMs + " ms; nothing changed");
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log(log, "interrupted while waiting; nothing changed");
			return false;
		} catch (java.util.concurrent.ExecutionException e) {
			log(log, "waiting failed: " + e.getCause() + "; nothing changed");
			return false;
		}
		log(log, "the game's process ended after " + (System.nanoTime() - begin) / 1_000_000 + " ms");
		return true;
	}

	/** Moves {@code staged} to {@code dest}, never leaving a partial file under a .jar name. */
	private static boolean moveIn(Path staged, Path dest, boolean copy, long retryMs, Path log) {
		Path temporary = dest.resolveSibling(dest.getFileName() + TEMPORARY_SUFFIX);
		long deadline = System.nanoTime() + retryMs * 1_000_000;
		for (int attempt = 0; ; attempt++) {
			try {
				if (!copy) {
					try {
						Files.move(staged, dest, StandardCopyOption.ATOMIC_MOVE);
						log(log, "moved the staged jar on attempt " + attempt);
						return true;
					} catch (AtomicMoveNotSupportedException otherDrive) {
						copy = true;
					}
				}
				log(log, "copying the staged jar through " + temporary.getFileName() + " (attempt " + attempt + ")");
				Files.copy(staged, temporary, StandardCopyOption.REPLACE_EXISTING);
				flush(temporary, log);
				Files.move(temporary, dest, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				deleteQuietly(temporary);
				if (System.nanoTime() >= deadline) {
					log(log, "could not move the new jar in within " + retryMs + " ms (" + e + "); the old jar stays");
					return false;
				}
				pause();
				continue;
			}
			log(log, "copied the staged jar on attempt " + attempt);
			try {
				Files.deleteIfExists(staged);
			} catch (IOException e) {
				log(log, "the staged copy remains (" + e + "); the next launch clears it");
			}
			return true;
		}
	}

	/** Forces a file's bytes to the disk. */
	private static void flush(Path file, Path log) {
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
			channel.force(true);
		} catch (IOException e) {
			log(log, "could not flush " + file.getFileName() + " to disk: " + e);
		}
	}

	private static boolean delete(Path file, long retryMs, Path log) {
		long deadline = System.nanoTime() + retryMs * 1_000_000;
		for (int attempt = 0; ; attempt++) {
			try {
				Files.deleteIfExists(file);
				if (attempt > 0) {
					log(log, "deleted " + file.getFileName() + " on attempt " + attempt);
				}
				return true;
			} catch (IOException e) {
				if (System.nanoTime() >= deadline) {
					log(log, "could not delete " + file + " within " + retryMs + " ms: " + e);
					return false;
				}
				pause();
			}
		}
	}

	/** The file's SHA-256 as lowercase hex, or null if it cannot be read. */
	public static String sha256(Path file) {
		try (InputStream input = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = input.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (IOException | NoSuchAlgorithmException e) {
			return null;
		}
	}

	private static void pause() {
		try {
			Thread.sleep(POLL_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
			// the next launch clears it (REQ-UPD-18)
		}
	}

	/** Appends one timestamped line; a log that cannot be written does not stop the swap. */
	static void log(Path log, String line) {
		try {
			Files.createDirectories(log.getParent());
			try (Writer out = Files.newBufferedWriter(log, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
				out.write(Instant.now() + " " + line + System.lineSeparator());
			}
		} catch (IOException ignored) {
			// nothing else to tell
		}
	}
}
