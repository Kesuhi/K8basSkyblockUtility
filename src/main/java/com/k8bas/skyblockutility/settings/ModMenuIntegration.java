package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.ui.screen.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's configure button opens the settings screen; closing it returns to Mod Menu (REQ-UI-10). Loaded only by Mod Menu. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
