package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Keybind;

/**
 * Capturing a binding (REQ-UI-14, R27): once armed, the next key binds and Esc unbinds; a left click
 * cancels (so attack is never bound by accident), a right click resets to the default, and the other
 * mouse buttons bind.
 */
public final class KeyCapture {
	private final KeyTarget target;
	private boolean armed;

	public KeyCapture(KeyTarget target) {
		this.target = target;
	}

	public void arm() {
		armed = true;
	}

	public boolean armed() {
		return armed;
	}

	public void cancel() {
		armed = false;
	}

	public void onKey(String keyName, boolean escape) {
		armed = false;
		target.bind(escape ? Keybind.UNBOUND : keyName);
	}

	/** @param mouseKeyName the button's options.txt name, e.g. "key.mouse.4" */
	public void onMouse(int button, String mouseKeyName) {
		armed = false;
		if (button == 0) {
			return;
		}
		target.bind(button == 1 ? target.defaultKey() : mouseKeyName);
	}

	public void reset() {
		armed = false;
		target.bind(target.defaultKey());
	}
}
