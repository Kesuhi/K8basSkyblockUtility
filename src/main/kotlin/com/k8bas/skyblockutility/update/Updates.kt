package com.k8bas.skyblockutility.update

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.net.SharedHttpClient
import com.k8bas.skyblockutility.ui.notice.Notice
import com.k8bas.skyblockutility.ui.notice.Notices
import com.k8bas.skyblockutility.util.ChatUtils
import com.k8bas.skyblockutility.util.RuntimeVersions
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.time.ZoneId
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Function
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.LongSupplier
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Wires the updater into the game: the launch's reconciliation (T4.3b), then the automatic check at startup and at the
 * first server join of the session only (R50); the notices on the client tick (T4.4); `/ksu update` and the
 * confirmation screen run their actions here, off the render thread; the state flushed on shutdown. Confirmed downloads
 * stage into `.k8bas_update/` in the game directory (T4.2) and are armed for the helper at exit (T4.3) when this game
 * may install them (InstallEligibility).
 */
object Updates {
	private val LOGGER: Logger = LoggerFactory.getLogger("k8bas_skyblock_utility/update")
	private val ACTIONS = Executors.newVirtualThreadPerTaskExecutor()

	/** The title of the updater's notices (REQ-UPD-18; T4.4 adds the others). */
	const val NOTICE_TITLE: String = "K8bas Skyblock Utility"

	/** In the game directory, beside `mods/`: Fabric never scans it (REQ-UPD-13). */
	const val STAGING_FOLDER: String = ".k8bas_update"

	@Volatile
	private var service: UpdateService? = null

	@Volatile
	private var notifier: UpdateNotifier? = null
	private val checks = SessionChecks { service!!.triggerAutomatic() }

	/** The service in use (for gametests, to restore it). */
	@JvmStatic
	fun service(): UpdateService? = service

	/** The notifier in use (for gametests, to restore it). */
	@JvmStatic
	fun notifier(): UpdateNotifier? = notifier

