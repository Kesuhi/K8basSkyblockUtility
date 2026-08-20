package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

public final class SettingsCommand {
	private SettingsCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommands.literal("k8ba").executes(context -> {
					K8basSkyblockUtilityClient.LOGGER.info("/k8ba executed, opening settings screen");
					var client = context.getSource().getClient();
					client.setScreen(SettingsScreenFactory.build(client.screen));
					return 1;
				})));
	}
}
