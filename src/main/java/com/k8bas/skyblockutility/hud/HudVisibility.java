package com.k8bas.skyblockutility.hud;

/** When HUD elements are drawn in game (REQ-HUD-02, R12). Pure. */
public final class HudVisibility {
	/** The screen open over the game. */
	public enum Screen { NONE, CHAT, OTHER }

	private HudVisibility() {
	}

	/** Hidden with F1, while the editor is open (it draws them itself), and under any screen but chat. */
	public static boolean drawn(boolean hudHidden, Screen screen, boolean editorOpen) {
		return !hudHidden && !editorOpen && screen != Screen.OTHER;
	}
}
