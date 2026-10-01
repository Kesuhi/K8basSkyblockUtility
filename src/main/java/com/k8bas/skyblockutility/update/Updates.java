package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.net.SharedHttpClient;
import com.k8bas.skyblockutility.util.RuntimeVersions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Wires the notify-only update check into the game (T1.4): the first check after the client has
 * started, then every 30 minutes a local "is one due?" test; the notice on the client tick; the
 * state flushed on shutdown.
 */
public final class Updates {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/update");
	private static final long RECHECK_TICKS = 20L * 60 * 30;

	private static UpdateService service;
	private static UpdateNotifier notifier;
	private static long ticks;

	private Updates() {
	}

	public static void register() {
		String modVersion = RuntimeVersions.mod(K8basSkyblockUtilityClient.MOD_ID);
		String minecraft = RuntimeVersions.minecraft();
		URI source = GitHubReleaseSource.resolve(System.getProperty(GitHubReleaseSource.SOURCE_PROPERTY),
				FabricLoader.getInstance().isDevelopmentEnvironment());
		if (!source.equals(GitHubReleaseSource.RELEASES)) {
			LOGGER.info("Update source overridden for development: {}", source);
		}
		UpdateStateStore store = new UpdateStateStore(FabricLoader.getInstance().getConfigDir().resolve(UpdateStateStore.FILE_NAME), LOGGER::warn);
		use(new UpdateService(new GitHubReleaseSource(source, SharedHttpClient.get(), modVersion, GitHubReleaseSource.REQUEST_TIMEOUT),
						store, System::currentTimeMillis, () -> ConfigManager.general().autoUpdateCheckEnabled, modVersion, minecraft,
						() -> ConfigManager.general().updateChannel, LOGGER::info, LOGGER::warn),
				new UpdateNotifier(modVersion, LOGGER));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> service.triggerAutomatic());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			notifier.tick(client);
			if (++ticks % RECHECK_TICKS == 0) {
				service.triggerAutomatic();
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> service.flush());
	}

	/** Replaces the running service and notifier; also how gametests point it at a test server. */
	static synchronized void use(UpdateService newService, UpdateNotifier newNotifier) {
		newService.setOnOutcome(newNotifier::offer);
		service = newService;
		notifier = newNotifier;
	}
}
