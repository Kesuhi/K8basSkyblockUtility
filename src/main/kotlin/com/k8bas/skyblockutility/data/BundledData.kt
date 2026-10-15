package com.k8bas.skyblockutility.data

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * The game's bundled-data registry (T3.0c): tables are read from the mod's own jar, so a resource pack
 * cannot replace them (EC-DATA-02), and in the dev run from the build output the same way (EC-DATA-06).
 * Loading runs on one daemon thread started at client init; the client thread only schedules it, and the
 * time it spent is logged (REQ-DATA-09). Listeners hear a loaded table on the client thread. The table ids,
 * dataVersions and gameVersions are logged once and listed by `/ksu debug data` (REQ-DATA-11).
 */
object BundledData {
	private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/data")
	private const val ROOT = "assets/" + K8basSkyblockUtilityClient.MOD_ID + "/data/"

	private val loader: Executor = Executors.newSingleThreadExecutor { task ->
		Thread(task, "k8bas-data").apply { isDaemon = true }
	}

	private val registry = DataRegistry(
		DataSource { read(it) },
		loader,
		Executor { task -> Minecraft.getInstance().execute(task) },
		Consumer { LOGGER.warn(it) },
	)

	/** The handle of [spec]'s table; register when the feature registers, before or after [start]. */
	@JvmStatic
	fun <T : Any> table(spec: TableSpec<T>): TableHandle<T> = registry.register(spec)

	/** Starts loading off the client thread; called once at client init. */
	@JvmStatic
	fun start() {
		val began = System.nanoTime()
		registry.start()
		val blockedMs = (System.nanoTime() - began) / 1_000_000
		loader.execute {
			val loadedMs = (System.nanoTime() - began) / 1_000_000
			LOGGER.info("Data registry: {} table(s) in the index, read in {} ms off the client thread; the client thread blocked {} ms",
				registry.entries().size, loadedMs, blockedMs)
			registry.describe().forEach { LOGGER.info("Data table {}", it) }
		}
	}

	/** One line per indexed table, for `/ksu debug data`. */
	@JvmStatic
	fun describe(): List<String> = registry.describe()

	private fun read(path: String): ByteArray? = try {
		FabricLoader.getInstance().getModContainer(K8basSkyblockUtilityClient.MOD_ID)
			.flatMap { it.findPath(ROOT + path) }
			.filter { Files.isRegularFile(it) }
			.map { Files.readAllBytes(it) }
			.orElse(null)
	} catch (e: IOException) {
		null
	}
}
