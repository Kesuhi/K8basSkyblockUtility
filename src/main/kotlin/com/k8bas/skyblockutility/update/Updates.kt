package com.k8bas.skyblockutility.update

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.net.SharedHttpClient
import com.k8bas.skyblockutility.util.RuntimeVersions
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.LongSupplier
import java.util.function.Supplier

/**
 * Wires the update check into the game (T1.4): the first check after the client has started, then
 * every 30 minutes a local "is one due?" test; the notice on the client tick; the state flushed on
 * shutdown. Confirmed downloads stage into `.k8bas_update/` in the game directory (T4.2); until T4.3
 * decides install eligibility, every candidate stays notify-only, so nothing is downloaded yet.
 */
object Updates {
	private val LOGGER: Logger = LoggerFactory.getLogger("k8bas_skyblock_utility/update")
	private const val RECHECK_TICKS: Long = 20L * 60 * 30

	/** In the game directory, beside `mods/`: Fabric never scans it (REQ-UPD-13). */
	const val STAGING_FOLDER: String = ".k8bas_update"

	@Volatile
	private var service: UpdateService? = null

	@Volatile
	private var notifier: UpdateNotifier? = null
	private var ticks: Long = 0

	@JvmStatic
	fun register() {
		val modVersion = RuntimeVersions.mod(K8basSkyblockUtilityClient.MOD_ID)
		val minecraft = RuntimeVersions.minecraft()
		val source = GitHubReleaseSource.resolve(
			System.getProperty(GitHubReleaseSource.SOURCE_PROPERTY),
			FabricLoader.getInstance().isDevelopmentEnvironment,
		)
		if (source != GitHubReleaseSource.RELEASES) {
			LOGGER.info("Update source overridden for development: {}", source)
		}
		val warn = Consumer<String> { LOGGER.warn(it) }
		val store = UpdateStateStore(FabricLoader.getInstance().configDir.resolve(UpdateStateStore.FILE_NAME), warn)
		// Downloads go only to GitHub, also when the release list comes from a development override (REQ-UPD-20).
		val userAgent = GitHubReleaseSource.userAgent(modVersion)
		val downloader = SecureDownloader(SharedHttpClient.get(), userAgent, SecureDownloader.GITHUB, SecureDownloader.TIMEOUT)
		// A sidecar is read during a check, so it gets the API's budget, not the jar's.
		val sidecars = SecureDownloader(SharedHttpClient.get(), userAgent, SecureDownloader.GITHUB, GitHubReleaseSource.REQUEST_TIMEOUT)
		val stager = JarStager(
			FabricLoader.getInstance().gameDir.resolve(STAGING_FOLDER), downloader, minecraft, RuntimeVersions.mod("fabricloader"),
		)
		// A download the last game was closed during leaves a .part; it goes now (EC-UPD-03).
		stager.cleanParts()
		use(
			UpdateService(
				GitHubReleaseSource(source, SharedHttpClient.get(), modVersion, GitHubReleaseSource.REQUEST_TIMEOUT),
				store, LongSupplier { System.currentTimeMillis() }, BooleanSupplier { ConfigManager.general().autoUpdateCheckEnabled },
				modVersion, minecraft, Supplier { ConfigManager.general().updateChannel }, Consumer { LOGGER.info(it) }, warn,
				UpdateHooks(ReleaseHash(sidecars, warn), UpdateHooks.EligibilityCheck { UpdateHooks.NOTIFY_ONLY }), stager,
			),
			UpdateNotifier(modVersion, LOGGER),
		)

		ClientLifecycleEvents.CLIENT_STARTED.register { service!!.triggerAutomatic() }
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			notifier!!.tick(client)
			if (++ticks % RECHECK_TICKS == 0L) {
				service!!.triggerAutomatic()
			}
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register { service!!.flush() }
	}

	/** Replaces the running service and notifier; also how gametests point it at a test server. */
	@JvmStatic
	@Synchronized
	fun use(newService: UpdateService, newNotifier: UpdateNotifier) {
		newService.setOnOutcome { newNotifier.offer(it) }
		service = newService
		notifier = newNotifier
	}
}
