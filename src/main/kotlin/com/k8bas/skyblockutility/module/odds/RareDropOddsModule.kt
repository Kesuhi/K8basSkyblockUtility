package com.k8bas.skyblockutility.module.odds

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.skyblock.ProfileService
import com.k8bas.skyblockutility.ui.option.ActionOption
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.Choice
import com.k8bas.skyblockutility.ui.option.Option
import com.k8bas.skyblockutility.ui.option.OptionStatus
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.Toggle
import com.k8bas.skyblockutility.util.ChatUtils
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier

/** The Rare Drop Odds settings (REQ-ODDS-01, -03, -07): the feature OFF (D-6), each case and the chat lines ON, threshold 2 %. */
class OddsConfig {
	@JvmField
	var enabled = false

	@JvmField
	var scatha = true

	@JvmField
	var croesus = true

	@JvmField
	var vanguard = true

	@JvmField
	var corpses = true

	@JvmField
	var slayer = true

	@JvmField
	var chat = true

	/** Chat lines for corpse loot only at or below this per-corpse chance, in percent (REQ-ODDS-07). */
	@JvmField
	var rareThreshold = DEFAULT_THRESHOLD

	/** Whether [case]'s own toggle is on. */
	fun caseOn(case: OddsCase): Boolean = when (case) {
		OddsCase.SCATHA -> scatha
		OddsCase.CROESUS -> croesus
		OddsCase.VANGUARD -> vanguard
		OddsCase.CORPSES -> corpses
		OddsCase.SLAYER -> slayer
	}

	fun setCase(case: OddsCase, on: Boolean) {
		when (case) {
			OddsCase.SCATHA -> scatha = on
			OddsCase.CROESUS -> croesus = on
			OddsCase.VANGUARD -> vanguard = on
			OddsCase.CORPSES -> corpses = on
			OddsCase.SLAYER -> slayer = on
		}
	}

	companion object {
		const val DEFAULT_THRESHOLD = 2.0

		/** The thresholds offered, from 0.01 % to 10 % (REQ-ODDS-07). */
		@JvmField
		val THRESHOLDS: List<Double> = java.util.List.of(0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
	}
}

/**
 * Rare Drop Odds (T3.9h, REQ-ODDS-01..REQ-ODDS-04, -07, -11, -12): the card, the five cases' gate, the per-profile
 * counters and the one local chat line per event. Display only: the cases read game-state (chat, tab, sidebar,
 * location, menus) and never send, click, open or cancel anything (REQ-ODDS-04). The cases themselves are T3.9b..f.
 */
class RareDropOddsModule : Module {
	private var config: OddsConfig? = null
	private val cfg: OddsConfig
		get() = config!!

