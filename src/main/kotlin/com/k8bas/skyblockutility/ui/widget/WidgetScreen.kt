package com.k8bas.skyblockutility.ui.widget

import com.k8bas.skyblockutility.ui.render.Clip
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

	override fun open(opened: Overlay) {
		if (overlay !== opened) {
			closeOverlay(true)
		}
		focus(null)
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
		updateFocusGuard()
	}

	override fun removed() {
		if (overlay != null) {
			closeOverlay(true)
		}
		focus(null)
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

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick)
		layout()
		requestedTooltip = ""
		// Under an open overlay nothing shows hover.
		val underX = if (overlay != null) -1 else mouseX
		val underY = if (overlay != null) -1 else mouseY
		drawContent(graphics, underX, underY)
		for (widget in widgets) {
			drawClipped(graphics, widget, underX, underY)
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
		}
		drawTooltip(graphics, mouseX, mouseY)
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
	private fun drawTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		lastTooltip = null
		if (pressed != null) {
			return
		}
		var text = ""
		val active = activeWidgets()
		for (i in active.size - 1 downTo 0) {
			val widget = active[i]
			if (widget.contains(mouseX.toDouble(), mouseY.toDouble()) && widget.inClip(mouseX.toDouble(), mouseY.toDouble())) {
				val own = widget.tooltipAt(mouseX.toDouble(), mouseY.toDouble())
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
		for (i in active.size - 1 downTo 0) {
			val widget = active[i]
			if (!widget.inClip(event.x(), event.y())) {
				continue
			}
			val requestsBefore = focusRequests
			if (widget.press(event.x(), event.y(), event.button())) {
				pressed = widget
				pressedButton = event.button()
				// A press that set the focus itself (a clear button handing it back to its field) keeps that.
				if (focusRequests == requestsBefore) {
					focus(widget)
				}
				return true
			}
		}
		val open = overlay
		if (open != null) {
			if (!open.modal() && !open.contains(event.x(), event.y())) {
				closeOverlay(true)
			}
			swallowedRelease = event.button()
			return true
		}
		// A left click that nothing takes (empty space, a card's text, a scroll area) leaves the field;
		// other buttons keep it.
		if (event.button() == 0) {
			focus(null)
		}
		return super.mouseClicked(event, doubleClick)
	}

	override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		val held = pressed
		if (held != null && event.button() == pressedButton) {
			held.drag(event.x(), event.y())
			return true
		}
		return super.mouseDragged(event, dragX, dragY)
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		val held = pressed
		if (held != null && event.button() == pressedButton) {
			pressed = null
			pressedButton = -1
			held.release(event.x(), event.y())
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
			for (i in active.size - 1 downTo 0) {
				if (active[i].inClip(mouseX, mouseY) && active[i].scroll(mouseX, mouseY, notches, fine)) {
					return true
				}
			}
		}
		return overlay != null || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
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
			if (event.key() == KEY_ESCAPE) {
				closeOverlay(true)
			} else {
				open.key(event.key(), shortcutCtrl(event), shift(event))
			}
			// The screen never closes behind an overlay.
			return true
		}
		// A repeat of a key taken above (a held Esc) is taken too, so it cannot close the screen.
		if (keysTaken.contains(keyId(event))) {
			return true
		}
		return super.keyPressed(event)
	}

	/** Releases of keys taken above are taken too; others reach the game, so no key stays held. */
	override fun keyReleased(event: KeyEvent): Boolean {
		if (keysTaken.remove(keyId(event))) {
			return true
		}
		return super.keyReleased(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		val focused = keyboardFocus
		if (focused != null) {
			focused.typed(event.codepoint())
			return true
		}
		return overlay != null || super.charTyped(event)
	}

	companion object {
		private const val KEY_ESCAPE = 256
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
