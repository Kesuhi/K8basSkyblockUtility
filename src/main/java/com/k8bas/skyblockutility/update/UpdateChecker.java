package com.k8bas.skyblockutility.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
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
 * Checks this mod's own Modrinth project for a newer version matching the running
 * Minecraft version and loader, downloads it (verifying its hash) into mods/ under a
 * new filename, and schedules the old jar for deletion via deleteOnExit() — a running
 * jar is locked on Windows, so a live hot-swap isn't possible; this is the realistic
 * "downloads it for me, tells me to restart" version of self-updating.
 *
 * Off by default (see GeneralConfig.autoUpdateCheckEnabled) — the mod isn't published
 * yet, so MODRINTH_PROJECT_SLUG below is a placeholder until it is.
 */
public final class UpdateChecker {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/update");

	private static final String MODRINTH_PROJECT_SLUG = "TODO"; // fill in once published on Modrinth
	private static final String MINECRAFT_VERSION = "26.1.2";
	private static final String USER_AGENT =
			"Kesuhi/k8bas-skyblock-utility/" + currentVersion() + " (https://github.com/Kesuhi/K8basSkyblockUtility)";

	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	private UpdateChecker() {
	}

	public static void checkInBackgroundIfEnabled() {
		if (!ConfigManager.general().autoUpdateCheckEnabled) {
			return;
		}
		Thread.ofVirtual().name("k8bas-update-check").start(UpdateChecker::checkNow);
	}

	public static void checkNowManually() {
		Thread.ofVirtual().name("k8bas-update-check-manual").start(UpdateChecker::checkNow);
	}

	private static void checkNow() {
		try {
			String url = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version"
					+ "?loaders=" + encode("[\"fabric\"]")
					+ "&game_versions=" + encode("[\"" + MINECRAFT_VERSION + "\"]")
					+ "&include_changelog=false";

			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", USER_AGENT)
					.GET()
					.build();

			HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				LOGGER.info("Update check got HTTP {} (expected until the mod is published on Modrinth)", response.statusCode());
				return;
			}

			JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
			if (versions.isEmpty()) {
				return;
			}

			JsonObject latest = versions.get(0).getAsJsonObject();
			String latestVersion = latest.get("version_number").getAsString();
			String current = currentVersion();

			if (latestVersion.equals(current)) {
				return;
			}

			LOGGER.info("Update available: {} -> {}", current, latestVersion);

			if (ConfigManager.general().autoUpdateDownloadEnabled) {
				downloadAndInstall(latest, latestVersion);
			} else {
				notifyUser("A K8bas Skyblock Utility update (" + latestVersion + ") is available.");
			}
		} catch (Exception e) {
			LOGGER.warn("Update check failed", e);
		}
	}

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
			return;
		}

		Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
		Files.write(modsDir.resolve(filename), jarBytes);

		FabricLoader.getInstance().getModContainer(K8basSkyblockUtilityClient.MOD_ID).ifPresent(container -> {
			for (Path oldPath : container.getOrigin().getPaths()) {
				// Locked while running on Windows; actually removed when the JVM exits, i.e. on restart.
				oldPath.toFile().deleteOnExit();
			}
		});

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

	private static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(K8basSkyblockUtilityClient.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
