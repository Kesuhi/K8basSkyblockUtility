package com.k8bas.skyblockutility.update;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Downloads a confirmed jar into the staging folder and checks it (REQ-UPD-13 to REQ-UPD-15): it streams into
 * {@code .k8bas_update/staged/<asset name>.part}, which Fabric never scans, and only a file whose SHA-256 and
 * fabric.mod.json both pass is renamed to its asset name. Any failure deletes the {@code .part}; {@code mods/} is never
 * touched here (T4.3 swaps the jar in after the game exits).
 */
final class JarStager {
	private final Path staged;
	private final SecureDownloader downloader;
	private final String minecraft;
	private final String loader;

	/**
	 * @param root      {@code .k8bas_update} in the game directory
	 * @param minecraft the running Minecraft version
	 * @param loader    the running Fabric Loader version
	 */
	JarStager(Path root, SecureDownloader downloader, String minecraft, String loader) {
		this.staged = root.resolve("staged");
		this.downloader = downloader;
		this.minecraft = minecraft;
		this.loader = loader;
	}

	/** The folder the checked jars wait in. */
	Path stagedFolder() {
		return staged;
	}

	/**
	 * Fetches {@code jar}, then checks it against {@code sha256} and its fabric.mod.json against the running game.
	 *
	 * @param version the candidate's version, for the outcome's text
	 * @param sha256  the expected SHA-256, 64 lowercase hex digits
	 */
	DownloadOutcome stage(String version, Release.Asset jar, String sha256) {
		String name = jar.name();
		// The name pattern allows only letters, digits and ".+-", so neither file can leave the staging folder.
		String declared = AssetSelector.declaredVersion(name);
		if (declared == null) {
			return new DownloadOutcome.Failed("the asset name " + name + " is not a jar of this mod");
		}
		Path part = staged.resolve(name + ".part");
		Path done = staged.resolve(name);
		try {
			Files.createDirectories(staged);
			SecureDownloader.Result result;
			try (OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
				result = downloader.fetch(jar.downloadUrl(), jar.size(), SecureDownloader.MAX_JAR_BYTES, out);
			}
			if (result instanceof SecureDownloader.Failed failed) {
				return new DownloadOutcome.Failed(failed.reason());
			}
			SecureDownloader.Done fetched = (SecureDownloader.Done) result;
			if (!MessageDigest.isEqual(HexFormat.of().parseHex(fetched.sha256()), HexFormat.of().parseHex(sha256))) {
				return DownloadOutcome.ChecksumMismatch.INSTANCE;
			}
			String problem = JarValidator.problem(part, declared, minecraft, loader);
			if (problem != null) {
				return new DownloadOutcome.Incompatible(problem);
			}
			Files.move(part, done, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			return new DownloadOutcome.Staged(version, done, fetched.sha256());
		} catch (IOException | IllegalArgumentException e) {
			return new DownloadOutcome.Failed("staging: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
		} finally {
			deleteQuietly(part);
		}
	}

	/** Deletes the {@code .part} files a closed or crashed game left (EC-UPD-03); called once at launch. */
	void cleanParts() {
		if (!Files.isDirectory(staged)) {
			return;
		}
		try (DirectoryStream<Path> parts = Files.newDirectoryStream(staged, "*.part")) {
			for (Path part : parts) {
				deleteQuietly(part);
			}
		} catch (IOException ignored) {
			// tried again at the next launch
		}
	}

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
			// cleanParts tries again at the next launch
		}
	}
}
