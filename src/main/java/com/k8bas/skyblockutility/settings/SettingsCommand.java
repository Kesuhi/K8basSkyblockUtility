package com.k8bas.skyblockutility.settings;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import java.util.ArrayList;
import java.util.List;

public final class SettingsCommand {
	private static final String BASE = "k8ba";

	private SettingsCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			for (String variant : casePermutations(BASE)) {
				dispatcher.register(ClientCommands.literal(variant).executes(context -> {
					var client = context.getSource().getClient();
					client.setScreen(SettingsScreenFactory.build(client.screen));
					return 1;
				}));
			}
		});
	}

	/** Brigadier literals match exact case; this covers "/k8ba" regardless of how it's cased. */
	private static List<String> casePermutations(String base) {
		List<String> results = new ArrayList<>();
		results.add("");
		for (char c : base.toCharArray()) {
			List<String> next = new ArrayList<>();
			for (String prefix : results) {
				if (Character.isLetter(c)) {
					next.add(prefix + Character.toLowerCase(c));
					next.add(prefix + Character.toUpperCase(c));
				} else {
					next.add(prefix + c);
				}
			}
			results = next;
		}
		return results;
	}
}
