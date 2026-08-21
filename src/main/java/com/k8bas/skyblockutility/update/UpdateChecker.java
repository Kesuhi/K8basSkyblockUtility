package com.k8bas.skyblockutility.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Checks this mod's own Modrinth project for a newer version matching the running Minecraft
 * version and loader, downloads it (verifying its hash) into mods/ under its own filename, and
 * schedules the currently-running jar for deletion via deleteOnExit() — a running jar is locked
 * on Windows, so a live hot-swap isn't possible; this is the realistic "downloads it for me,
 * tells me to restart" version of self-updating. Never touches or deletes the running jar while
 * the game is open; the delete only actually happens when the JVM exits, i.e. on restart.
 *
 * MODRINTH_PROJECT_SLUG is the only project this ever talks to, and api.modrinth.com/
 * cdn.modrinth.com are the only hosts it ever contacts — the download URL's host is checked
 * against Modrinth's own CDN before anything is fetched from it. Nothing downloaded is ever
 * executed; it's written to disk as a plain file and nothing more.
 */
public final class UpdateChecker {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/update");

	private static final String MODRINTH_PROJECT_SLUG = "k8bas-skyblock-utility";
	private static final String MODRINTH_API_HOST = "api.modrinth.com";
	private static final String MODRINTH_CDN_HOST = "cdn.modrinth.com";
	private static final String MINECRAFT_VERSION = "26.1.2";
	private static final String USER_AGENT =
			"Kesuhi/k8bas-skyblock-utility/" + currentVersionString() + " (https://github.com/Kesuhi/K8basSkyblockUtility)";

	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	/** Set once a download completes successfully; read by the settings GUI to show a persistent
	 *  "restart to apply" notice (the chat message is one-shot and easy to miss/scroll past). */
	private static volatile String pendingUpdateVersion = null;

	private UpdateChecker() {
	}

	public static String getPendingUpdateVersion() {
		return pendingUpdateVersion;
	}

	public static void checkInBackgroundIfEnabled() {
		if (!ConfigManager.general().autoUpdateCheckEnabled) {
			return;
		}
		Thread.ofVirtual().name("k8bas-update-check").start(UpdateChecker::checkNow);
	}

	public static void checkNowManually() {
		notifyUser("Checking for K8bas Skyblock Utility updates...");
		Thread.ofVirtual().name("k8bas-update-check-manual").start(() -> checkNow(true));
	}

	private static void checkNow() {
		checkNow(false);
	}

