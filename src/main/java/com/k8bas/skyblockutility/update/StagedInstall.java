package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.update.helper.UpdateHelper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Arms a checked jar for install when the game exits (REQ-UPD-17). The game cannot replace its own jar while it runs (on
 * Windows the file stays locked), so a shutdown hook starts {@link UpdateHelper} in its own JVM, which waits for this
 * process to end and then swaps the jars. Arming copies the helper's one class file out of the mod into
 * {@code .k8bas_update/helper/}, so no jar in {@code mods/} is held open and the helper always matches the code that calls
 * it. Nothing is written into {@code mods/} while the game runs.
 *
 * <p>The hook runs on a normal quit and on a crash exit, not on a hard kill; the next launch reconciles that (T4.3b). It
 * does no network work, re-checks the staged jar's SHA-256, leaves a marker that the helper was started, and does not wait
 * for it.
 */
final class StagedInstall {
	/** Written by the hook just before the helper starts, so the next launch can tell a failed apply from a hard kill. */
	static final String ATTEMPT_MARKER = "apply-attempt";
	private static final String HELPER_CLASS = "com/k8bas/skyblockutility/update/helper/UpdateHelper.class";
	private static final String HELPER_FOLDER = "helper";

	/** Starts the helper in {@code directory}; a {@link ProcessBuilder} with its output discarded in the game. */
	interface Launcher {
		void start(List<String> command, Path directory) throws IOException;
	}

	/**
	 * Where an armed jar goes, or why it may not.
	 *
	 * @param dest    the new jar's place: the running jar's folder, under the asset name
	 * @param old     the running jar, deleted by the helper once the new one is in
	 * @param refusal null when armed
	 */
	record Armed(Path dest, Path old, String refusal) {
	}

	record Plan(Path staged, Path dest, Path old, String sha256) {
	}

	private final Path root;
	private final Supplier<Path> runningJar;
	private final Launcher launcher;
	private final Consumer<Thread> hooks;
	private final Consumer<String> warn;
	private final AtomicBoolean hooked = new AtomicBoolean();
	private volatile Plan armed;

	/**
	 * @param root       {@code .k8bas_update} in the game directory
	 * @param runningJar the jar the mod runs from (InstallEligibility's), null when unknown
	 * @param hooks      registers the shutdown hook; {@code Runtime.getRuntime()::addShutdownHook} in the game
	 */
	StagedInstall(Path root, Supplier<Path> runningJar, Launcher launcher, Consumer<Thread> hooks, Consumer<String> warn) {
		this.root = root;
		this.runningJar = runningJar;
		this.launcher = launcher;
		this.hooks = hooks;
		this.warn = warn;
	}

	/** The game's launcher: the JVM's own java, windowless on Windows. */
	static Launcher processLauncher() {
		return (command, directory) -> new ProcessBuilder(command)
				.directory(directory.toFile())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
	}

	/**
	 * Why the new jar may not be put beside {@code runningJar} under {@code assetName}, or null (AC-UPD-19): only a jar name
	 * of this mod, which cannot hold a separator, a drive or "..", so it stays in the running jar's folder; and never the
	 * running jar's own name, compared without case as NTFS does.
	 */
	static String refusal(String assetName, Path runningJar) {
		if (runningJar == null || runningJar.getParent() == null) {
			return "the running jar is not known";
		}
		if (AssetSelector.declaredVersion(assetName) == null) {
			return "the asset name " + assetName + " is not a jar of this mod";
		}
		if (assetName.toLowerCase(Locale.ROOT).equals(runningJar.getFileName().toString().toLowerCase(Locale.ROOT))) {
			return "the new jar would replace the running jar";
		}
		return null;
	}

