package com.k8bas.skyblockutility.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Every chat message this mod sends goes through here, so they all read consistently — modeled
 * on SkyHanni's own ChatUtils.chat(): a bracketed, distinctly-colored prefix followed by the
 * message body in its own default color, rather than every call site building its own Component.
 */
public final class ChatUtils {
	private static final String PREFIX = "[KSU] ";

	/** True while a line of this mod is being shown; the client thread only. */
	private static boolean sending;

	private ChatUtils() {
	}

	/** True while this mod is showing one of its own chat lines, so the game-state readers skip it (REQ-GS-04). */
	public static boolean isSending() {
		return sending;
	}

	/** Must be called from the main/render thread — same requirement as sendSystemMessage itself.
	 *  A caller on a background thread (e.g. a virtual thread doing network I/O) needs to hop
	 *  back via Minecraft.getInstance().execute(...) first. */
	public static void chat(String message) {
		chat(Component.literal(message).withStyle(ChatFormatting.YELLOW));
	}

	/** The same, for a message with its own styling (e.g. clickable links). Local only. */
	public static void chat(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		MutableComponent text = Component.literal(PREFIX).withStyle(ChatFormatting.GOLD).append(message);
		// The client shows it through the same message events as a server line.
		sending = true;
		try {
			client.player.sendSystemMessage(text);
		} finally {
			sending = false;
		}
	}
}
