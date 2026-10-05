package com.k8bas.skyblockutility.ui.screen

import com.k8bas.skyblockutility.ui.option.ActionOption
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.Choice
import com.k8bas.skyblockutility.ui.option.ColorOption
import com.k8bas.skyblockutility.ui.option.DatabaseOption
import com.k8bas.skyblockutility.ui.option.InfoOption
import com.k8bas.skyblockutility.ui.option.IntSlider
import com.k8bas.skyblockutility.ui.option.Option
import com.k8bas.skyblockutility.ui.option.RuleGroup
import com.k8bas.skyblockutility.ui.option.TextOption
import com.k8bas.skyblockutility.ui.option.Toggle
import com.k8bas.skyblockutility.ui.render.Ellipsis
import com.k8bas.skyblockutility.ui.render.TooltipLayout
import java.util.function.Predicate
import java.util.function.ToIntFunction

/**
 * The settings screen's geometry (REQ-UI-01): one pass that both drawing and hit-testing use, so a
 * click lands on the control that is drawn there (REQ-UI-03). It reruns every frame, so a resize or a
 * GUI-scale change is simply the next layout (EC-UI-01). Clicks reach the widgets placed from these
 * rectangles, so there is no second, click-only geometry. Every size is held to the window: the panel
 * keeps 4 px free, and parts that do not fit get smaller, never negative (REQ-UI-02). Pure arithmetic;
 * written from the spec, no AlpakaAddons code (REQ-UI-24).
 */
object ConfigLayout {
	const val MARGIN: Int = 4
	const val MIN_WIDTH: Int = 480
	const val MAX_WIDTH: Int = 660
	const val MIN_HEIGHT: Int = 340
	const val MAX_HEIGHT: Int = 440
	const val HEADER: Int = 38
	const val SIDEBAR: Int = 160
	const val CARD: Int = 44
	const val CARD_GAP: Int = 6
	const val SECTION: Int = 22
	const val SECTION_GAP: Int = 8
	const val PAD: Int = 10
	const val SCROLLBAR: Int = 6

	/** A rule card's header, collapsed or not (REQ-UI-11). */
	const val RULE_HEADER: Int = 24
	const val RULE_GAP: Int = 4

	/** A rule's fields sit this far right of its header. */
	const val RULE_INDENT: Int = 12
	const val TAB: Int = 22
	const val TAB_GAP: Int = 2
	const val SEARCH_WIDTH: Int = 140
	const val SEARCH_HEIGHT: Int = 16
	const val CONTROL_HEIGHT: Int = 16

	/** The category description shows at most this many lines. */
	const val DESCRIPTION_LINES: Int = 2

	/** Room for a slider's value ("Unlimited", "128 blocks") right of its track. */
	const val SLIDER_LABEL: Int = 62

	/**
	 * A rectangle in GUI pixels; width and height are never negative. A class rather than a data class, as
	 * the size is held on construction; Java sees `x()`, `y()`, `w()`, `h()`.
	 */
	class Rect(@get:JvmName("x") val x: Int, @get:JvmName("y") val y: Int, w: Int, h: Int) {
		@get:JvmName("w")
		val w: Int = maxOf(0, w)

		@get:JvmName("h")
		val h: Int = maxOf(0, h)

		fun right(): Int = x + w

		fun bottom(): Int = y + h

		fun contains(px: Double, py: Double): Boolean = px >= x && py >= y && px < right() && py < bottom()

		override fun equals(other: Any?): Boolean = other is Rect && x == other.x && y == other.y && w == other.w && h == other.h

		override fun hashCode(): Int = ((x * 31 + y) * 31 + w) * 31 + h

		override fun toString(): String = "Rect[x=$x, y=$y, w=$w, h=$h]"
	}

	@JvmRecord
	data class Frame(val panel: Rect, val header: Rect, val sidebar: Rect, val content: Rect, val search: Rect) {
		override fun toString(): String = "Frame[panel=$panel, header=$header, sidebar=$sidebar, content=$content, search=$search]"
	}

	/** One row of a category's page, at `y` from the page's top. Functions, so Java keeps calling `row.y()`. */
	sealed interface Row {
		fun y(): Int

		fun height(): Int
	}

	/** The category's name and description, at the top; [cut] when the description did not fit (its tooltip has it all). */
	data class Heading(
		@get:JvmName("category") val category: Category,
		@get:JvmName("description") val description: List<String>,
		@get:JvmName("cut") val cut: Boolean,
		private val y: Int,
		private val height: Int,
	) : Row {
		override fun y(): Int = y

		override fun height(): Int = height

		override fun toString(): String = "Heading[category=$category, description=$description, cut=$cut, y=$y, height=$height]"
	}

	/** Text wrapped to at most a few lines; when more was needed, the last line ends in "..." and [cut] is set. */
	@JvmRecord
	data class Lines(val lines: List<String>, val cut: Boolean) {
		override fun toString(): String = "Lines[lines=$lines, cut=$cut]"
	}

