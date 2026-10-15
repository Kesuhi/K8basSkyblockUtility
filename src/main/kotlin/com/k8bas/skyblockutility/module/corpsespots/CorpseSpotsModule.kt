package com.k8bas.skyblockutility.module.corpsespots

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.render.marker.Marker
import com.k8bas.skyblockutility.render.marker.MarkerAnchor
import com.k8bas.skyblockutility.render.marker.MarkerLabel
import com.k8bas.skyblockutility.render.marker.MarkerProvider
import com.k8bas.skyblockutility.render.marker.WorldMarkers
import com.k8bas.skyblockutility.skyblock.MineshaftState
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.Toggle
import com.k8bas.skyblockutility.util.ChatUtils
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/** The possible corpse spots' settings (REQ-CORPSE-05, REQ-CORPSE-08): both ON in a fresh config (R40). */
class CorpseSpotsConfig {
	@JvmField
	var enabled = true

	@JvmField
	var hideWhenLooted = true
}

/**
 * Possible corpse spots (T3.3, REQ-CORPSE-01..REQ-CORPSE-10, R40): in a Glacite Mineshaft, a "Possible corpse #n"
 * label at every known spawn spot of the shaft's layout, from the bundled table (CorpseSpotTable), and `/ksu corpses`
 * with the list by distance. The labels mark fixed coordinates from static data, so they show through blocks
 * (REQ-MARK-02); which ones show depends only on the shaft code and the Frozen Corpses widget, never on entities
 * (REQ-CORPSE-04). Nothing is sent (REQ-XC-RULES-02).
 */
class CorpseSpotsModule : Module {
	private var config: CorpseSpotsConfig? = null
	private val cfg: CorpseSpotsConfig
		get() = config!!

	/** The shaft a `/ksu corpses` was run in keeps the mineshaft state read while the feature is off (REQ-CORPSE-06). */
	private var commandHold = false
	private var request: CorpseListRequest? = null
	private var notedCode: String? = null
	private val loggedCodes = HashSet<String>()

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, CorpseSpotsConfig::class.java) { CorpseSpotsConfig() }
		instance = this
		CorpseSpotTable.register()
		MineshaftState.subscribe(ID) { cfg.enabled || commandHold }
		IslandTracker.onChange { location ->
			if (location.island != MineshaftState.ISLAND) {
				commandHold = false
				// A request still waiting is answered: "Not in a Glacite Mineshaft".
				answerRequest()
				notedCode = null
			}
		}
		ClientTickEvents.END_CLIENT_TICK.register {
			noteMissingData()
			answerRequest()
		}
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			for (root in ROOTS) {
				dispatcher.register(ClientCommands.literal(root).then(ClientCommands.literal(COMMAND).executes { listSpots() }))
			}
		}
		WorldMarkers.add(PROVIDER)
	}

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		cfg.enabled = enabled
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
	}

	override fun cards(): List<Card> = cards(cfg, Consumer { setEnabled(it) })

	override fun onSettingsClosed() {
		ConfigManager.putModuleSection(ID, cfg)
	}

	/** EC-CORPSE-01/02: with the feature on, one local note per shaft without spot data; a code the table does not know is logged once. */
	private fun noteMissingData() {
		val shaft = MineshaftState.snapshot()
		val spots = CorpseSpotTable.current()
		val code = shaft.code
		if (!cfg.enabled || !shaft.inShaft || code == null || spots == null || code == notedCode) {
			return
		}
		notedCode = code
		if (spots.spots(code).isEmpty()) {
			ChatUtils.chat("No spot data for $code")
			if (!spots.knows(code) && loggedCodes.add(code)) {
				LOGGER.info("Possible corpse spots: shaft code {} is not in the table", code)
			}
		}
	}

	/** The command keeps the shaft read and answers once the state has it (CorpseListRequest). */
	private fun listSpots(): Int {
		commandHold = IslandTracker.getCurrentIsland() == MineshaftState.ISLAND
		request = CorpseListRequest(now())
		answerRequest()
		return 1
	}

	private fun answerRequest() {
		val pending = request ?: return
		val shaft = MineshaftState.snapshot()
		if (!pending.ready(shaft, IslandTracker.getCurrentIsland() == MineshaftState.ISLAND, now())) {
			return
		}
		request = null
		val player = Minecraft.getInstance().player?.let { doubleArrayOf(it.x, it.y, it.z) }
		CorpseSpotRules.list(shaft, CorpseSpotTable.current(), CorpseSpotTable.unavailable(), player).forEach { ChatUtils.chat(it) }
	}

	private fun markers(out: Consumer<Marker>) {
		for (spot in CorpseSpotRules.visible(cfg, MineshaftState.snapshot(), CorpseSpotTable.current())) {
			val label = MarkerLabel.of(MarkerLabel.Line(Component.literal("Possible corpse #${spot.number}"), LABEL_COLOR)).asSeeThrough()
			out.accept(Marker(MarkerAnchor.fixed(spot.x + 0.5, spot.y + 0.5, spot.z + 0.5), label))
		}
	}

	companion object {
		const val ID = "corpse_spots"
		const val COMMAND = "corpses"
		private val ROOTS = arrayOf("ksu", "kskyblockutility")
		private const val LABEL_COLOR = 0x55FFFF
		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility")

		private fun now(): Long = System.nanoTime() / 1_000_000

		@Volatile
		private var instance: CorpseSpotsModule? = null

		private val PROVIDER = object : MarkerProvider {
			override fun isActive(): Boolean = instance?.cfg?.enabled == true

			override fun collect(out: Consumer<Marker>) {
				instance?.markers(out)
			}

			// The markers come from the table and the mineshaft state, which game-state clears on a world change.
			override fun reset() {
			}
		}

		/** The Mining card (REQ-CORPSE-08): found by "corpse", "spot", "mineshaft" and "waypoint". */
		@JvmStatic
		fun cards(config: CorpseSpotsConfig, setEnabled: Consumer<Boolean>): List<Card> {
			val keywords = java.util.List.of("corpse", "spot", "mineshaft", "glacite", "waypoint")
			val enabled = Toggle.of(
				"$ID.enabled", "modules.$ID.enabled",
				OptionText(
					"Possible corpse spots", "In a Glacite Mineshaft: a label at every place a frozen corpse can spawn in that layout.",
					"These are the known possible spawn spots from community data, not detected corpses: a label never tells you " +
						"whether a corpse is there. /ksu corpses lists them by distance. Data from SkyHanni-REPO, meowdding and ShaftUtils.",
					keywords,
				),
				true, Binding.of({ config.enabled }, setEnabled),
			)
			val hide = Toggle.of(
				"$ID.hide_when_looted", "modules.$ID.hideWhenLooted",
				OptionText(
					"Hide spots when all corpses are looted", "Once the Frozen Corpses tab widget lists every corpse as looted, the labels go.",
					"Without the widget the labels stay until you leave the shaft.", keywords,
				),
				true, Binding.of({ config.hideWhenLooted }, { value -> config.hideWhenLooted = value }),
			)
			return java.util.List.of(Card(ID, Category.MINING, "Possible corpse spots", enabled, java.util.List.of(hide)))
		}
	}
}
