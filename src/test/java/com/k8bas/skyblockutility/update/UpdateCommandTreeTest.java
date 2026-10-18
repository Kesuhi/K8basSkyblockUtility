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

	/** A command source that answers only {@code attended()}: true for a typed command, false for a chat click's check. */
	private static FabricClientCommandSource source(boolean attended) {
		return (FabricClientCommandSource) java.lang.reflect.Proxy.newProxyInstance(UpdateCommandTreeTest.class.getClassLoader(),
				new Class<?>[] {FabricClientCommandSource.class}, (proxy, method, args) -> switch (method.getName()) {
					case "attended" -> attended;
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					case "toString" -> attended ? "attended" : "clicked";
					default -> null;
				});
	}

	/** Fabric's test of a command it would run: parsed to the end, no errors, something to execute. */
	private static boolean runs(ParseResults<FabricClientCommandSource> parse) {
		return !parse.getReader().canRead() && parse.getExceptions().isEmpty() && parse.getContext().getCommand() != null;
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
				ParseResults<FabricClientCommandSource> parse = dispatcher.parse(root + " " + command, source(true));
				assertFalse(parse.getReader().canRead(), root + " " + command + " parses to the end: " + parse.getExceptions());
				assertTrue(parse.getExceptions().isEmpty(), root + " " + command);
				List<String> nodes = path(parse);
				assertEquals(root, nodes.getFirst());
				assertEquals(expected, nodes.subList(1, nodes.size()), root + " " + command + " goes through the update node, not the search");
				assertNotNull(parse.getContext().build(root + " " + command).getCommand(), root + " " + command + " has something to run");
			}
		}
	}

	/**
	 * R55: Fabric parses a clicked command again with a source that is not attended and asks in its confirmation window
	 * when only the full source parses it. A server's click on the skip's confirm or on cancel therefore always asks
	 * first; typed, both run. The other links run from a click as before (show opens the screen; skip only asks).
	 */
	@Test
	void theSkipConfirmAndCancelAskFirstWhenClicked() {
		for (String root : List.of("ksu", "kskyblockutility")) {
			CommandDispatcher<FabricClientCommandSource> dispatcher = tree(root);
			for (String command : List.of("update skip 2.1.0 confirm", "update cancel")) {
				assertTrue(runs(dispatcher.parse(root + " " + command, source(true))), root + " " + command + " typed");
				assertFalse(runs(dispatcher.parse(root + " " + command, source(false))), root + " " + command + " clicked asks first");
			}
			for (String command : List.of("update", "update show", "update skip 2.1.0", "update install 2.1.0", "update check")) {
				assertTrue(runs(dispatcher.parse(root + " " + command, source(false))), root + " " + command + " runs from a click");
			}
		}
	}
}
