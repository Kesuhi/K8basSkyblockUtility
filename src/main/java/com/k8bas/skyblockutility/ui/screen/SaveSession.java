package com.k8bas.skyblockutility.ui.screen;

/**
 * When the settings screen writes the config (REQ-UI-15, D-8). Every change applies at once; the file
 * is written on a discrete commit (a slider released, a colour saved, a rule added or deleted) and once
 * when the screen closes, by whatever route (Esc, another screen replacing it, a disconnect). On the
 * close each module first rebuilds its derived state, once. There is no "unsaved changes" prompt.
 */
final class SaveSession {
	private final Runnable rebuildModules;
	private final Runnable save;
	private boolean closed;

	/**
	 * @param rebuildModules runs each module's close hook once
	 * @param save           the config's single save path
	 */
	SaveSession(Runnable rebuildModules, Runnable save) {
		this.rebuildModules = rebuildModules;
		this.save = save;
	}

	/** The screen is shown (again, after a screen it opened returns to it): its next close saves once more. */
	void open() {
		closed = false;
	}

	/** A discrete change that should reach the disk now (REQ-CFG-10). */
	void commit() {
		save.run();
	}

	/** The screen is gone: rebuild the modules and write, once, however often this is called. */
	void close() {
		if (closed) {
			return;
		}
		closed = true;
		rebuildModules.run();
		save.run();
	}
}