	@JvmStatic
	fun register() {
		val modVersion = RuntimeVersions.mod(K8basSkyblockUtilityClient.MOD_ID)
		val minecraft = RuntimeVersions.minecraft()
		val hook = testHook()
		val source = hook?.releases() ?: GitHubReleaseSource.resolve(
			System.getProperty(GitHubReleaseSource.SOURCE_PROPERTY),
			FabricLoader.getInstance().isDevelopmentEnvironment,
		)
		if (hook != null) {
			LOGGER.warn("Update: test build, releases from {}", source)
		} else if (source != GitHubReleaseSource.RELEASES) {
			LOGGER.info("Update source overridden for development: {}", source)
		}
		val warn = Consumer<String> { LOGGER.warn(it) }
		val store = UpdateStateStore(FabricLoader.getInstance().configDir.resolve(UpdateStateStore.FILE_NAME), warn)
		// Downloads go only to GitHub, also when the release list comes from a development override (REQ-UPD-20); a test
		// build adds its stand-in.
		val userAgent = GitHubReleaseSource.userAgent(modVersion)
		val hosts = if (hook == null) SecureDownloader.GITHUB else SecureDownloader.GITHUB.or(hook::allowsDownload)
		val downloader = SecureDownloader(SharedHttpClient.get(), userAgent, hosts, SecureDownloader.TIMEOUT)
		// A sidecar is read during a check, so it gets the API's budget, not the jar's.
		val sidecars = SecureDownloader(SharedHttpClient.get(), userAgent, hosts, GitHubReleaseSource.REQUEST_TIMEOUT)
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
				try {
					hook?.afterLaunch(service!!)
				} catch (e: Exception) {
					LOGGER.warn("Update: the test hook failed: {}", e.toString())
				}
				checks.started()
			}
		}
		ClientPlayConnectionEvents.JOIN.register { _, _, _ -> checks.joined() }
		ClientTickEvents.END_CLIENT_TICK.register { client -> notifier!!.tick(client) }
		ClientLifecycleEvents.CLIENT_STOPPING.register { service!!.flush() }
	}

	/**
	 * The test build's hook (T4.5), or null: the release jar carries none (ReleaseArtifactTest). Only one from this mod's
	 * own jar counts; one another jar registers is ignored, and a broken one leaves the updater as it is (REQ-UPD-03).
	 */
	private fun testHook(): UpdateTestHook? = try {
		val own = UpdateTestHook::class.java.protectionDomain.codeSource?.location?.toString()
		ServiceLoader.load(UpdateTestHook::class.java, Updates::class.java.classLoader).stream()
			.filter { own != null && it.type().protectionDomain.codeSource?.location?.toString() == own }
			.findFirst().map { it.get() }.orElse(null)
	} catch (e: ServiceConfigurationError) {
		LOGGER.warn("Update: no test hook: {}", e.toString())
		null
	}

	/** Where Fabric loaded this mod from; the paths only for a PATH origin, whose getPaths() alone does not throw. */
	private fun origin(): InstallEligibility.Origin? =
		FabricLoader.getInstance().getModContainer(K8basSkyblockUtilityClient.MOD_ID).map { container ->
			val origin = container.origin
			if (origin.kind == ModOrigin.Kind.PATH) InstallEligibility.Origin(true, origin.paths) else InstallEligibility.Origin(false, listOf())
		}.orElse(null)

	/** Runs an update action off the render thread (`/ksu update`, the confirmation screen). */
	@JvmStatic
	fun run(action: Function<UpdateActions, List<String>>): CompletableFuture<List<String>> =
		CompletableFuture.supplyAsync({ action.apply(UpdateActions(service!!, ZoneId.systemDefault())) }, ACTIONS)

	/**
	 * Opens the confirmation screen for the candidate on offer, or says in the chat that there is none. The offer is read in
	 * the background (the hash check may read a sidecar); the screen opens on the client thread, and only if the player is
	 * still where the command ran (no screen, or the chat): it never covers a menu opened meanwhile (REQ-UPD-10).
	 */
	@JvmStatic
	fun showScreen(client: Minecraft) {
		showScreen(client, null)
	}

	/** As [showScreen], also over [over] (the settings screen whose card was clicked) if it is still open. */
	private fun showScreen(client: Minecraft, over: Screen?) {
		val current = service ?: return
		CompletableFuture.supplyAsync({ UpdateActions(current, ZoneId.systemDefault()).showable() }, ACTIONS).whenComplete { found, error ->
			client.execute {
				val open = client.gui.screen()
				when {
					found == null || error != null -> say(UpdateActions(current, ZoneId.systemDefault()).nothingToShow())
					open != null && open !is ChatScreen && open !== over -> say(listOf("The update is ready to see: /ksu update show"))
					else -> client.gui.setScreen(UpdateScreen(open, found, current.confirmation(found), Function { run(it) }, Consumer { say(it) }))
				}
			}
		}
	}

	/**
	 * `/ksu update install <version>`: the confirmation screen for that version when it is the candidate, else why not. The
	 * command downloads nothing itself (REQ-UPD-11): a chat click from a server could run it.
	 */
	@JvmStatic
	fun requestInstall(client: Minecraft, version: String) {
		val current = service ?: return
		CompletableFuture.supplyAsync({ UpdateActions(current, ZoneId.systemDefault()).refusalToInstall(version) }, ACTIONS)
			.whenComplete { refusal, error ->
				if (refusal == null && error == null) {
					showScreen(client)
				} else {
					say(refusal ?: listOf("The update action failed"))
				}
			}
	}

	/**
	 * The General › Updates card over the running service (T4.4b), or none before the updater is registered. *Install* opens
	 * the confirmation screen over the settings, which *Later* returns to; *Open release page* goes through the game's link
	 * confirmation.
	 */
	@JvmStatic
	fun panel(): UpdatesPanel {
		val current = service ?: return UpdatesPanel.NONE
		return UpdatesCard(current, ZoneId.systemDefault(), LongSupplier { System.currentTimeMillis() }, object : UpdatesCard.Actions {
			override fun run(action: Function<UpdateActions, List<String>>): CompletableFuture<List<String>> = Updates.run(action)

			override fun showScreen() {
				val client = Minecraft.getInstance()
				showScreen(client, client.gui.screen())
			}

			override fun openLink(page: URI) {
				val client = Minecraft.getInstance()
				client.execute { ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), page) }
			}
		})
	}

	/** Local chat lines, on the client thread (never sent to the server). */
	private fun say(lines: List<String>) {
		Minecraft.getInstance().execute {
			lines.forEach { ChatUtils.chat(Component.literal(it).withStyle(ChatFormatting.YELLOW)) }
		}
	}

	/** Replaces the running service and notifier; also how gametests point it at a test server. */
	@JvmStatic
	@Synchronized
	fun use(newService: UpdateService, newNotifier: UpdateNotifier) {
		newService.setOnOutcome { newNotifier.offer(it) }
		newNotifier.setStaged { newService.pendingInstall()?.version() }
		newNotifier.setCurrentOffer { newService.lastOffer() }
		service = newService
		notifier = newNotifier
	}
}
