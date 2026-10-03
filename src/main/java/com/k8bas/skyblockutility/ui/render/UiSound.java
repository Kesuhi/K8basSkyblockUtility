package com.k8bas.skyblockutility.ui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

/** The vanilla UI click for buttons, toggles and tabs (REQ-UI-17). */
public final class UiSound {
	private UiSound() {
	}

	public static void click() {
		AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
	}
}
