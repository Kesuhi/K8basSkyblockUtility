package com.k8bas.skyblockutility.module.corpsespots

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.hud.HudText
import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.render.marker.Marker
import com.k8bas.skyblockutility.render.marker.MarkerAnchor
import com.k8bas.skyblockutility.render.marker.MarkerLabel
import com.k8bas.skyblockutility.render.marker.MarkerProvider
import com.k8bas.skyblockutility.render.marker.WorldMarkers
import com.k8bas.skyblockutility.skyblock.MineshaftState
import com.k8bas.skyblockutility.skyblock.WidgetPresence
import com.k8bas.skyblockutility.settings.HudOptions
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.Toggle
import com.k8bas.skyblockutility.util.ChatUtils
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundEventListener
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.function.Consumer

/** The possible corpse spots' settings (REQ-CORPSE-05, REQ-CORPSE-08, REQ-CORPSE-11): all ON in a fresh config (R40). */
class CorpseSpotsConfig {
	@JvmField
	var enabled = true

	@JvmField
	var hideWhenLooted = true

	@JvmField
	var dingHint = true

	/** Spots the ding's silence rules out are removed (REQ-CORPSE-12, R44); only with the ding hint on. */
	@JvmField
	var ruleOutSpots = true
}

/**
 * Possible corpse spots (T3.3, REQ-CORPSE-01..REQ-CORPSE-10, R40): in a Glacite Mineshaft, a "Possible corpse #n"
 * label at every known spawn spot of the shaft's layout, from the bundled table (CorpseSpotTable), and `/ksu corpses`
 * with the list by distance. The labels mark fixed coordinates from static data, so they show through blocks
 * (REQ-MARK-02); which ones show depends only on the shaft code and the Frozen Corpses widget, never on entities
 * (REQ-CORPSE-04). While the Organ Donor's ding is heard, the "Corpse ding" HUD element says how warm it is (DingHint,
 * CorpseDingHud, REQ-CORPSE-11, R43): from the sound only, read the way vanilla subtitles read sounds. Nothing is sent
 * (REQ-XC-RULES-02).
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
	private val ding = DingHint()
	private val ruleOut = SpotRuleOut()
	private var ruleOutCode: String? = null

	/** The HUD element's content, worked out each tick (REQ-HUD-11); null when there is nothing to show. */
	@Volatile
	private var dingText: HudText? = null

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, CorpseSpotsConfig::class.java) { CorpseSpotsConfig() }
		instance = this
		CorpseSpotTable.register()
		MineshaftState.subscribe(ID) { cfg.enabled || commandHold }
		IslandTracker.onChange { location ->
			if (location.island != MineshaftState.ISLAND) {
				commandHold = false
				ding.clear()
				ruleOut.reset()
				ruleOutCode = null
				// A request still waiting is answered: "Not in a Glacite Mineshaft".
				answerRequest()
				notedCode = null
			}
		}
		ClientTickEvents.END_CLIENT_TICK.register {
			noteMissingData()
			answerRequest()
			// Nothing once every corpse is looted (R57), whatever it hears.
			dingText = if (CorpseSpotRules.dingShows(cfg, MineshaftState.snapshot())) ding.warmth(now())?.let { HudText(listOf(DingHint.hudLine(it))) } else null
			tickRuleOut()
		}
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			for (root in ROOTS) {
				dispatcher.register(ClientCommands.literal(root).then(ClientCommands.literal(COMMAND).executes { listSpots() }))
			}
		}
		// The sound manager exists once the client has started; the listener only reads (like vanilla subtitles).
		ClientLifecycleEvents.CLIENT_STARTED.register { client -> client.soundManager.addListener(SoundEventListener { sound, _, _ -> heard(sound) }) }
		WorldMarkers.add(PROVIDER)
	}

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		cfg.enabled = enabled
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
	}

	override fun cards(): List<Card> = cards(cfg, Consumer { setEnabled(it) }, true)

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
		CorpseSpotRules.list(shaft, CorpseSpotTable.current(), CorpseSpotTable.unavailable(), player, ruledOut()).forEach { ChatUtils.chat(it) }
	}

	private fun heard(sound: SoundInstance) {
		val player = Minecraft.getInstance().player ?: return
		if (!cfg.enabled || !cfg.dingHint) {
			return
		}
		val id = sound.identifier.toString()
		val source = sound.source.getName()
		val at = doubleArrayOf(player.x, player.y, player.z)
		val inShaft = MineshaftState.snapshot().inShaft
		ding.heard(id, source, sound.x, sound.y, sound.z, sound.pitch, at, inShaft, now())
		if (DingHint.isDing(id, source, sound.x, sound.y, sound.z, at, inShaft, SpotRuleOut.DING_NEAR)) {
			ruleOut.ding(now())
		}
	}

	/** REQ-CORPSE-12: the player's position and the widget's corpse count each tick; a new shaft code starts over. */
	private fun tickRuleOut() {
		val shaft = MineshaftState.snapshot()
		val player = Minecraft.getInstance().player
		if (!shaft.inShaft || shaft.code != ruleOutCode) {
			ruleOut.reset()
			ruleOutCode = shaft.code
		}
		val spots = CorpseSpotTable.current()?.spots(shaft.code) ?: return
		if (!ruleOutActive() || player == null || spots.isEmpty()) {
			return
		}
		// A cut-off widget still shows a new corpse among the lines it has; the count only rising matters.
		val corpses = shaft.corpses.takeIf { it.presence == WidgetPresence.PRESENT || it.presence == WidgetPresence.PARTIAL }?.corpses?.size ?: 0
		ruleOut.tick(now(), doubleArrayOf(player.x, player.y, player.z), spots, corpses)
	}

	private fun ruleOutActive(): Boolean = cfg.enabled && cfg.dingHint && cfg.ruleOutSpots

	/** The spots ruled out now, or none while the rule-out is off. */
	private fun ruledOut(): Set<Int> = if (ruleOutActive()) ruleOut.ruledOut() else setOf()

	/** For gametests and the HUD element: what the "Corpse ding" element shows now, or null. */
	fun dingText(): HudText? = dingText

	/** Each shown spot with its label text, always plain "Possible corpse #n" (R43: the ding is on the HUD). */
	fun labels(): List<Pair<CorpseSpot, String>> =
		CorpseSpotRules.visible(cfg, MineshaftState.snapshot(), CorpseSpotTable.current(), ruledOut()).map { it to "Possible corpse #${it.number}" }

	private fun markers(out: Consumer<Marker>) {
		for ((spot, text) in labels()) {
			val label = MarkerLabel.of(MarkerLabel.Line(Component.literal(text), LABEL_COLOR)).asSeeThrough()
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

		/** For the HUD element: whether its feature and toggle are on. */
		@JvmStatic
		fun dingHudEnabled(): Boolean = instance?.let { it.cfg.enabled && it.cfg.dingHint } == true

		/** For the HUD element: its content now, or null. */
		@JvmStatic
		fun dingHudText(): HudText? = instance?.dingText

		private val PROVIDER = object : MarkerProvider {
			override fun isActive(): Boolean = instance?.cfg?.enabled == true

			override fun collect(out: Consumer<Marker>) {
				instance?.markers(out)
			}

			// The markers come from the table and the mineshaft state, which game-state clears on a world change.
			override fun reset() {
			}
		}

		/**
		 * The Mining card (REQ-CORPSE-08); [editPosition] adds "Edit position" for the Corpse ding element (REQ-HUD-06), which
		 * opens a screen, so only the running game's card has it (as General's "Edit HUD layout"). Found by "corpse", "spot",
		 * "mineshaft" and "waypoint".
		 */
		@JvmStatic
		@JvmOverloads
		fun cards(config: CorpseSpotsConfig, setEnabled: Consumer<Boolean>, editPosition: Boolean = false): List<Card> {
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
			val ding = Toggle.of(
				"$ID.ding_hint", "modules.$ID.dingHint",
				OptionText(
					"Organ Donor ding hint", "While your Organ Donor dings, a \"Corpse ding\" HUD line says how warm the ding is.",
					"The ding plays when an unlooted corpse is within 20 blocks, and gets higher the closer it is. The HUD line reads " +
						"\"far\", \"close\" or \"very close\" from that pitch alone and goes 2 s after the last ding; move it in the HUD editor.",
					java.util.List.of("corpse", "ding", "organ donor", "sound", "mineshaft"),
				),
				true, Binding.of({ config.dingHint }, { value -> config.dingHint = value }),
			)
			val ruleOut = Toggle.of(
				"$ID.rule_out", "modules.$ID.ruleOutSpots",
				OptionText(
					"Remove ruled-out spots", "With the ding hint: spots where your Organ Donor stayed silent are removed.",
					"The ding plays while an unlooted corpse is within 20 blocks. Once it has dinged in this shaft, a spot goes when you " +
						"spent 4 s within 16 blocks of it without a ding. Removed spots come back with a new corpse or in another shaft; " +
						"/ksu corpses still lists them.",
					java.util.List.of("corpse", "ding", "organ donor", "remove", "spot", "ruled out", "rule out", "silence", "mineshaft"),
				),
				true, Binding.of({ config.ruleOutSpots }, { value -> config.ruleOutSpots = value }),
			)
			val options = if (editPosition) java.util.List.of(hide, ding, ruleOut, HudOptions.editPosition(ID, CorpseDingHud.ID)) else java.util.List.of(hide, ding, ruleOut)
			return java.util.List.of(Card(ID, Category.MINING, "Possible corpse spots", enabled, options))
		}
	}
}
