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

	private ChatUtils() {
	}

	/** Must be called from the main/render thread — same requirement as sendSystemMessage itself.
	 *  A caller on a background thread (e.g. a virtual thread doing network I/O) needs to hop
	 *  back via Minecraft.getInstance().execute(...) first. */
	public static void chat(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		MutableComponent text = Component.literal(PREFIX).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(message).withStyle(ChatFormatting.YELLOW));
		client.player.sendSystemMessage(text);
	}
}