	private static void checkNow(boolean manuallyTriggered) {
		try {
			String url = "https://" + MODRINTH_API_HOST + "/v2/project/" + MODRINTH_PROJECT_SLUG + "/version"
					+ "?loaders=" + encode("[\"fabric\"]")
					+ "&game_versions=" + encode("[\"" + MINECRAFT_VERSION + "\"]");

			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", USER_AGENT)
					.GET()
					.build();

			HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				LOGGER.warn("Update check got HTTP {}", response.statusCode());
				if (manuallyTriggered) {
					notifyUser("Update check failed (HTTP " + response.statusCode() + ").");
				}
				return;
			}

			JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
			if (versions.isEmpty()) {
				LOGGER.info("No published versions match Minecraft {} / fabric.", MINECRAFT_VERSION);
				if (manuallyTriggered) {
					notifyUser("No K8bas Skyblock Utility release matches this Minecraft version yet.");
				}
				return;
			}

			Version currentVersion = parseVersion(currentVersionString());
			JsonObject newest = null;
			Version newestVersion = null;
			for (JsonElement element : versions) {
				JsonObject candidate = element.getAsJsonObject();
				Version candidateVersion = parseVersion(candidate.get("version_number").getAsString());
				if (newestVersion == null || candidateVersion.compareTo(newestVersion) > 0) {
					newest = candidate;
					newestVersion = candidateVersion;
				}
			}

			if (newestVersion.compareTo(currentVersion) <= 0) {
				LOGGER.info("Already up to date ({}).", currentVersion.getFriendlyString());
				if (manuallyTriggered) {
					notifyUser("K8bas Skyblock Utility is already up to date (" + currentVersion.getFriendlyString() + ").");
				}
				return;
			}

			String latestVersionNumber = newest.get("version_number").getAsString();
			LOGGER.info("Update available: {} -> {}", currentVersion.getFriendlyString(), latestVersionNumber);

			if (ConfigManager.general().autoUpdateDownloadEnabled) {
				downloadAndInstall(newest, latestVersionNumber);
			} else {
				notifyUser("A K8bas Skyblock Utility update (" + latestVersionNumber + ") is available.");
			}
		} catch (Exception e) {
			LOGGER.warn("Update check failed", e);
			if (manuallyTriggered) {
				notifyUser("Update check failed — see the log for details.");
			}
		}
	}

	/** Falls back to plain Version parsing (Fabric Loader's general, non-semver-strict parser)
	 *  for a version string that isn't strict semver, so a check never crashes on an odd tag —
	 *  it just compares as best it can instead of silently doing a naive string comparison. */
	private static Version parseVersion(String raw) {
		try {
			return SemanticVersion.parse(raw);
		} catch (VersionParsingException e) {
			try {
				return Version.parse(raw);
			} catch (VersionParsingException e2) {
				LOGGER.warn("Couldn't parse version '{}', treating as lowest possible", raw);
				return LOWEST_VERSION;
			}
		}
	}

	private static final Version LOWEST_VERSION = new Version() {
		@Override
		public String getFriendlyString() {
			return "0.0.0";
		}

		@Override
		public int compareTo(Version other) {
			return other == this ? 0 : -1;
		}
	};

	private static void downloadAndInstall(JsonObject latestVersion, String versionNumber)
			throws IOException, InterruptedException, NoSuchAlgorithmException {
		JsonArray files = latestVersion.getAsJsonArray("files");
		JsonObject primaryFile = null;
		for (JsonElement fileElement : files) {
			JsonObject file = fileElement.getAsJsonObject();
			if (file.get("primary").getAsBoolean()) {
				primaryFile = file;
				break;
			}
		}
		if (primaryFile == null && !files.isEmpty()) {
			primaryFile = files.get(0).getAsJsonObject();
		}
		if (primaryFile == null) {
			LOGGER.warn("Update version has no downloadable files");
			return;
		}

		String downloadUrl = primaryFile.get("url").getAsString();
		String downloadHost = URI.create(downloadUrl).getHost();
		if (downloadHost == null || !downloadHost.equalsIgnoreCase(MODRINTH_CDN_HOST)) {
			LOGGER.warn("Refusing to download update: file URL host '{}' isn't Modrinth's CDN", downloadHost);
			return;
		}

		String filename = primaryFile.get("filename").getAsString();
		String expectedSha1 = primaryFile.getAsJsonObject("hashes").get("sha1").getAsString();

		HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(downloadUrl))
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();
		byte[] jarBytes = CLIENT.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray()).body();

		MessageDigest sha1Digest = MessageDigest.getInstance("SHA-1");
		String actualSha1 = HexFormat.of().formatHex(sha1Digest.digest(jarBytes));
		if (!actualSha1.equalsIgnoreCase(expectedSha1)) {
			LOGGER.warn("Downloaded update failed hash verification, discarding");
			notifyUser("Update download failed hash verification — discarded, nothing changed.");
			return;
		}

		Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
		Files.write(modsDir.resolve(filename), jarBytes);

		FabricLoader.getInstance().getModContainer(K8basSkyblockUtilityClient.MOD_ID).ifPresent(container -> {
			for (Path oldPath : container.getOrigin().getPaths()) {
				// Locked while running on Windows; actually removed when the JVM exits, i.e. on restart.
				// Never touched/deleted before this point — the running jar is left alone while playing.
				oldPath.toFile().deleteOnExit();
			}
		});

		pendingUpdateVersion = versionNumber;
		LOGGER.info("Downloaded update {} to {}", versionNumber, filename);
		notifyUser("K8bas Skyblock Utility updated to " + versionNumber + " — restart to apply.");
	}

	private static void notifyUser(String message) {
		Minecraft.getInstance().execute(() -> {
			if (Minecraft.getInstance().player != null) {
				Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
			}
		});
	}

	private static String currentVersionString() {
		return FabricLoader.getInstance().getModContainer(K8basSkyblockUtilityClient.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