	private val counters = OddsCounters(
		Supplier { ProfileService.read(ID) }, Consumer { ProfileService.write(ID, it) }, Supplier { LocalDate.now() },
	)

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, OddsConfig::class.java) { OddsConfig() }
		instance = this
		OddsTables.register()
		// The counters are per profile (REQ-ODDS-12): the profile is read only while the feature is on (REQ-GS-16).
		ProfileService.subscribe(ID) { cfg.enabled }
		// The account and the profile's key name it; the store says whether its state can be read and written yet.
		ProfileService.onChange { profile -> counters.load(profile?.let { "${it.account}/${it.key}" }, ProfileService.storable()) }
	}

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		cfg.enabled = enabled
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
	}

	override fun cards(): List<Card> = cards(
		cfg, Consumer { setEnabled(it) }, Supplier { status() }, Runnable { ConfigManager.putModuleSection(ID, cfg) }, Runnable { counters.resetSession() },
	)

	override fun onSettingsClosed() {
		ConfigManager.putModuleSection(ID, cfg)
	}

	companion object {
		const val ID = "rare_drop_odds"
		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/odds")

		@Volatile
		private var instance: RareDropOddsModule? = null

		/** Names that had no number this session (REQ-ODDS-11): each is logged once. */
		private val unknown = OnceLog { LOGGER.info(it) }

		/** Whether [case] runs now: the feature, its toggle and its tables (OddsGate). */
		@JvmStatic
		fun active(case: OddsCase): Boolean {
			val module = instance ?: return false
			return OddsGate.active(module.cfg.enabled, module.cfg.caseOn(case), case, Predicate { OddsTables.readable(it) })
		}

		/** The counters of the current profile (REQ-ODDS-12). */
		@JvmStatic
		fun counters(): OddsCounters? = instance?.counters

		/** The rare threshold in percent (REQ-ODDS-07). */
		@JvmStatic
		fun rareThreshold(): Double = instance?.cfg?.rareThreshold ?: OddsConfig.DEFAULT_THRESHOLD

		/** One local chat line for a rare-drop event, when [case] runs and the chat lines are on (REQ-ODDS-07). */
		@JvmStatic
		fun post(case: OddsCase, line: String) {
			if (active(case) && instance?.cfg?.chat == true) {
				ChatUtils.chat(line)
			}
		}

		/** An item, corpse type or floor with no table entry: no number is shown, and it is logged once per session. */
		@JvmStatic
		fun unknown(case: OddsCase, name: String) {
			unknown.log(case.id + ":" + name, "Rare Drop Odds (${case.title}): no data for '$name', shown without a number")
		}

		/** The card's notice when a table could not be read (REQ-ODDS-11). */
		@JvmStatic
		fun status(): OptionStatus = OddsGate.notice(Predicate { OddsTables.readable(it) })?.let { OptionStatus(OptionStatus.State.PARTIAL, it) } ?: OptionStatus.OK

		/** The Odds & Trackers card (REQ-ODDS-01, -02, -03, -07, -12). "Gambling" is only a search keyword (P7). */
		@JvmStatic
		@JvmOverloads
		fun cards(
			config: OddsConfig,
			setEnabled: Consumer<Boolean>,
			status: Supplier<OptionStatus> = Supplier { OptionStatus.OK },
			changed: Runnable = Runnable { },
			resetCounters: Runnable = Runnable { },
		): List<Card> {
			val keywords = java.util.List.of("odds", "drop", "rare", "chance", "rng", "gambling")
			val master = Toggle.of(
				"$ID.enabled", "modules.$ID.enabled",
				OptionText(
					"Rare Drop Odds", "The odds behind rare drops: Scatha, dungeon chests, frozen corpses and the slayer RNG meter.",
					"Shows published per-attempt chances and your counts; it never predicts loot that has not been shown. Counts only " +
						"cover what K8bas saw while running.",
					keywords,
				),
				false, Binding.of({ config.enabled }, setEnabled),
			).withStatus(status)
			val options = ArrayList<Option>()
			for (case in OddsCase.entries) {
				options += Toggle.of(
					"$ID.${case.id}", "modules.$ID.${case.id}", OptionText(case.title, case.description, "", keywords), true,
					Binding.of({ config.caseOn(case) }, { value -> config.setCase(case, value); changed.run() }),
				)
			}
			options += Toggle.of(
				"$ID.chat", "modules.$ID.chat",
				OptionText("Chat lines", "One local chat line on a rare drop, with its odds.", "Only you see it.", keywords),
				true, Binding.of({ config.chat }, { value -> config.chat = value; changed.run() }),
			)
			options += Choice.of(
				"$ID.rare_threshold", "modules.$ID.rareThreshold",
				OptionText("Rare threshold", "Corpse loot gets a chat line only at or below this chance per corpse.", "", keywords),
				OddsConfig.DEFAULT_THRESHOLD, OddsConfig.THRESHOLDS, java.util.function.Function { threshold(it) },
				Binding.of({ config.rareThreshold }, { value -> config.rareThreshold = value; changed.run() }),
			)
			options += ActionOption(
				"$ID.reset_counters",
				OptionText("Reset odds counters", "Sets this session's counts back to zero.", "The counts kept per profile stay.", keywords),
				"Reset", false, resetCounters,
			)
			return java.util.List.of(Card(ID, Category.ODDS_TRACKERS, "Rare Drop Odds", master, options))
		}

		private fun threshold(percent: Double): String = java.math.BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString() + " %"
	}
}
