package com.k8bas.skyblockutility.ui.widget

import com.k8bas.skyblockutility.ui.render.Clip
import com.k8bas.skyblockutility.ui.render.ScaleAbout
import com.k8bas.skyblockutility.ui.render.Shapes
import com.k8bas.skyblockutility.ui.render.Theme
import com.k8bas.skyblockutility.ui.render.TooltipLayout
import com.k8bas.skyblockutility.ui.render.UiText
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/**
 * A screen of our widgets (T2.3a, T2.3b): lays them out every frame, draws them, and routes input to
 * the one under the mouse, topmost first (REQ-UI-03). The widget that took a press gets that button's
 * drag and release.
 * - While a text field has the keyboard, every key and its release go to it and none to the game or
 *   other mods (E, the mod's keys, the debug keys; REQ-UI-22); Esc leaves the field.
 * - While a keybind widget is armed, the next key or mouse button is its binding (REQ-UI-14).
 * - One overlay (a dropdown's list, a modal) can be open; it is drawn over everything and gets all
 *   input. A click outside closes a plain overlay unchanged and is not passed on; a modal stays.
 *   Esc closes it without applying anything.
 * - The hovered widget's tooltip is drawn last, over everything, inside the window (REQ-UI-19).
 * - A screen that opts in ([keyboardNavigation], REQ-UI-18) is also run from the keyboard: Tab and Shift+Tab move a
 *   focus marked by an accent ring, Space/Enter activate, the arrows operate the focused control. Tab is the one
 *   key a typing field or a plain overlay lets go of (to the next control); a modal keeps Tab inside. The Esc
 *   order above is unchanged.
 *
 * The game checks a few keys (the narrator hotkey) before the screen sees them and turns the input
 * method off each tick unless a vanilla text box is focused, so while one of our text fields has the
 * keyboard an invisible vanilla box is the screen's focused element; it never draws or gets input.
 */
abstract class WidgetScreen protected constructor(title: Component) : Screen(title), OverlayHost {
	private val widgets = ArrayList<Widget>()
	private val keysTaken = HashSet<Int>()
	private var pressed: Widget? = null
	private var pressedButton = -1

	/** A mouse button whose press went to a capture or overlay; its release is not passed on. */
	private var swallowedRelease = -1
	private var keyboardFocus: Widget? = null
	private var textInputGuard: EditBox? = null
	private var keyGuard: EditBox? = null
	private var overlay: Overlay? = null

	/** Scrolling left over from fractional wheel or trackpad events, below one notch. */
	private var scrollRest = 0.0

	/** Asked for by [drawContent] this frame. */
	private var requestedTooltip = ""

	/** The content's scale and its centre as drawn last (T2.9); the mouse maps back through them until the next frame. */
	private var frameScale = 1F
	private var frameCx = 0F
	private var frameCy = 0F

	/** The keyboard focus apart from typing (REQ-UI-18, T2.9b), for screens with [keyboardNavigation]. */
	private val nav = KeyboardNav<Widget>()

	/** The order Tab goes through, refilled on a key or a click (never per frame). */
	private val order = ArrayList<Widget>()
	private val ring = IntArray(4)

	/** A control a key reached, to be scrolled into view before the next layout. */
	private var revealPending: Widget? = null

	/** Counts focus requests, so a press that asked for one (even for the widget that already had it) keeps it. */
	private var focusRequests = 0
	private var lastTooltip: TooltipLayout.Box? = null

	protected fun <W : Widget> add(widget: W): W {
		widgets.add(widget)
		return widget
	}

	protected fun widgets(): MutableList<Widget> = widgets

	/** The widget that has the keyboard, or null. */
	fun keyboardFocus(): Widget? = keyboardFocus

	/** Whether a mouse button is held on a widget (a slider being dragged). */
	protected fun pressHeld(): Boolean = pressed != null

	/** The open overlay, or null. */
	fun overlay(): Overlay? = overlay

	/**
	 * Whether Tab, Shift+Tab, Space/Enter and the arrows navigate this screen's controls (REQ-UI-18, T2.9b). Off by default:
	 * other screens keep their keys as they were.
	 */
	protected open fun keyboardNavigation(): Boolean = false

	/** The controls Tab goes through, in order; by default the focusable widgets as listed. */
	protected open fun collectFocusOrder(out: MutableList<Widget>) {
		for (widget in widgets) {
			if (widget.focusable()) {
				out.add(widget)
			}
		}
	}

