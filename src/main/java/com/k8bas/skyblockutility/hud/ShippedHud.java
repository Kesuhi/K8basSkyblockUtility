package com.k8bas.skyblockutility.hud;

import java.util.List;

/**
 * The HUD elements the mod ships, registered at startup. A feature that adds one adds it here, so the
 * default layout check (HudLayoutTest, REQ-HUD-13) covers it (EC-HUD-10). None yet: the first come with
 * the odds panels and the SkyBlock XP HUD.
 */
public final class ShippedHud {
	private ShippedHud() {
	}

	public static List<HudElement> elements() {
		return List.of();
	}

	public static void register() {
		elements().forEach(HudRegistry::register);
	}
}
