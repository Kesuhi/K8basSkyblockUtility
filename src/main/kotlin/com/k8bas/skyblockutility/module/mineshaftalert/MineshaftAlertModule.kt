package com.k8bas.skyblockutility.module.mineshaftalert

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.skyblock.InventoryCounts
import com.k8bas.skyblockutility.skyblock.InventoryReader
import com.k8bas.skyblockutility.skyblock.MineshaftState
import com.k8bas.skyblockutility.skyblock.WidgetHint
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.Toggle
import com.k8bas.skyblockutility.util.ChatUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.function.Function

/** The mineshaft entry alert's settings (REQ-MSA-11); both ON in a fresh config. */
class MineshaftAlertConfig {
	@JvmField
	var enabled = true

	@JvmField
	var showKeys = true
}

/**
 * The mineshaft entry alert (T3.2, REQ-MSA-01..REQ-MSA-12): on entering a Glacite Mineshaft, one local `[KSU]` chat
 * line with the shaft, its frozen corpses and the matching keys in the inventory. It reads the game-state mineshaft
 * state (REQ-GS-17), the inventory counts (REQ-GS-07) and the shared widget hint (REQ-GS-18), and only while it is
 * on (REQ-GS-16); it has no parser of its own, never reads entities and sends nothing to the server (REQ-MSA-10).
 */
class MineshaftAlertModule : Module {
	private var config: MineshaftAlertConfig? = null
	private val cfg: MineshaftAlertConfig
		get() = config!!

	private val alert = MineshaftAlert { event -> show(event) }

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, MineshaftAlertConfig::class.java) { MineshaftAlertConfig() }
		MineshaftState.subscribe(ID) { cfg.enabled }
		// Only in a shaft: the inventory is not read anywhere else for this module (REQ-GS-16).
		InventoryReader.subscribe(ID) { cfg.enabled && cfg.showKeys && IslandTracker.getCurrentIsland() == SHAFTS }
		// IslandTracker calls its listeners on the client thread; a world change reports no island first. While the
		// alert is off no entry is recorded, so switching it on inside a shaft posts nothing until the next entry.
		IslandTracker.onChange { location ->
			if (location.island == SHAFTS && cfg.enabled) {
				alert.enter(location.serverName, now())
			} else {
				alert.leave()
			}
		}
		ClientTickEvents.END_CLIENT_TICK.register {
			if (cfg.enabled) {
				alert.tick(MineshaftState.snapshot(), now())
			}
		}
		ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { alert.reset() } }
	}

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		cfg.enabled = enabled
		if (!enabled) {
			alert.leave()
		}
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
	}

	override fun cards(): List<Card> = cards(cfg) { setEnabled(it) }

	override fun onSettingsClosed() {
		ConfigManager.putModuleSection(ID, cfg)
	}

	private fun show(event: MineshaftAlert.Event) {
		if (event is MineshaftAlert.Event.Alert) {
			// EC-MSA-07: a corpse type not known yet is logged once per launch.
			MineshaftAlertText.unknownTypes(event.corpses).filter { loggedTypes.add(it) }
				.forEach { LOGGER.info("Mineshaft alert: unknown corpse type '{}', listed by its name", it) }
		}
		lines(event, cfg, InventoryReader.snapshot(), Function { WidgetHint.text(it) }).forEach { ChatUtils.chat(it) }
	}

	companion object {
		const val ID = "mineshaft_alert"
		private const val SHAFTS = "Glacite Mineshafts"
		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility")
		private val loggedTypes = HashSet<String>()

		private fun now(): Long = System.nanoTime() / 1_000_000

		/** The chat lines for [event]: none while the alert is off (REQ-MSA-11), the hint after an unavailable line once. */
		@JvmStatic
		fun lines(event: MineshaftAlert.Event, config: MineshaftAlertConfig, keys: InventoryCounts, widgetHint: Function<String, Component>): List<Component> {
			if (!config.enabled) {
				return listOf()
			}
			return when (event) {
				is MineshaftAlert.Event.Alert ->
					listOf(MineshaftAlertText.alert(event.name, event.corpses, keys, config.showKeys, MineshaftAlertText.SACK_KEYS_WORK))
				is MineshaftAlert.Event.Unavailable ->
					listOf(MineshaftAlertText.unavailable(event.name, event.empty)) + if (event.hint) listOf(MineshaftAlertText.hint(widgetHint)) else listOf()
			}
		}

		/** The Mining card (REQ-MSA-11), found by "mineshaft", "corpse" and "key". */
		@JvmStatic
		fun cards(config: MineshaftAlertConfig, setEnabled: java.util.function.Consumer<Boolean>): List<Card> {
			val keywords = java.util.List.of("mineshaft", "corpse", "key", "glacite")
			val enabled = Toggle.of(
				"$ID.enabled", "modules.$ID.enabled",
				OptionText(
					"Mineshaft entry alert", "On entering a Glacite Mineshaft: one chat line with the shaft, its frozen corpses and your keys.",
					"Only you see it. It reads the Frozen Corpses tab widget and never the corpses themselves.", keywords,
				),
				true, Binding.of({ config.enabled }, setEnabled),
			)
			val showKeys = Toggle.of(
				"$ID.show_keys", "modules.$ID.showKeys",
				OptionText(
					"Show corpse key counts", "How many of each needed key are in your inventory and offhand; ⚠ when too few.",
					"Keys in sacks or storage are not counted.", keywords,
				),
				true, Binding.of({ config.showKeys }, { value -> config.showKeys = value }),
			)
			return java.util.List.of(Card(ID, Category.MINING, "Mineshaft entry alert", enabled, java.util.List.of(showKeys)))
		}
	}
}