	/** A key reached [widget]: scroll it into view in the next layout. */
	protected open fun revealFocus(widget: Widget) {
	}

	/** The control with the keyboard focus (the ring), typing or not; null for none. */
	fun navFocus(): Widget? = nav.focus

	protected fun clearNavFocus() {
		nav.focus = null
	}

	/** After controls came and went: a focus that is gone moves to the next control still there. */
	protected fun repairNavFocus() {
		nav.repair { widgets.contains(it) || overlay?.widgets()?.contains(it) == true }
	}

	override fun open(opened: Overlay) {
		if (overlay !== opened) {
			closeOverlay(true)
		}
		focus(null)
		nav.overlayOpened()
		overlay = opened
		updateFocusGuard()
	}

	override fun close(closed: Overlay) {
		if (overlay === closed) {
			closeOverlay(false)
		}
	}

	/** Gives the keyboard to a widget that wants it, or to none. */
	fun focus(widget: Widget?) {
		focusRequests++
		val target = widget?.takeIf { it.wantsKeyboard() }
		if (target === keyboardFocus) {
			return
		}
		keyboardFocus?.setFocused(false)
		keyboardFocus = target
		target?.setFocused(true)
		// A field given the keyboard (a click, Ctrl+F) is also where Tab goes on from.
		if (target != null) {
			nav.focus = target
		}
		updateFocusGuard()
	}

	/**
	 * The vanilla element the game sees as focused: the text-input guard while a field types (it keeps
	 * the input method on), the key guard while a keybind is armed or an overlay is open (it keeps the
	 * narrator hotkey from firing, without the input method), and nothing otherwise.
	 */
	private fun updateFocusGuard() {
		val focused = keyboardFocus
		when {
			focused != null && focused.usesTextInput() -> setFocused(textInputGuard())
			focused != null && focused.capturesKeys() || overlay != null -> setFocused(keyGuard())
			else -> setFocused(null)
		}
	}

	private fun textInputGuard(): EditBox = textInputGuard ?: EditBox(font, 0, 0, 0, 0, Component.empty()).also { textInputGuard = it }

	private fun keyGuard(): EditBox = keyGuard ?: KeyGuard(font).also { keyGuard = it }

	/** Looks like a focused text box to the game's narrator check, but never turns the input method on. */
	private class KeyGuard(font: Font) : EditBox(font, 0, 0, 0, 0, Component.empty()) {
		override fun setFocused(focused: Boolean) {
		}

		override fun canConsumeInput(): Boolean = true
	}

	/**
	 * A resize or GUI-scale change rebuilds the screen, and the game clears its focused element then;
	 * the guard for a focused field, armed keybind or open overlay is put back (EC-UI-01).
	 */
	override fun init() {
		super.init()
		updateFocusGuard()
	}

	/** Closes the open overlay; [cancel] first when it closes without applying. */
	private fun closeOverlay(cancel: Boolean) {
		val open = overlay ?: return
		if (cancel) {
			open.cancel()
		}
		overlay = null
		pressed = null
		pressedButton = -1
		nav.overlayClosed()
		updateFocusGuard()
	}

	override fun removed() {
		if (overlay != null) {
			closeOverlay(true)
		}
		focus(null)
		nav.clear()
		keysTaken.clear()
		super.removed()
	}

	/** The game behind the screen, dimmed; with no world, the title panorama. Subtitles stay. */
	protected fun extractDimmedGame(graphics: GuiGraphicsExtractor, partialTick: Float) {
		if (minecraft.level == null) {
			extractPanorama(graphics, partialTick)
		}
		graphics.fill(0, 0, width, height, Theme.BACKDROP)
		minecraft.gui.hud.extractDeferredSubtitles()
	}

	/**
	 * A screen shown while Esc is still held (the one before closed on its press, e.g. the HUD editor returning
	 * here) takes that Esc's repeats until its release, so one long press does not close this one too.
	 */
	override fun added() {
		super.added()
		if (InputConstants.isKeyDown(Minecraft.getInstance().window, KEY_ESCAPE)) {
			keysTaken.add(KEY_ESCAPE)
		}
	}

	/** Sets every widget's bounds for this frame. */
	protected abstract fun layout()

