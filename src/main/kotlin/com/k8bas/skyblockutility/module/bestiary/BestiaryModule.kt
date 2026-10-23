package com.k8bas.skyblockutility.module.bestiary

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.config.Normalizable
import com.k8bas.skyblockutility.hud.HudText
import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.settings.HudOptions
import com.k8bas.skyblockutility.settings.ListDatabase
import com.k8bas.skyblockutility.skyblock.ChatReader
import com.k8bas.skyblockutility.skyblock.MenuEvent
import com.k8bas.skyblockutility.skyblock.MenuReader
import com.k8bas.skyblockutility.skyblock.ProfileService
import com.k8bas.skyblockutility.skyblock.ShapeLog
import com.k8bas.skyblockutility.skyblock.TabReader
import com.k8bas.skyblockutility.skyblock.WidgetHint
import com.k8bas.skyblockutility.ui.option.ActionOption
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.Checklist
import com.k8bas.skyblockutility.ui.option.Choice
import com.k8bas.skyblockutility.ui.option.DatabaseOption
import com.k8bas.skyblockutility.ui.option.IntSlider
import com.k8bas.skyblockutility.ui.option.Option
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.RuleDatabase
import com.k8bas.skyblockutility.ui.option.Toggle
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import org.slf4j.LoggerFactory
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * The Bestiary Tracker's settings: OFF in a fresh config (REQ-BEST-01, D-6), the rate over 5 minutes (REQ-BEST-06), the
 * lines and selection of §12.H (R11, R59). A field missing from an older file keeps its default.
 */
class BestiaryConfig : Normalizable {
	@JvmField
	var enabled = false

	/** The rolling window of the rate, 1 to 60 minutes. */
	@JvmField
	var windowMinutes = 5

	/** End the session on another island; OFF (R11, REQ-BEST-07). */
	@JvmField
	var resetOnIslandChange = false

	/** Which families the HUD shows (REQ-BEST-09): auto-follow by default. */
	@JvmField
	var selection: SelectionMode? = SelectionMode.AUTO

	/** The pinned families' table ids, at most 5. */
	@JvmField
	var pins: MutableList<String>? = ArrayList()

	/** Leave maxed families out; ON (R59). */
	@JvmField
	var hideMaxed = true

	/** The lines shown (REQ-BEST-08, §12.H). */
	@JvmField
	var lineFamily = BestiaryLine.FAMILY.defaultShown

	@JvmField
	var lineKills = BestiaryLine.KILLS.defaultShown

	@JvmField
	var lineToNext = BestiaryLine.TO_NEXT.defaultShown

	@JvmField
	var lineToMax = BestiaryLine.TO_MAX.defaultShown

	@JvmField
	var lineMilestone = BestiaryLine.MILESTONE.defaultShown

	@JvmField
	var lineRate = BestiaryLine.RATE.defaultShown

	@JvmField
	var lineEta = BestiaryLine.ETA.defaultShown

	@JvmField
	var lineSessionKills = BestiaryLine.SESSION_KILLS.defaultShown

	@JvmField
	var lineSessionAverage = BestiaryLine.SESSION_AVERAGE.defaultShown

	@JvmField
	var lineSourceAge = BestiaryLine.SOURCE_AGE.defaultShown

	/** The lines' order, a preset (R59). */
	@JvmField
	var lineOrder: LineOrder? = LineOrder.PROGRESS_FIRST

	/** The lines joined into one (T5.3b); OFF. */
	@JvmField
	var compact = false

	/** Tiers as Arabic or Roman numerals; Arabic (R59). */
	@JvmField
	var numerals: TierNumerals? = TierNumerals.ARABIC

	/** The layout these settings give. */
	fun layout(): BestiaryLayout {
		val shown = BestiaryLine.entries.filter { shown(it) }
		return BestiaryLayout(java.util.Set.copyOf(shown), (lineOrder ?: LineOrder.PROGRESS_FIRST).lines, numerals ?: TierNumerals.ARABIC, compact)
	}

	private fun shown(line: BestiaryLine): Boolean = when (line) {
		BestiaryLine.FAMILY -> lineFamily
		BestiaryLine.KILLS -> lineKills
		BestiaryLine.TO_NEXT -> lineToNext
		BestiaryLine.TO_MAX -> lineToMax
		BestiaryLine.MILESTONE -> lineMilestone
		BestiaryLine.RATE -> lineRate
		BestiaryLine.ETA -> lineEta
		BestiaryLine.SESSION_KILLS -> lineSessionKills
		BestiaryLine.SESSION_AVERAGE -> lineSessionAverage
		BestiaryLine.SOURCE_AGE -> lineSourceAge
	}

