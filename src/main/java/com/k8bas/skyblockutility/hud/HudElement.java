package com.k8bas.skyblockutility.hud;

/**
 * One on-screen HUD element (REQ-HUD-01). The framework draws it at its stored position and scale while
 * its feature is on and it has content; with no content it draws nothing, never an empty box. Every
 * method is called on the draw path, so each only returns state worked out elsewhere (REQ-HUD-11).
 */
public interface HudElement {
	/** Stable, used as the key of its position in the config. */
	String id();

	/** Its name in the HUD editor. */
	String displayName();

	/** Whether its feature is on. */
	boolean enabled();

	/** What it shows now, or null for nothing (not on SkyBlock, no data yet: EC-HUD-09). */
	HudContent content();

	/** What the HUD editor shows when there is no live content. */
	HudContent preview();

	/** Where it starts, and where Reset puts it. */
	HudPosition defaultPosition();
}