	/** Draws what lies behind the widgets (panel, labels). */
	protected open fun drawContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
	}

	/** Draws over the widgets, under any overlay and the tooltip (a fade veil); in the same scale as the content. */
	protected open fun drawOverWidgets(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
	}

	/**
	 * The scale this frame's content and widgets are drawn at, about the screen centre (an opening panel, T2.9);
	 * asked after [layout]. Overlays and tooltips are never scaled, and while an overlay is open the content is not either.
	 */
	protected open fun contentScale(): Float = 1F

	/** Where a mouse x or y lies in the layout: through the inverse of the scale drawn last, so input meets what was drawn. */
	private fun localX(x: Double): Double = ScaleAbout.toLocal(x, frameCx.toDouble(), frameScale)

	private fun localY(y: Double): Double = ScaleAbout.toLocal(y, frameCy.toDouble(), frameScale)

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick)
		revealPending?.let {
			revealPending = null
			revealFocus(it)
		}
		layout()
		requestedTooltip = ""
		// The transform input maps through until the next frame; no scale under an open overlay.
		frameScale = if (overlay != null) 1F else contentScale()
		frameCx = width / 2F
		frameCy = height / 2F
		val lookX = localX(mouseX.toDouble())
		val lookY = localY(mouseY.toDouble())
		// Under an open overlay nothing shows hover.
		val underX = if (overlay != null) -1 else floor(lookX).toInt()
		val underY = if (overlay != null) -1 else floor(lookY).toInt()
		val scaled = frameScale != 1F
		if (scaled) {
			graphics.pose().pushMatrix()
			graphics.pose().scaleAround(frameScale, frameCx, frameCy)
		}
		try {
			drawContent(graphics, underX, underY)
			for (widget in widgets) {
				drawClipped(graphics, widget, underX, underY)
			}
			drawFocusRing(graphics, false)
			drawOverWidgets(graphics, underX, underY)
		} finally {
			if (scaled) {
				graphics.pose().popMatrix()
			}
		}
		val open = overlay
		if (open != null) {
			open.layout(width, height)
			if (!open.stillAnchored()) {
				closeOverlay(true)
				lastTooltip = null
				return
			}
			graphics.nextStratum()
			if (open.modal()) {
				graphics.fill(0, 0, width, height, Theme.BACKDROP)
				graphics.nextStratum()
			}
			open.drawFrame(graphics, font, mouseX, mouseY)
			for (widget in open.widgets()) {
				drawClipped(graphics, widget, mouseX, mouseY)
			}
			drawFocusRing(graphics, true)
		}
		drawTooltip(graphics, mouseX, mouseY, lookX, lookY)
	}

	/**
	 * The keyboard focus ring in the accent colour (REQ-UI-17), in the layer that has the focus ([inOverlay]); none after a
	 * click, and none for a field that is typing (it draws its own outline). Clipped like its widget.
	 */
	private fun drawFocusRing(graphics: GuiGraphicsExtractor, inOverlay: Boolean) {
		if (!keyboardNavigation() || nav.focusInOverlay != inOverlay) {
			return
		}
		val target = nav.ringTarget(keyboardFocus, overlay != null) ?: return
		if (!target.showing()) {
			return
		}
		target.focusRing(ring)
		val accent = Theme.current().accent()
		val region = target.clip()
		if (region == null) {
			Shapes.roundedOutline(graphics, ring[0], ring[1], ring[2], ring[3], target.focusRadius(), 1, accent)
			return
		}
		Clip.push(graphics, region.x, region.y, region.width, region.height).use { clip ->
			if (clip.visible()) {
				Shapes.roundedOutline(graphics, ring[0], ring[1], ring[2], ring[3], target.focusRadius(), 1, accent)
			}
		}
	}

	/** Draws a widget inside its clip region, if it has one; the mouse outside the region is no hover. */
	private fun drawClipped(graphics: GuiGraphicsExtractor, widget: Widget, mouseX: Int, mouseY: Int) {
		val region = widget.clip()
		if (region == null) {
			widget.draw(graphics, font, mouseX, mouseY)
			return
		}
		if (!widget.showing()) {
			return
		}
		val inside = region.contains(mouseX.toDouble(), mouseY.toDouble())
		Clip.push(graphics, region.x, region.y, region.width, region.height).use { clip ->
			if (clip.visible()) {
				widget.draw(graphics, font, if (inside) mouseX else -1, if (inside) mouseY else -1)
			}
		}
	}

	/**
	 * Shows a tooltip at the mouse this frame, for something drawn in [drawContent] rather than
	 * a widget (e.g. a label cut with "..."). A widget's own tooltip under the mouse comes first.
	 */
	protected fun showTooltip(text: String?) {
		requestedTooltip = text ?: ""
	}

	/** The tooltip drawn in the last frame, or null. */
	protected fun lastTooltip(): TooltipLayout.Box? = lastTooltip

	/**
	 * The tooltip of the topmost widget under the mouse that has one, over everything else (REQ-UI-19):
	 * a control without one shows its card's. With an overlay open only its widgets count. None while a
	 * press is held, so a drag is not covered.
	 */
	private fun drawTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, lookX: Double, lookY: Double) {
		lastTooltip = null
		if (pressed != null) {
			return
		}
		var text = ""
		val active = activeWidgets()
		for (i in active.size - 1 downTo 0) {
			val widget = active[i]
			// The widget under the mouse in the layout (the content may be scaled); the box itself at the real mouse, unscaled.
			if (widget.contains(lookX, lookY) && widget.inClip(lookX, lookY)) {
				val own = widget.tooltipAt(lookX, lookY)
				if (!own.isNullOrEmpty()) {
					text = own
					break
				}
			}
		}
		if (text.isEmpty() && overlay == null) {
			text = requestedTooltip
		}
		if (text.isEmpty()) {
			return
		}
		val box = TooltipLayout.layout(text, mouseX, mouseY, width, height, font::width)
		lastTooltip = box
		graphics.nextStratum()
		Shapes.roundedRect(graphics, box.x(), box.y(), box.width(), box.height(), Shapes.RADIUS_CONTROL, Theme.SIDEBAR)
		Shapes.roundedOutline(graphics, box.x(), box.y(), box.width(), box.height(), Shapes.RADIUS_CONTROL, 1, Theme.SEPARATOR)
		var lineY = box.y() + TooltipLayout.PADDING
		for (line in box.lines()) {
			UiText.draw(graphics, font, line, box.x() + TooltipLayout.PADDING, lineY, Theme.TEXT_PRIMARY)
			lineY += TooltipLayout.LINE_HEIGHT
		}
	}

	private fun activeWidgets(): List<Widget> = overlay?.widgets() ?: widgets

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		// An armed keybind takes the button as its input (R27: left cancels, right resets, others bind).
		val focused = keyboardFocus
		if (focused != null && focused.capturesKeys()) {
			focused.captureMouse(event.button(), KeyMappingTarget.mouseKeyName(event.button()))
			focus(null)
			swallowedRelease = event.button()
			return true
		}
		val active = activeWidgets()
		val x = localX(event.x())
		val y = localY(event.y())
		for (i in active.size - 1 downTo 0) {
			val widget = active[i]
			if (!widget.inClip(x, y)) {
				continue
			}
			val requestsBefore = focusRequests
			val overlayBefore = overlay
			if (widget.press(x, y, event.button())) {
				pressed = widget
				pressedButton = event.button()
				if (keyboardNavigation()) {
					// The ring hides; Tab goes on from the clicked control, or from the field the press gave the keyboard to
					// (the search box's clear button), or the control gets the focus back when the overlay it opened closes.
					val handedOver = if (focusRequests != requestsBefore) keyboardFocus else null
					nav.pointer(handedOver ?: widget.takeIf { it.focusable() }, overlay !== overlayBefore)
					if (overlay === overlayBefore) {
						nav.focusInOverlay = overlay?.widgets()?.contains(widget) == true
						fillOrder()
						nav.remember(order)
					}
				}
				// A press that set the focus itself (a clear button handing it back to its field) keeps that.
				if (focusRequests == requestsBefore) {
					focus(widget)
				}
				return true
			}
		}
		val open = overlay
		if (open != null) {
			// No scale while an overlay is open, so these are the raw coordinates.
			if (!open.modal() && !open.contains(event.x(), event.y())) {
				closeOverlay(true)
				nav.ringVisible = false
			}
			swallowedRelease = event.button()
			return true
		}
		// A left click that nothing takes (empty space, a card's text, a scroll area) leaves the field;
		// other buttons keep it.
		if (event.button() == 0) {
			focus(null)
			nav.pointer(null, false)
		}
		return super.mouseClicked(event, doubleClick)
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		val held = pressed
		if (held != null && event.button() == pressedButton) {
			held.drag(localX(event.x()), localY(event.y()))
			return true
		}
		return super.mouseDragged(event, dragX, dragY)
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		val held = pressed
		if (held != null && event.button() == pressedButton) {
			pressed = null
			pressedButton = -1
			held.release(localX(event.x()), localY(event.y()))
			return true
		}
		if (event.button() == swallowedRelease) {
			swallowedRelease = -1
			return true
		}
		return super.mouseReleased(event)
	}

	/**
	 * One step per wheel event, whatever the scroll sensitivity; small trackpad movements add up to a
	 * step. Shift gives the fine step (on macOS Shift turns the wheel horizontal, which counts too).
	 * With an overlay open the wheel only reaches the overlay.
	 */
	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		val fine = Minecraft.getInstance().hasShiftDown()
		val amount = if (scrollY != 0.0) scrollY else if (fine) scrollX else 0.0
		val notches: Int
		if (abs(amount) >= 1) {
			notches = sign(amount).toInt()
			scrollRest = 0.0
		} else {
			scrollRest += amount
			notches = scrollRest.toInt()
			scrollRest -= notches
		}
		if (notches != 0) {
			val active = activeWidgets()
			val x = localX(mouseX)
			val y = localY(mouseY)
			for (i in active.size - 1 downTo 0) {
				if (active[i].inClip(x, y) && active[i].scroll(x, y, notches, fine)) {
					return true
				}
			}
		}
		return overlay != null || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		val navigating = keyboardNavigation()
		val navKey = if (navigating) navKeyOf(event) else NavKey.NONE
		// A held Space or Enter that just activated a control must not pick in the list it opened, bind the capture it armed
		// or press the button it led to.
		if (navigating && nav.isActivationRepeat(keyId(event))) {
			return true
		}
		// A held Esc that left a field or closed something must not go on to close what lies behind.
		if (event.key() == KEY_ESCAPE && keysTaken.contains(keyId(event))) {
			return true
		}
		val focused = keyboardFocus
		if (focused != null && focused.capturesKeys()) {
			keysTaken.add(keyId(event))
			focused.captureKey(InputConstants.getKey(event).name, event.key() == KEY_ESCAPE)
			focus(null)
			return true
		}
		if (focused != null) {
			keysTaken.add(keyId(event))
			// Tab leaves a field for the next control; every other key stays the field's (REQ-UI-22).
			if (navKey == NavKey.NEXT || navKey == NavKey.PREVIOUS) {
				navigate(navKey == NavKey.NEXT)
				return true
			}
			if (event.key() == KEY_ESCAPE) {
				focus(null)
			} else {
				focused.key(event.key(), shortcutCtrl(event), shift(event))
			}
			// Taken, so the game does not run the key's mapping or debug keys.
			return true
		}
		val open = overlay
		if (open != null) {
			keysTaken.add(keyId(event))
			if (navKey == NavKey.NEXT || navKey == NavKey.PREVIOUS) {
				if (!open.modal()) {
					// Tab leaves a dropdown's list unchanged and goes on from its box.
					closeOverlay(true)
				}
				navigate(navKey == NavKey.NEXT)
				return true
			}
			val inside = nav.focus
			if (navKey != NavKey.NONE && open.modal() && inside != null && open.widgets().contains(inside) && navigateKey(inside, navKey, event)) {
				return true
			}
			if (event.key() == KEY_ESCAPE) {
				closeOverlay(true)
			} else {
				open.key(event.key(), shortcutCtrl(event), shift(event))
			}
			// The screen never closes behind an overlay.
			return true
		}
		if (navKey == NavKey.NEXT || navKey == NavKey.PREVIOUS) {
			// Taken even with nothing to focus, so the game's own Tab handling never runs here.
			if (!pressHeld()) {
				navigate(navKey == NavKey.NEXT)
			}
			return true
		}
		// A repeat of a key taken above (a held Esc; an Enter that picked in a list or was bound) is taken too: it
		// cannot close the screen, and does not activate the control that has the focus again.
		if (keysTaken.contains(keyId(event))) {
			return true
		}
		val current = nav.focus
		if (navKey != NavKey.NONE && current != null && !pressHeld() && navigateKey(current, navKey, event)) {
			return true
		}
		return super.keyPressed(event)
	}

	/**
	 * Tab or Shift+Tab: the next (previous) control takes the focus and is scrolled into view; a field is given the
	 * keyboard with its text selected, as Ctrl+F does.
	 */
	private fun navigate(forward: Boolean) {
		fillOrder()
		val next = nav.navigate(order, forward) ?: return
		nav.focusInOverlay = overlay != null
		if (next.wantsKeyboard()) {
			focus(next)
			(next as? TextField)?.model()?.selectAll()
		} else {
			focus(null)
		}
		revealPending = next
	}

	/** The order Tab goes through now: an open modal's controls, or the screen's; only placed, enabled ones (a plain list has none). */
	private fun fillOrder() {
		order.clear()
		val open = overlay
		if (open != null) {
			if (open.modal()) {
				for (widget in open.widgets()) {
					if (widget.focusable() && widget.isEnabled && placed(widget)) {
						order.add(widget)
					}
				}
			}
			return
		}
		collectFocusOrder(order)
		order.removeIf { !it.focusable() || !it.isEnabled || !placed(it) }
	}

	/** Laid out this frame (a control scrolled out of view still is; Tab brings it into view). */
	private fun placed(widget: Widget): Boolean = widget.width() > 0 && widget.height() > 0

	/**
	 * Space/Enter or an arrow for the focused control: Space/Enter activates it (Enter gives a field the keyboard again),
	 * other keys go to its [Widget.navKey]. True if it used the key.
	 */
	private fun navigateKey(widget: Widget, key: NavKey, event: KeyEvent): Boolean {
		if (key == NavKey.ACTIVATE) {
			nav.ringVisible = true
			revealPending = widget
			// Recorded first: an activation that leaves the screen (the HUD editor) has this cleared with the screen.
			nav.activated(keyId(event), event.key() == KEY_SPACE)
			keysTaken.add(keyId(event))
			var done = widget.activate()
			if (!done && widget.wantsKeyboard() && event.key() != KEY_SPACE) {
				focus(widget)
				done = true
			}
			if (!done) {
				nav.released(keyId(event))
				keysTaken.remove(keyId(event))
				return false
			}
			// An armed keybind wants the next key.
			if (widget.wantsKeyboard()) {
				focus(widget)
			}
			return true
		}
		if (!widget.navKey(key, shift(event))) {
			return false
		}
		nav.ringVisible = true
		revealPending = widget
		return true
	}

	/** Releases of keys taken above are taken too; others reach the game, so no key stays held. */
	override fun keyReleased(event: KeyEvent): Boolean {
		nav.released(keyId(event))
		if (keysTaken.remove(keyId(event))) {
			return true
		}
		return super.keyReleased(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		// The space that follows a Space activation types nowhere.
		if (keyboardNavigation() && nav.takeSpaceChar(event.codepoint())) {
			return true
		}
		val focused = keyboardFocus
		if (focused != null) {
			focused.typed(event.codepoint())
			return true
		}
		return overlay != null || super.charTyped(event)
	}

	companion object {
		private const val KEY_ESCAPE = 256
		private const val KEY_SPACE = 32

		/** What a key means for navigation; Ctrl and Alt read from the keyboard too, as gametest input carries no modifiers. */
		private fun navKeyOf(event: KeyEvent): NavKey {
			val client = Minecraft.getInstance()
			return NavKey.of(event.key(), shift(event), event.hasControlDownWithQuirk() || client.hasControlDown(), event.hasAltDown() || client.hasAltDown())
		}
		private const val MOD_SHIFT = 1

		/** A key's identity for press/release pairing; keys without a key code are told apart by scancode. */
		private fun keyId(event: KeyEvent): Int = if (event.key() != -1) event.key() else -1000 - event.scancode()

		/**
		 * Ctrl (Cmd on macOS) for shortcuts and word moves, but not with Alt: Windows reports AltGr, which
		 * types characters such as "ą" or "@", as Ctrl+Alt. Also read from the keyboard, as gametest input
		 * carries no modifiers.
		 */
		@JvmStatic
		protected fun shortcutCtrl(event: KeyEvent): Boolean {
			val client = Minecraft.getInstance()
			val ctrl = event.hasControlDownWithQuirk() || client.hasControlDown()
			val alt = event.hasAltDown() || client.hasAltDown()
			return ctrl && !alt
		}

		@JvmStatic
		protected fun shift(event: KeyEvent): Boolean = (event.modifiers() and MOD_SHIFT) != 0 || Minecraft.getInstance().hasShiftDown()
	}
}