	override fun normalize(): Boolean {
		val window = windowMinutes.coerceIn(1, 60)
		// A hand-edited file: no mode, order, numerals or list, a null or blank pin, a pin repeated, more than five.
		val pinned = pins.orEmpty().filter { !it.isNullOrBlank() }.distinct().take(BestiarySelection.MAX_PINS)
		val changed = window != windowMinutes || selection == null || pins != pinned || lineOrder == null || numerals == null
		windowMinutes = window
		selection = selection ?: SelectionMode.AUTO
		pins = ArrayList(pinned)
		lineOrder = lineOrder ?: LineOrder.PROGRESS_FIRST
		numerals = numerals ?: TierNumerals.ARABIC
		return changed
	}
}

/**
 * The Bestiary Tracker (T5.2, T5.3, REQ-BEST-01..REQ-BEST-17): it feeds one [BestiaryTracker] from the `Bestiary:` tab
 * widget, the BESTIARY chat blocks (also those another mod hides, EC-BEST-13) and the Bestiary menu pages the player
 * opens, and draws the [BestiaryHud] element. The readers read only while it is ON (REQ-GS-16), the tab list only on
 * SkyBlock. It only reads: it never opens a menu, clicks, sends a command or uses the Hypixel API (REQ-BEST-13). A world
 * or server change resyncs the live baselines without ending the session, unless the player chose "reset on island
 * change"; another profile starts over (REQ-BEST-11, REQ-BEST-07). On each client tick while ON it counts the session's
 * time on SkyBlock, keeps the profile store (REQ-BEST-14), and works out the HUD's lines: the selected families
 * (REQ-BEST-09), or the shared widget hint after 10 s without a `Bestiary:` section (REQ-BEST-12, REQ-GS-18), which shows
 * on the HUD only and is never sent to chat.
 */
class BestiaryModule : Module {
	private var config: BestiaryConfig? = null
	private val cfg: BestiaryConfig
		get() = config!!

	private val parser = BestiaryChatParser()
	private val watch = BestiaryWidgetWatch()

	/** The profile's last-known values (T5.2d): read once a profile is known, written when they changed. */
	private val store = BestiaryProfileStore(
		Supplier { ProfileService.read(STORE) }, Consumer { ProfileService.write(STORE, it) },
		BooleanSupplier { ProfileService.storable() }, Supplier { ProfileService.current()?.let { it.account to it.key } },
		Consumer { LOGGER.warn("{}", it) },
	)
	private var tracker: BestiaryTracker? = null

	/** The HUD's lines as of the last tick; null draws nothing (REQ-HUD-11). */
	@Volatile
	private var hud: HudText? = null