	/** A feature's (or a General section's) header. */
	data class Section(@get:JvmName("card") val card: Card, private val y: Int) : Row {
		override fun y(): Int = y

		override fun height(): Int = SECTION

		override fun toString(): String = "Section[card=$card, y=$y]"
	}

	/**
	 * One option's card; [featureToggle] for the feature's own switch, which a click anywhere on it
	 * flips; [indent] for a rule's field, under its rule's header.
	 */
	data class OptionRow @JvmOverloads constructor(
		@get:JvmName("card") val card: Card,
		@get:JvmName("option") val option: Option,
		@get:JvmName("featureToggle") val featureToggle: Boolean,
		private val y: Int,
		@get:JvmName("indent") val indent: Int = 0,
	) : Row {
		override fun y(): Int = y

		override fun height(): Int = CARD

		override fun toString(): String = "OptionRow[card=$card, option=$option, featureToggle=$featureToggle, y=$y, indent=$indent]"
	}

	/** A rule's header: label and colour dot; a click opens or closes the rule (REQ-UI-11). */
	data class RuleHeader(
		@get:JvmName("card") val card: Card,
		@get:JvmName("rule") val rule: RuleGroup,
		@get:JvmName("expanded") val expanded: Boolean,
		private val y: Int,
	) : Row {
		override fun y(): Int = y

		override fun height(): Int = RULE_HEADER

		override fun toString(): String = "RuleHeader[card=$card, rule=$rule, expanded=$expanded, y=$y]"
	}

	/** Why an open rule does nothing, under its header (REQ-GLOW-10, REQ-UI-12). */
	data class RuleProblem(
		@get:JvmName("rule") val rule: RuleGroup,
		@get:JvmName("lines") val lines: List<String>,
		private val y: Int,
		private val height: Int,
	) : Row {
		override fun y(): Int = y

		override fun height(): Int = height

		override fun toString(): String = "RuleProblem[rule=$rule, lines=$lines, y=$y, height=$height]"
	}

	/** What a page shows: which options and rules (a search, REQ-UI-08/09) and which rules are open. */
	fun interface Filter {
		fun shows(option: Option): Boolean

		fun shows(rule: RuleGroup): Boolean = true

		fun expanded(rule: RuleGroup): Boolean = false

		companion object {
			@JvmStatic
			fun of(shown: Predicate<Option>): Filter = Filter { shown.test(it) }
		}
	}

	@JvmRecord
	data class Page(val category: Category, val rows: List<Row>, val height: Int) {
		override fun toString(): String = "Page[category=$category, rows=$rows, height=$height]"
	}

	/** Wraps text to [maxLines] lines of [width], cutting the last one with "..." if it does not all fit (REQ-UI-07). */
	@JvmStatic
	fun fitLines(text: String, width: Int, maxLines: Int, measure: ToIntFunction<String>): Lines {
		if (maxLines <= 0) {
			// Java's String.isBlank: Character.isWhitespace only.
			return Lines(java.util.List.of(), !text.all(Character::isWhitespace))
		}
		val all = TooltipLayout.wrap(text, maxOf(1, width), measure)
		if (all.size <= maxLines) {
			return Lines(java.util.List.copyOf(all), false)
		}
		val kept = ArrayList(all.subList(0, maxLines))
		kept[maxLines - 1] = Ellipsis.fit(all.subList(maxLines - 1, all.size).joinToString(" "), width, measure)
		return Lines(java.util.List.copyOf(kept), true)
	}

	@JvmStatic
	fun frame(guiWidth: Int, guiHeight: Int): Frame {
		val width = minOf(clamp(Math.round(guiWidth * 0.70f), MIN_WIDTH, MAX_WIDTH), guiWidth - 2 * MARGIN)
		val height = minOf(clamp(Math.round(guiHeight * 0.68f), MIN_HEIGHT, MAX_HEIGHT), guiHeight - 2 * MARGIN)
		val panel = Rect((guiWidth - width) / 2, (guiHeight - height) / 2, width, height)
		val headerHeight = minOf(HEADER, panel.h)
		val header = Rect(panel.x, panel.y, panel.w, headerHeight)
		// 160 px, unless the panel is so narrow that the content would get less than 3/5 of it.
		val sidebarWidth = minOf(SIDEBAR, panel.w * 2 / 5)
		val sidebar = Rect(panel.x, header.bottom(), sidebarWidth, panel.h - headerHeight)
		val content = Rect(sidebar.right(), header.bottom(), panel.w - sidebarWidth, panel.h - headerHeight)
		val searchWidth = minOf(SEARCH_WIDTH, panel.w / 3)
		val searchHeight = minOf(SEARCH_HEIGHT, headerHeight)
		val search = Rect(header.right() - PAD - searchWidth, header.y + (headerHeight - searchHeight) / 2, searchWidth, searchHeight)
		return Frame(panel, header, sidebar, content, search)
	}

