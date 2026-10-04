package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.ui.screen.ConfigScreen;
import com.k8bas.skyblockutility.ui.screen.HudEditorScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/**
 * `/ksu` and `/kskyblockutility` open the settings screen; `/ksu <text>` opens it with the text in the
 * search box, unless the text starts with a reserved subcommand word (REQ-UI-10, {@link CommandWords});
 * `/ksu hud` opens the HUD editor (REQ-HUD-06).
 * Client-side only: nothing is sent to the server (REQ-UI-25).
 */
public final class SettingsCommand {
	private static final String[] NAMES = {"ksu", "kskyblockutility"};

	private SettingsCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			K8basSkyblockUtilityClient.LOGGER.debug("Registering settings commands: {}", Arrays.toString(NAMES));
			for (String name : NAMES) {
				dispatcher.register(ClientCommands.literal(name)
						.executes(context -> open(context.getSource().getClient(), ""))
						.then(ClientCommands.literal("hud").executes(context -> openHudEditor(context.getSource().getClient())))
						// A registered subcommand's own literal wins over this; a reserved word gets here only
						// when its subcommand is not there (yet, or typed in another case).
						.then(ClientCommands.argument("search", StringArgumentType.greedyString()).executes(context -> {
							String text = StringArgumentType.getString(context, "search");
							String word = CommandWords.reserved(text);
							if (word != null) {
								boolean exists = context.getRootNode().getChild(name).getChild(word) != null;
								context.getSource().sendError(Component.literal(exists ? "Subcommands are lower case: /" + name + " " + word
										: "Unknown /" + name + " subcommand: " + text.strip().split("\s+", 2)[0]));
								return 0;
							}
							return open(context.getSource().getClient(), text);
						})));
			}
		});
	}

	private static int openHudEditor(Minecraft client) {
		// Next frame, as for the settings (EC-UI-03).
		client.schedule(() -> client.gui.setScreen(new HudEditorScreen(client.gui.screen())));
		return 1;
	}

	private static int open(Minecraft client, String search) {
		// Queued for the next frame: the chat closes itself after running the command, which would close a
		// screen opened now (EC-UI-03). execute() would run it at once, as this is the render thread.
		client.schedule(() -> client.gui.setScreen(new ConfigScreen(client.gui.screen(), search)));
		return 1;
	}
}