	/**
	 * Arms {@code staged} to replace the running jar at exit. A second arm replaces the first, whose staged jar is deleted.
	 *
	 * @param sha256 the staged jar's checked SHA-256
	 */
	Armed arm(Path staged, String assetName, String sha256) {
		Path old = runningJar.get();
		String refusal = refusal(assetName, old);
		if (refusal != null) {
			return new Armed(null, old, refusal);
		}
		Path dest = old.getParent().resolve(assetName);
		// Loaded now, so a class-loading problem shows while the game runs, not in the shutdown hook.
		String helperClass = UpdateHelper.class.getName();
		try (InputStream helper = StagedInstall.class.getClassLoader().getResourceAsStream(HELPER_CLASS)) {
			if (helper == null) {
				return new Armed(dest, old, "the helper is missing from the mod");
			}
			Path copy = helperFolder().resolve(helperClass.replace('.', '/') + ".class");
			Files.createDirectories(copy.getParent());
			Files.copy(helper, copy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			return new Armed(dest, old, "the helper could not be copied: " + e.getClass().getSimpleName());
		}
		Plan previous = armed;
		armed = new Plan(staged, dest, old, sha256);
		if (previous != null && !previous.staged().equals(staged)) {
			deleteQuietly(previous.staged());
		}
		if (hooked.compareAndSet(false, true)) {
			hooks.accept(new Thread(this::onExit, "k8bas-update-apply"));
		}
		return new Armed(dest, old, null);
	}

	/** The armed jar's checked file, or null. */
	Path armedFile() {
		Plan plan = armed;
		return plan == null ? null : plan.staged();
	}

	/** Nothing starts at exit (REQ-UPD-12's cancel); the staged files are the caller's to delete. */
	void disarm() {
		armed = null;
	}

	/** Deletes every staged jar and partial download. */
	void deleteStaged() {
		Path staged = root.resolve("staged");
		if (!Files.isDirectory(staged)) {
			return;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(staged)) {
			for (Path file : files) {
				deleteQuietly(file);
			}
		} catch (IOException e) {
			warn.accept("Update: the staged files could not all be deleted: " + e.getClass().getSimpleName());
		}
	}

	/** The shutdown hook: re-check, mark, start the helper. Never throws and never waits. */
	private void onExit() {
		Plan plan = armed;
		if (plan == null) {
			return;
		}
		try {
			if (!plan.sha256().equals(UpdateHelper.sha256(plan.staged()))) {
				warn.accept("Update: the staged " + plan.staged().getFileName() + " changed after it was checked; it is not installed");
				return;
			}
			Files.writeString(root.resolve(ATTEMPT_MARKER), ProcessHandle.current().pid() + " " + Instant.now() + "\n", StandardCharsets.UTF_8);
			launcher.start(command(plan), root);
		} catch (IOException | RuntimeException e) {
			warn.accept("Update: the installer could not be started: " + e);
		}
	}

	/**
	 * The helper's command line. The class path is relative to {@code .k8bas_update}, where the helper starts, so a ';' or
	 * ':' in the game folder's path cannot split it; the file paths are separate arguments, which a path cannot break.
	 */
	List<String> command(Plan plan) {
		return List.of(javaLauncher(), "-Xmx32m", "-cp", HELPER_FOLDER, UpdateHelper.class.getName(),
				"--parent-pid", Long.toString(ProcessHandle.current().pid()),
				"--parent-start", Long.toString(ProcessHandle.current().info().startInstant().map(Instant::toEpochMilli).orElse(-1L)),
				"--staged", plan.staged().toString(), "--dest", plan.dest().toString(), "--old", plan.old().toString(),
				"--sha256", plan.sha256(), "--log", root.resolve("logs").resolve("helper.log").toString());
	}

	private Path helperFolder() {
		return root.resolve(HELPER_FOLDER);
	}

	/** The running JVM's java: {@code javaw.exe} on Windows, so no console window opens; else {@code java}. */
	static String javaLauncher() {
		Path bin = Path.of(System.getProperty("java.home"), "bin");
		Path windowless = bin.resolve("javaw.exe");
		if (Files.isRegularFile(windowless)) {
			return windowless.toString();
		}
		Path java = bin.resolve(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") ? "java.exe" : "java");
		if (Files.isRegularFile(java)) {
			return java.toString();
		}
		return ProcessHandle.current().info().command().orElse("java");
	}

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
			// the next launch clears it (T4.3b)
		}
	}
}
