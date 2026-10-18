package com.k8bas.skyblockutility.update

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.net.SharedHttpClient
import com.k8bas.skyblockutility.ui.notice.Notice
import com.k8bas.skyblockutility.ui.notice.Notices
import com.k8bas.skyblockutility.util.RuntimeVersions
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.LongSupplier
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Wires the update check into the game (T1.4): the first check after the client has started, then
 * every 30 minutes a local "is one due?" test; the notice on the client tick; the state flushed on
 * shutdown. Confirmed downloads stage into `.k8bas_update/` in the game directory (T4.2) and are armed
 * for the helper at exit (T4.3) when this game may install them (InstallEligibility).
 */
object Updates {
	private val LOGGER: Logger = LoggerFactory.getLogger("k8bas_skyblock_utility/update")
	private const val RECHECK_TICKS: Long = 20L * 60 * 30

	/** The title of the updater's notices (REQ-UPD-18; T4.4 adds the others). */
	const val NOTICE_TITLE: String = "K8bas Skyblock Utility"

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
		val loader = FabricLoader.getInstance()
		val root = loader.gameDir.resolve(STAGING_FOLDER)
		val stager = JarStager(root, downloader, minecraft, RuntimeVersions.mod("fabricloader"))
		// A download the last game was closed during leaves a .part; it goes now (EC-UPD-03).
		stager.cleanParts()
		val eligibility = InstallEligibility(
			loader.isDevelopmentEnvironment, origin(), modVersion, loader.gameDir, Predicate { Files.isWritable(it) },
		)
		val installer = StagedInstall(
			root, Supplier { eligibility.runningJar }, StagedInstall.processLauncher(),
			Consumer { Runtime.getRuntime().addShutdownHook(it) }, warn,
		)
		use(
			UpdateService(
				GitHubReleaseSource(source, SharedHttpClient.get(), modVersion, GitHubReleaseSource.REQUEST_TIMEOUT),
				store, LongSupplier { System.currentTimeMillis() }, BooleanSupplier { ConfigManager.general().autoUpdateCheckEnabled },
				modVersion, minecraft, Supplier { ConfigManager.general().updateChannel }, Consumer { LOGGER.info(it) }, warn,
				UpdateHooks(ReleaseHash(sidecars, warn), eligibility), stager, installer,
			),
			UpdateNotifier(modVersion, LOGGER),
		)

		ClientLifecycleEvents.CLIENT_STARTED.register {
			// The staged update is settled first, locally, then the automatic check may run (REQ-UPD-18).
			Thread.ofVirtual().name("k8bas-update-launch").start {
				try {
					val report = service!!.reconcileAtLaunch()
					report.toast?.let { Notices.post(Notice(NOTICE_TITLE, listOf(it))) }
					notifier!!.showOnJoin(report.chat)
				} catch (e: Exception) {
					LOGGER.warn("Update: the launch check failed: {}", e.toString())
				}
				service!!.triggerAutomatic()
			}
		}
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			notifier!!.tick(client)
			if (++ticks % RECHECK_TICKS == 0L) {
				service!!.triggerAutomatic()
			}
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register { service!!.flush() }
	}

	/** Where Fabric loaded this mod from; the paths only for a PATH origin, whose getPaths() alone does not throw. */
	private fun origin(): InstallEligibility.Origin? =
		FabricLoader.getInstance().getModContainer(K8basSkyblockUtilityClient.MOD_ID).map { container ->
			val origin = container.origin
			if (origin.kind == ModOrigin.Kind.PATH) InstallEligibility.Origin(true, origin.paths) else InstallEligibility.Origin(false, listOf())
		}.orElse(null)

	/** Replaces the running service and notifier; also how gametests point it at a test server. */
	@JvmStatic
	@Synchronized
	fun use(newService: UpdateService, newNotifier: UpdateNotifier) {
		newService.setOnOutcome { newNotifier.offer(it) }
		service = newService
		notifier = newNotifier
	}
}
