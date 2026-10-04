package com.k8bas.skyblockutility.ui.screen;

import net.minecraft.client.gui.screens.Screen;

/** For gametests in other packages: what the settings screen shows. Call on the client thread. */
public final class ConfigScreensForTests {
	private ConfigScreensForTests() {
	}

	/** The query in the screen's search box, as the search sees it; null when the screen is not the settings screen. */
	public static String query(Screen screen) {
		return screen instanceof ConfigScreen config ? config.query() : null;
	}
}
