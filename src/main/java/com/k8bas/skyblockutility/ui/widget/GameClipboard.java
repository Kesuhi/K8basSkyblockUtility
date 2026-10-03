package com.k8bas.skyblockutility.ui.widget;

import net.minecraft.client.Minecraft;

/** The system clipboard, through the game, for text fields. */
public final class GameClipboard implements TextEditModel.Clipboard {
	public static final GameClipboard INSTANCE = new GameClipboard();

	private GameClipboard() {
	}

	@Override
	public String get() {
		return Minecraft.getInstance().keyboardHandler.getClipboard();
	}

	@Override
	public void set(String text) {
		Minecraft.getInstance().keyboardHandler.setClipboard(text);
	}
}
