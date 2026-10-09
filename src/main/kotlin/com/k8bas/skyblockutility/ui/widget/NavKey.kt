package com.k8bas.skyblockutility.ui.widget

/**
 * What a key means for keyboard navigation in a screen that has it (REQ-UI-18, T2.9b): Tab and Shift+Tab move the
 * focus, Space and Enter activate, the arrows and Home/End/Page Up/Page Down operate the focused control, Delete
 * resets a key binding. Everything else, Esc included, is [NONE], so it goes where it always went.
 */
enum class NavKey {
	NEXT,
	PREVIOUS,
	ACTIVATE,
	LEFT,
	RIGHT,
	UP,
	DOWN,
	HOME,
	END,
	PAGE_UP,
	PAGE_DOWN,
	DELETE,
	NONE,
	;

	companion object {
		/** GLFW key codes; with Ctrl or Alt, Tab and Enter are left to others (Alt+Tab, Ctrl+Enter). */
		@JvmStatic
		fun of(key: Int, shift: Boolean, ctrl: Boolean, alt: Boolean): NavKey = when (key) {
			258 -> if (ctrl || alt) NONE else if (shift) PREVIOUS else NEXT
			32, 257, 335 -> if (ctrl || alt) NONE else ACTIVATE
			263 -> LEFT
			262 -> RIGHT
			265 -> UP
			264 -> DOWN
			268 -> HOME
			269 -> END
			266 -> PAGE_UP
			267 -> PAGE_DOWN
			261 -> DELETE
			else -> NONE
		}
	}
}