	/** The editor's preview with the player's lines, order and numerals, made again when they or the family table change. */
	@Volatile
	private var preview: HudText? = null
	private val previews = BestiaryPreview()

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, BestiaryConfig::class.java) { BestiaryConfig() }
		instance = this
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
		// A location change is a world or server change or a disconnect (T1.9): every widget value is a baseline again, with
		// the option another island ends the session, and the widget gets a fresh 10 s before the hint.
		IslandTracker.onChange { location ->
			parser.reset()
			watch.restart(now())
			tracker()?.locationChanged(location.island, cfg.resetOnIslandChange)
		}
		ClientTickEvents.END_CLIENT_TICK.register { tick() }
		ProfileService.subscribe(ID, on)
		// Unknown on a disconnect is no switch; the tracker tells another profile from the same one seen again.
		ProfileService.onChange { profile -> profile?.let { tracker()?.profile(it.account, it.key, it.id) } }
	}

	private fun tick() {
		val layout = cfg.layout()
		preview = previews.get(layout, BestiaryTable.current())
		if (!cfg.enabled) {
			hud = null
			return
		}
		val tracker = tracker() ?: return
		val now = now()
		val snapshot = IslandTracker.snapshot()
		tracker.tick(snapshot.onSkyBlock, now)
		store.tick(tracker, now)
		// The tab list as it is now: an unchanged one fires no change event, so the hint's clock reads it on the tick.
		if (snapshot.onSkyBlock && TabReader.snapshot().widget(BestiaryWidget.HEADER) != null) {
			watch.seen(now)
		}
		val mode = cfg.selection ?: SelectionMode.AUTO
		// Wrapped, so the hint stays left of the crosshair at any GUI scale.
		val hint = if (watch.hint(snapshot.island, now)) {
			BestiaryHudLines.wrap(WidgetHint.text(BestiaryWidget.HEADER).string, HINT_WIDTH).map { "§7$it" }
		} else {
			null
		}
		val lines = when {
			// Off SkyBlock (another server, singleplayer) nothing shows.
			!snapshot.onSkyBlock -> listOf()
			mode == SelectionMode.PINNED && cfg.pins.isNullOrEmpty() -> hint ?: listOf("§8No family pinned yet")
			else -> {
				val families = BestiarySelection.select(tracker, mode, cfg.pins.orEmpty(), cfg.hideMaxed)
				val panel = Supplier {
					BestiaryHudLines.lines(families, { tracker.rate(it, now) }, tracker.session(), tracker.milestone(), layout, now, hint == null)
				}
				// Without the widget the families known from the menu or the store stay under the hint (EC-BEST-01).
				if (hint != null) BestiaryHudLines.withHint(hint, families, panel) else panel.get()
			}
		}
		hud = if (lines.isEmpty()) null else HudText(lines)
	}

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		// Nothing was read while OFF: kills made meanwhile do not count, a half-read chat block is forgotten, and the widget
		// gets a fresh 10 s before the hint.
		if (enabled != cfg.enabled) {
			parser.reset()
			tracker?.worldChanged()
			watch.restart(now())
		}
		cfg.enabled = enabled
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
	}

	override fun cards(): List<Card> = cards(cfg, Consumer { setEnabled(it) }, Runnable { tracker?.resetSession() }, pinPicker())

	override fun onSettingsClosed() {
		ConfigManager.putModuleSection(ID, cfg)
		tracker?.setWindowMinutes(cfg.windowMinutes)
	}

	/** For gametests: the settings as they are. */
	fun configForTest(): BestiaryConfig = cfg

	/** The tracker once the family table has loaded (or failed to: then only the server's values show); else null. */
	fun tracker(): BestiaryTracker? {
		tracker?.let { return it }
		val families = BestiaryTable.current()
		if (families == null && !BestiaryTable.unavailable()) {
			return null
		}
		return BestiaryTracker(families, Consumer { LOGGER.info("{}", it) }).also {
			it.setWindowMinutes(cfg.windowMinutes)
			// The island and the profile it is made on: the first change after it ends the session when the option says so,
			// and a later switch makes it forget this profile.
			it.locationChanged(IslandTracker.getCurrentIsland(), false)
			ProfileService.current()?.let { profile -> it.profile(profile.account, profile.key, profile.id) }
			tracker = it
		}
	}

	/**
	 * The table's families to pin from, in a picker grouped by area; Add pins one, up to 5 (REQ-BEST-09). Pinned families
	 * stay listed, greyed and marked, so the picker shows what is pinned; with 5 pinned every other one says so.
	 */
	private fun pinPicker(): RuleDatabase {
		var listed: List<BestiaryFamily> = listOf()
		var listedFor: Pair<BestiaryFamilies, List<String>>? = null
		return ListDatabase(
			"Pin a family",
			Supplier {
				when {
					BestiaryTable.current() != null -> RuleDatabase.State.READY
					BestiaryTable.unavailable() -> RuleDatabase.State.UNAVAILABLE
					else -> RuleDatabase.State.LOADING
				}
			},
			// A new list whenever the pins change, so the entries are made again with their notes (ListDatabase maps per list).
			Supplier {
				val families = BestiaryTable.current()
				val pins = java.util.List.copyOf(cfg.pins.orEmpty())
				if (families != null && (listedFor?.first !== families || listedFor?.second != pins)) {
					listedFor = families to pins
					listed = java.util.List.copyOf(families.families())
				}
				listed
			},
			java.util.function.Function { family ->
				val area = BestiaryTable.current()?.categoryName(family.category, null) ?: family.category
				val sub = family.subcategory?.let { BestiaryTable.current()?.categoryName(family.category, it) }
				val pins = cfg.pins.orEmpty()
				val note = when {
					family.id in pins -> "pinned"
					pins.size >= BestiarySelection.MAX_PINS -> "5 pinned: clear them first"
					else -> null
				}
				RuleDatabase.Entry(family.id, family.name, area, sub, listOfNotNull(family.name, area, sub).joinToString(" "), note)
			},
			Supplier { setOf() },
			Consumer { family -> BestiarySelection.pin(cfg.pins.orEmpty(), family.id)?.let { cfg.pins = ArrayList(it) } },
		)
	}

	companion object {
		const val ID = "bestiary"

		/** The profile store entry (reserved for this module in ProfileStore). */
		private const val STORE = "bestiary"
		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility")

		@Volatile
		private var instance: BestiaryModule? = null

		/** Whether the HUD element shows: the tracker is ON. */
		@JvmStatic
		fun hudEnabled(): Boolean = instance?.cfg?.enabled == true

		/** The HUD's lines of the last tick, or null. */
		@JvmStatic
		fun hudText(): HudText? = instance?.hud

		/** The editor's preview with the player's layout, once a tick has made it; else null. */
		@JvmStatic
		fun previewText(): HudText? = instance?.preview

		/** The hint's width in characters: well left of the crosshair at GUI scale 4 on 1920×1080. */
		private const val HINT_WIDTH = 34

		/**
		 * The "Bestiary Tracker" card in Odds & Trackers (REQ-BEST-01, REQ-XC-TOGGLE-01): its toggle, OFF by default, the
		 * lines, the rate's window, the session's reset, the family selection, and in the game the pin picker and "Edit
		 * position" ([pinPicker] null leaves both out, as the settings tests build the card without a game).
		 */
		@JvmStatic
		@JvmOverloads
		fun cards(config: BestiaryConfig, setEnabled: Consumer<Boolean>, resetSession: Runnable = Runnable {}, pinPicker: RuleDatabase? = null): List<Card> {
			val keywords = java.util.List.of("bestiary", "kills", "tier", "milestone", "family", "mobs")
			val toggle = Toggle.of(
				"$ID.enabled", "modules.$ID.enabled",
				OptionText(
					"Bestiary Tracker", "A HUD panel with your bestiary progress: kills, tiers, kills per hour and the milestone.",
					"Reads the Bestiary widget of your tab list, the bestiary messages in chat and the Bestiary menu pages " +
						"you open. It never opens a game menu or sends anything.",
					keywords,
				),
				false, Binding.of({ config.enabled }, setEnabled),
			)
			val options = ArrayList<Option>()
			// The lines in one checklist (REQ-BEST-08, R60); each item keeps the id, config field and default it had as a toggle.
			val lines = listOf(
				line("family", "The family's name and its tier out of its last one.", BestiaryLine.FAMILY,
					Binding.of({ config.lineFamily }, { config.lineFamily = it })),
				line("kills", "The family's kills, as the server counts them.", BestiaryLine.KILLS, Binding.of({ config.lineKills }, { config.lineKills = it })),
				line("to_next", "The kills left to the family's next tier.", BestiaryLine.TO_NEXT, Binding.of({ config.lineToNext }, { config.lineToNext = it })),
				line("to_max", "The kills left to the family's last tier, or MAX.", BestiaryLine.TO_MAX, Binding.of({ config.lineToMax }, { config.lineToMax = it })),
				line("milestone", "Your Bestiary Milestone and the tiers to the next, once known.", BestiaryLine.MILESTONE,
					Binding.of({ config.lineMilestone }, { config.lineMilestone = it })),
				line("rate", "The family's kills per hour over the last minutes.", BestiaryLine.RATE, Binding.of({ config.lineRate }, { config.lineRate = it })),
				line("eta", "How long the next tier and the last one take at that pace.", BestiaryLine.ETA, Binding.of({ config.lineEta }, { config.lineEta = it })),
				line("session_kills", "The family's kills since the session began.", BestiaryLine.SESSION_KILLS,
					Binding.of({ config.lineSessionKills }, { config.lineSessionKills = it })),
				line("session_average", "The session's kills of all families, per hour, and its length.", BestiaryLine.SESSION_AVERAGE,
					Binding.of({ config.lineSessionAverage }, { config.lineSessionAverage = it })),
				line("source_age", "Where the values come from and how old they are.", BestiaryLine.SOURCE_AGE,
					Binding.of({ config.lineSourceAge }, { config.lineSourceAge = it })),
			)
			options += Checklist(
				"$ID.lines", OptionText("Lines shown", "Pick which lines the panel shows.", "", java.util.List.of("lines", "show", "hide")), lines,
			)
			options += Choice.of(
				"$ID.line_order", "modules.$ID.lineOrder",
				OptionText("Line order", "Which lines come first: the progress, the session or the milestone.", "", keywords),
				LineOrder.PROGRESS_FIRST, java.util.List.of(*LineOrder.entries.toTypedArray()), java.util.function.Function { it.label },
				Binding.of({ config.lineOrder ?: LineOrder.PROGRESS_FIRST }, { config.lineOrder = it }),
			)
			options += Choice.of(
				"$ID.numerals", "modules.$ID.numerals",
				OptionText("Tier numbers", "Tiers and the milestone as 15 or as XV.", "", java.util.List.of("roman", "arabic", "numerals")),
				TierNumerals.ARABIC, java.util.List.of(*TierNumerals.entries.toTypedArray()), java.util.function.Function { it.label },
				Binding.of({ config.numerals ?: TierNumerals.ARABIC }, { config.numerals = it }),
			)
			options += Toggle.of(
				"$ID.compact", "modules.$ID.compact",
				OptionText("Compact", "Joins each family's lines into one or two; the shared lines stay apart.", "", java.util.List.of("compact", "one line")),
				false, Binding.of({ config.compact }, { config.compact = it }),
			)
			options += IntSlider.of(
				"$ID.window", "modules.$ID.windowMinutes",
				OptionText("Kills per hour over", "How many recent minutes the kills per hour count.", "", java.util.List.of("rate", "window", "per hour")),
				5, 1, 60, 1, "min", "", Binding.of({ config.windowMinutes }, { config.windowMinutes = it }),
			)
			options += Toggle.of(
				"$ID.reset_on_island_change", "modules.$ID.resetOnIslandChange",
				OptionText("New session on another island", "Starts a new session whenever you go to another island.",
					"The Dungeon Hub and a dungeon run count as two islands.", java.util.List.of("session", "island", "reset")),
				false, Binding.of({ config.resetOnIslandChange }, { config.resetOnIslandChange = it }),
			)
			options += ActionOption("$ID.reset_session", OptionText("Reset session", "Sets the session's kills, kills per hour and time to zero.", "",
				java.util.List.of("session", "reset")), "Reset", true, resetSession)
			options += Choice.of(
				"$ID.selection", "modules.$ID.selection",
				OptionText("Families shown", "The family whose kills changed last, or the families you pinned.", "", java.util.List.of("pin", "follow")),
				SelectionMode.AUTO, java.util.List.of(*SelectionMode.entries.toTypedArray()),
				java.util.function.Function { if (it == SelectionMode.AUTO) "Follow kills" else "Pinned families" },
				Binding.of({ config.selection ?: SelectionMode.AUTO }, { config.selection = it }),
			)
			if (pinPicker != null) {
				options += DatabaseOption("$ID.pin", OptionText("Pin families", "Picks up to 5 families for the pinned view; pinned ones are marked.", "",
					java.util.List.of("pin", "family")), "Pin…", pinPicker)
			}
			options += ActionOption("$ID.clear_pins", OptionText("Clear pins", "Removes every pinned family.", "", java.util.List.of("pin", "clear")),
				"Clear", true, Runnable { config.pins = ArrayList() })
			options += Toggle.of(
				"$ID.hide_maxed", "modules.$ID.hideMaxed",
				OptionText("Hide maxed families", "Leaves out families at their last tier.", "", java.util.List.of("max", "hide")),
				true, Binding.of({ config.hideMaxed }, { config.hideMaxed = it }),
			)
			if (pinPicker != null) {
				options += HudOptions.editPosition(ID, BestiaryHud.ID)
			}
			return java.util.List.of(Card(ID, Category.ODDS_TRACKERS, "Bestiary Tracker", toggle, java.util.List.copyOf(options)))
		}

		/** A line's item of the checklist, labelled as the line; its id and config field are the ones it had as a toggle of its own. */
		private fun line(key: String, description: String, line: BestiaryLine, binding: Binding<Boolean>): Toggle =
			Toggle.of("$ID.line_$key", "modules.$ID.line" + key.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) },
				OptionText(line.label, description, "", java.util.List.of()), line.defaultShown, binding)

		/** Wall-clock ms: a value's age survives a restart (T5.2d). */
		private fun now(): Long = System.currentTimeMillis()
	}
}
