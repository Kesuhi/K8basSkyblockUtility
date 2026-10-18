package com.k8bas.skyblockutility.module.bestiary

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.skyblock.ChatReader
import com.k8bas.skyblockutility.skyblock.MenuEvent
import com.k8bas.skyblockutility.skyblock.MenuReader
import com.k8bas.skyblockutility.skyblock.ProfileService
import com.k8bas.skyblockutility.skyblock.ShapeLog
import com.k8bas.skyblockutility.skyblock.TabReader
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.Toggle
import org.slf4j.LoggerFactory
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/** The Bestiary Tracker's settings; OFF in a fresh config (REQ-BEST-01, D-6). */
class BestiaryConfig {
	@JvmField
	var enabled = false
}

/**
 * The Bestiary Tracker (T5.2, REQ-BEST-02..REQ-BEST-17): it feeds one [BestiaryTracker] from the `Bestiary:` tab widget,
 * the BESTIARY chat blocks (also those another mod hides, EC-BEST-13) and the Bestiary menu pages the player opens. The
 * readers read only while it is ON (REQ-GS-16), the tab list only on SkyBlock. It only reads: it never opens a menu,
 * clicks, sends a command or uses the Hypixel API (REQ-BEST-13). A world or server change resyncs the live baselines
 * without ending the session; another profile starts over (REQ-BEST-11). The HUD element and its card follow in T5.3.
 */
class BestiaryModule : Module {
	private var config: BestiaryConfig? = null
	private val cfg: BestiaryConfig
		get() = config!!

	private val parser = BestiaryChatParser()
	private var tracker: BestiaryTracker? = null

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, BestiaryConfig::class.java) { BestiaryConfig() }
		BestiaryTable.register()
		val on = BooleanSupplier { cfg.enabled }
		val onSkyBlock = BooleanSupplier { cfg.enabled && IslandTracker.snapshot().onSkyBlock }
		TabReader.subscribe(ID, onSkyBlock)
		TabReader.onChange { tab ->
			if (onSkyBlock.asBoolean) {
				tab.widget(BestiaryWidget.HEADER)?.let { widget ->
					tracker()?.widget(BestiaryWidget.parse(widget), IslandTracker.getCurrentIsland(), now())
				}
			}
		}
		ChatReader.subscribe(ID, on) { message ->
			if (!message.overlay) {
				when (val event = parser.accept(message)) {
					null -> {}
					is BestiaryChatEvent.Unreadable -> ShapeLog.SHARED.once("bestiary", event.line)
					else -> tracker()?.chat(event, IslandTracker.getCurrentIsland(), now())
				}
			}
		}
		MenuReader.subscribe(ID, on, { BestiaryMenu.matchesTitle(it) }) { event ->
			if (event is MenuEvent.Shown) {
				BestiaryMenu.parse(event.menu)?.let { tracker()?.menu(it, now()) }
			}
		}
		// A location change is a world or server change or a disconnect (T1.9): every widget value is a baseline again.
		IslandTracker.onChange {
			parser.reset()
			tracker?.worldChanged()
		}
		ProfileService.subscribe(ID, on)
		// Unknown on a disconnect is no switch; the tracker tells another profile from the same one seen again.
		ProfileService.onChange { profile -> profile?.let { tracker()?.profile(it.account, it.key, it.id) } }
	}

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		// Nothing was read while OFF: kills made meanwhile do not count, and a half-read chat block is forgotten.
		if (enabled != cfg.enabled) {
			parser.reset()
			tracker?.worldChanged()
		}
		cfg.enabled = enabled
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
	}

	override fun cards(): List<Card> = cards(cfg, Consumer { setEnabled(it) })

	override fun onSettingsClosed() {
		ConfigManager.putModuleSection(ID, cfg)
	}

	/** The tracker once the family table has loaded (or failed to: then only the server's values show); else null. */
	fun tracker(): BestiaryTracker? {
		tracker?.let { return it }
		val families = BestiaryTable.current()
		if (families == null && !BestiaryTable.unavailable()) {
			return null
		}
		return BestiaryTracker(families, Consumer { LOGGER.info("{}", it) }).also { tracker = it }
	}

	companion object {
		const val ID = "bestiary"
		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility")

		/** The "Bestiary Tracker" card in Odds & Trackers: its toggle, OFF by default (REQ-BEST-01, REQ-XC-TOGGLE-01). */
		@JvmStatic
		fun cards(config: BestiaryConfig, setEnabled: Consumer<Boolean>): List<Card> {
			val toggle = Toggle.of(
				"$ID.enabled", "modules.$ID.enabled",
				OptionText(
					"Bestiary Tracker", "Your bestiary progress: kills, tiers and the next milestone.",
					"Reads the Bestiary widget of your tab list, the bestiary messages in chat and the Bestiary menu pages " +
						"you open. It never opens a menu or sends anything.",
					java.util.List.of("bestiary", "kills", "tier", "milestone", "family", "mobs"),
				),
				false, Binding.of({ config.enabled }, setEnabled),
			)
			return java.util.List.of(Card(ID, Category.ODDS_TRACKERS, "Bestiary Tracker", toggle, java.util.List.of()))
		}

		/** Wall-clock ms: a value's age survives a restart (T5.2d). */
		private fun now(): Long = System.currentTimeMillis()
	}
}
