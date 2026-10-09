package com.k8bas.skyblockutility.ui.widget

import net.minecraft.client.Minecraft

/** The system clipboard, through the game, for text fields. Java reaches it as `GameClipboard.INSTANCE`. */
object GameClipboard : TextEditModel.Clipboard {
	override fun get(): String = Minecraft.getInstance().keyboardHandler.clipboard

	override fun set(text: String) {
		Minecraft.getInstance().keyboardHandler.clipboard = text
	}
}