	/** The categories that have a card, in screen order (REQ-UI-04: an empty one is hidden). */
	@JvmStatic
	fun categories(cards: List<Card>): List<Category> = Category.entries.filterTo(ArrayList()) { category -> cards.any { it.category == category } }

	/**
	 * The rows of one category: the options [filter] shows (a section appears when one of its options or
	 * rules does), then each feature's rules (REQ-UI-11): a header per rule, and for an open one the reason it does
	 * nothing (if it does nothing) and its fields, indented. The rows of one category, for a content area
	 * [contentWidth] wide.
	 */
	@JvmStatic
	@JvmOverloads
	fun page(cards: List<Card>, category: Category, contentWidth: Int, width: ToIntFunction<String>, filter: Filter = Filter.of { true }): Page {
		val rows = ArrayList<Row>()
		val textWidth = maxOf(1, contentWidth - 2 * PAD - SCROLLBAR)
		val description = fitLines(category.description(), textWidth, DESCRIPTION_LINES, width)
		var y = PAD
		val headingHeight = 12 + description.lines.size * 10 + 6
		rows.add(Heading(category, description.lines, description.cut, y, headingHeight))
		y += headingHeight
		for (card in cards) {
			if (card.category != category) {
				continue
			}
			val rules = card.rules().filter { filter.shows(it) }
			if (card.all().none { filter.shows(it) } && rules.isEmpty()) {
				continue
			}
			rows.add(Section(card, y))
			y += SECTION
			for (option in card.all()) {
				if (!filter.shows(option)) {
					continue
				}
				rows.add(OptionRow(card, option, option === card.toggle, y))
				y += CARD + CARD_GAP
			}
			for (rule in rules) {
				val expanded = filter.expanded(rule)
				rows.add(RuleHeader(card, rule, expanded, y))
				y += RULE_HEADER + RULE_GAP
				if (!expanded) {
					continue
				}
				val problem = rule.problem.get()
				if (problem != null) {
					val lines = fitLines(problemText(problem), maxOf(1, textWidth - RULE_INDENT), 2, width)
					val height = lines.lines.size * 10 + 2
					rows.add(RuleProblem(rule, lines.lines, y, height))
					y += height + RULE_GAP
				}
				for (field in rule.fields) {
					rows.add(OptionRow(card, field, false, y, RULE_INDENT))
					y += CARD + CARD_GAP
				}
			}
			y += SECTION_GAP - CARD_GAP
		}
		val last = rows[rows.size - 1]
		return Page(category, java.util.List.copyOf(rows), last.y() + last.height() + PAD)
	}

	/** A row's rectangle on screen, the page scrolled by [scroll]. */
	@JvmStatic
	fun row(frame: Frame, scroll: Int, row: Row): Rect {
		val content = frame.content
		val indent = when (row) {
			is OptionRow -> row.indent
			is RuleProblem -> RULE_INDENT
			else -> 0
		}
		return Rect(content.x + PAD + indent, content.y + row.y() - scroll, content.w - 2 * PAD - SCROLLBAR - indent, row.height())
	}

	/** The line under an open rule that does nothing (REQ-GLOW-10). */
	@JvmStatic
	fun problemText(reason: String): String = "⚠ This rule can't be used: $reason. It is kept, but does nothing until you change it."

	@JvmStatic
	fun card(frame: Frame, scroll: Int, row: OptionRow): Rect = row(frame, scroll, row)

	/**
	 * The control on the right of a card: a switch, dropdown, key button or colour swatch, or a slider's track (its
	 * value label sits in the [SLIDER_LABEL] px to the track's right, which a press does not reach).
	 */
	@JvmStatic
	fun control(card: Rect, option: Option): Rect {
		var height = CONTROL_HEIGHT
		var right = card.right() - PAD
		var width = when (option) {
			is Toggle -> {
				height = 12
				22
			}
			is IntSlider -> {
				right -= SLIDER_LABEL
				// A narrow card gives up track length first, so the title keeps some room.
				maxOf(30, minOf(80, card.w - 2 * PAD - SLIDER_LABEL - 60))
			}
			is Choice<*> -> 120
			is TextOption -> 140
			is ActionOption -> 90
			is DatabaseOption -> 120
			is InfoOption -> 160
			is ColorOption -> {
				height = 14
				24
			}
			else -> 100
		}
		width = minOf(width, maxOf(0, card.w / 2 - PAD))
		return Rect(right - width, card.y + (card.h - height) / 2, width, height)
	}

	/** Where a card's title, description and status go: from its left edge to just before the control. */
	@JvmStatic
	fun text(card: Rect, option: Option): Rect {
		val control = control(card, option)
		return Rect(card.x + PAD, card.y, control.x - 8 - (card.x + PAD), card.h)
	}

	private fun clamp(value: Int, min: Int, max: Int): Int = maxOf(min, minOf(max, value))
}
