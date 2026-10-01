package com.k8bas.skyblockutility.highlight;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Husk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Highlight matching in a world (in the highlight package, so it can read the package-private test
 * counters). T1.10a: matching runs once per tick on the client thread and resolves each name tag at
 * most once per entity per tick (REQ-GLOW-06, EC-GLOW-05); the first-registered instance's colour
 * wins (REQ-GLOW-08).
 */
public class HighlightGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final int HUSKS = 210;
	private static final int RULES = 25;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			for (int i = 0; i < HUSKS; i++) {
				int x = (i % 15) * 2 - 14;
				int z = (i / 15) * 2 + 4;
				server.runCommand("summon minecraft:husk " + x + ".5 -60 " + z + ".5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
			}
			// One tagged husk: a displayed name stand above the husk at (0.5, -60, 4.5).
			server.runCommand("summon minecraft:armor_stand 0.5 -58 4.5 {Invisible:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:\"Tag 3\"}");
			singleplayer.getConnection().waitForChunksRender();
			context.waitFor(client -> StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
					.filter(entity -> entity instanceof Husk).count() >= HUSKS, 200);

			HighlightManager first = new HighlightManager();
			HighlightManager second = new HighlightManager();
			java.util.concurrent.atomic.AtomicInteger secondMatches = new java.util.concurrent.atomic.AtomicInteger();
			second.setOnMatchListener((rule, entity) -> secondMatches.incrementAndGet());
			try {
				context.runOnClient(client -> {
					List<HighlightRule> rules = new ArrayList<>();
					for (int i = 0; i < RULES; i++) {
						HighlightRule rule = new HighlightRule();
						rule.nameMatchMode = NameMatchMode.CONTAINS;
						rule.namePattern = "Tag " + i;
						rule.color = 0xFF00FF;
						rules.add(rule);
					}
					first.rebuild(rules);
					HighlightRule blue = new HighlightRule();
					blue.nameMatchMode = NameMatchMode.CONTAINS;
					blue.namePattern = "Tag 3";
					blue.color = 0x0000FF;
					second.rebuild(List.of(blue));
				});
				context.waitTicks(2);

				int ticks = 10;
				context.runOnClient(client -> {
					HighlightManager.nameTagLookups = 0;
					HighlightManager.offThreadQueries = 0;
				});
				context.waitTicks(ticks);
				int lookups = context.computeOnClient(client -> HighlightManager.nameTagLookups);
				int offThread = context.computeOnClient(client -> HighlightManager.offThreadQueries);
				long entities = context.computeOnClient(client ->
						StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false).count());
				LOGGER.info("{} name-tag lookups in {} ticks for {} entities and {} rules; {} off the client thread",
						lookups, ticks, entities, RULES + 1, offThread);
				check(lookups <= ticks * entities, "at most one name-tag lookup per entity per tick (EC-GLOW-05)");
				check(lookups > 0, "matching ran");
				check(offThread == 0, "every name-tag query ran on the client thread (AC-GLOW-05)");

				List<Integer> colours = context.computeOnClient(client -> {
					List<Integer> found = new ArrayList<>();
					for (Entity entity : client.level.entitiesForRendering()) {
						if (entity instanceof Husk) {
							int colour = HighlightManager.getOutlineColorFromAny(entity);
							if (colour != 0) {
								found.add(colour);
							}
						}
					}
					return found;
				});
				check(colours.size() == 1, "exactly the tagged husk is matched: " + colours.size());
				check(colours.get(0) == 0xFFFF00FF, "the first-registered instance's colour wins (REQ-GLOW-08)");
				// Review S-5 (G1): the second instance still sees its match (NPC Search's title).
				check(secondMatches.get() > 0, "the second instance's match listener runs too: " + secondMatches.get());
			} finally {
				context.runOnClient(client -> {
					first.setEnabled(false);
					second.setEnabled(false);
				});
				server.runCommand("kill @e[type=!minecraft:player]");
			}
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
