package com.k8bas.skyblockutility.hud;

import com.k8bas.skyblockutility.module.bestiary.BestiaryHud;
import com.k8bas.skyblockutility.module.corpsespots.CorpseDingHud;

import java.util.List;

/**
 * The HUD elements the mod ships, registered at startup. A feature that adds one adds it here, so the
 * default layout check (HudLayoutTest, REQ-HUD-13) covers it (EC-HUD-10): the corpse ding (T3.3d), the bestiary
 * panel (T5.3); later the SkyBlock XP HUD. The corpse ding stays first: gametests take the first element.
 */
public final class ShippedHud {
	private ShippedHud() {
	}

	public static List<HudElement> elements() {
		return List.of(CorpseDingHud.INSTANCE, BestiaryHud.INSTANCE);
	}

	public static void register() {
		elements().forEach(HudRegistry::register);
	}
}
