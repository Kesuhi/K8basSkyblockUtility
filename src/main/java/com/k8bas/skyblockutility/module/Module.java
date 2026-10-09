package com.k8bas.skyblockutility.module;

import com.k8bas.skyblockutility.ui.option.Card;

import java.util.List;

/**
 * A self-contained feature. Fabric API events can't be unregistered once
 * registered (verified: net.fabricmc.fabric.api.event.Event exposes only register()),
 * so onRegister() is a one-time bootstrap step, not something re-run per enable/disable —
 * modules gate their own behavior on isEnabled() internally (see HighlightManager) instead
 * of having listeners added/removed at toggle time.
 */
public interface Module {
	/** Stable id, used as the config-section key. e.g. "mob_highlighter". */
	String id();

	/** Called once at mod bootstrap: load config, register listeners, keybinds and highlight managers. */
	void onRegister();

	boolean isEnabled();

	void setEnabled(boolean enabled);

	/** This module's settings, declared once for the cards and the search index (REQ-UI-07). */
	List<Card> cards();

	/**
	 * Called once when the settings screen closes (REQ-UI-15). Its changes were applied to the config
	 * as they were made, so this only refreshes the module's section and rebuilds derived runtime state
	 * (e.g. a rule-matching index) from the config as it is now.
	 */
	default void onSettingsClosed() {
	}
}
