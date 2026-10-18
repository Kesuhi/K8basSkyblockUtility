package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.settings.SettingsCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R52 (REQ-XC-RULES-02, AC-XC-13): every command the updater's chat links run parses to the end, through the `update`
 * node and not the settings search, with something to execute, in the `/ksu` tree exactly as it is registered. Fabric
 * runs a command that parses there on the client, so none of them can fall through to the server.
 */
class UpdateCommandTreeTest {
	private static CommandDispatcher<FabricClientCommandSource> tree(String root) {
		CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
		dispatcher.register(SettingsCommand.tree(root));
		return dispatcher;
	}

	private static List<String> path(ParseResults<FabricClientCommandSource> parse) {
		return parse.getContext().getNodes().stream().map(ParsedCommandNode::getNode).map(node -> node.getName()).toList();
	}

	@Test
	void everyClickCommandRunsOnTheUpdateNode() {
		for (String root : List.of("ksu", "kskyblockutility")) {
			CommandDispatcher<FabricClientCommandSource> dispatcher = tree(root);
			for (List<String> expected : List.of(List.of("update", "show"), List.of("update", "skip", "version"),
					List.of("update", "skip", "version", "confirm"), List.of("update", "install", "version"), List.of("update", "check"),
					List.of("update", "cancel"), List.of("update"), List.of("update", "install"), List.of("update", "skip"))) {
				String command = switch (String.join(" ", expected)) {
					case "update skip version" -> "update skip 99.0.0";
					case "update skip version confirm" -> "update skip 2.1.0-beta.1 confirm";
					case "update install version" -> "update install 99.0.0+26.2";
					default -> String.join(" ", expected);
				};
				ParseResults<FabricClientCommandSource> parse = dispatcher.parse(root + " " + command, null);
				assertFalse(parse.getReader().canRead(), root + " " + command + " parses to the end: " + parse.getExceptions());
				assertTrue(parse.getExceptions().isEmpty(), root + " " + command);
				List<String> nodes = path(parse);
				assertEquals(root, nodes.getFirst());
				assertEquals(expected, nodes.subList(1, nodes.size()), root + " " + command + " goes through the update node, not the search");
				assertNotNull(parse.getContext().build(root + " " + command).getCommand(), root + " " + command + " has something to run");
			}
		}
	}
}
