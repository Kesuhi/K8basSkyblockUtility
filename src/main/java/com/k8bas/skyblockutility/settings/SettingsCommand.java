package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

public final class SettingsCommand {
	private static final String[] NAMES = {"ksu", "kskyblockutility"};

	private SettingsCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			K8basSkyblockUtilityClient.LOGGER.info("Registering settings commands: {}", (Object) NAMES);
			for (String name : NAMES) {
				dispatcher.register(ClientCommands.literal(name).executes(context -> {
					K8basSkyblockUtilityClient.LOGGER.info("/{} executed, opening settings screen", name);
					var client = context.getSource().getClient();
					client.setScreen(SettingsScreenFactory.build(client.screen));
					return 1;
				}));
			}
			K8basSkyblockUtilityClient.LOGGER.info("Settings commands registered");
		});
	}
}
