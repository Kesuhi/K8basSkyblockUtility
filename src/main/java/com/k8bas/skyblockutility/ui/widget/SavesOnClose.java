package com.k8bas.skyblockutility.ui.widget;

/**
 * A screen that writes the config when it closes. The game flushes the config on shutdown before it
 * removes the open screen (REQ-CFG-10), so the shutdown hook asks such a screen to save first; its own
 * removal then writes nothing more.
 */
public interface SavesOnClose {
	void saveBeforeShutdown();
}
