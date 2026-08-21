package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import java.util.Arrays;

public final class SettingsCommand {
	private static final String[] NAMES = {"ksu", "kskyblockutility"};

	private SettingsCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			K8basSkyblockUtilityClient.LOGGER.debug("Registering settings commands: {}", Arrays.toString(NAMES));
			for (String name : NAMES) {
				dispatcher.register(ClientCommands.literal(name).executes(context -> {
					K8basSkyblockUtilityClient.LOGGER.debug("/{} executed, opening settings screen", name);
					var client = context.getSource().getClient();
					// Deferred to next tick: the chat input's own Enter keystroke can otherwise
					// leak into the freshly opened screen and close it again immediately.
					client.execute(() -> client.setScreen(SettingsScreenFactory.build(client.screen)));
					return 1;
				}));
			}
		});
	}
}
