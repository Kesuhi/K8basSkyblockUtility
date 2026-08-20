package com.k8bas.skyblockutility.module;

import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

/**
 * A self-contained feature. Fabric API events and mixins can't be unregistered once
 * registered (verified: net.fabricmc.fabric.api.event.Event exposes only register()),
 * so onRegister() is a one-time bootstrap step, not something re-run per enable/disable —
 * modules gate their own behavior on isEnabled() internally (see HighlightManager) instead
 * of having listeners added/removed at toggle time.
 */
public interface Module {
	/** Stable id, used as the config-section key. e.g. "mob_highlighter". */
	String id();

	/** Display name, used as the Cloth Config category title. */
	String displayName();

	/** Called once at mod bootstrap: load config, register listeners/keybinds/mixin-backed managers. */
	void onRegister();

	boolean isEnabled();

	void setEnabled(boolean enabled);

	/** Add this module's entries to its own settings category. */
	void buildConfigScreen(ConfigCategory category, ConfigEntryBuilder entryBuilder);

	/** Called once when the settings screen's Save button fires, before ConfigManager.save()
	 *  writes the file — override to reconcile pending add/delete actions collected during
	 *  buildConfigScreen (so they're included in that write) and rebuild any derived runtime
	 *  state (e.g. a rule-matching index) from the now-final config. */
	default void onConfigScreenSaved() {
	}
}
