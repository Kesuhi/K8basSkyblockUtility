package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.highlight.HighlightManager;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Husk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;

/**
 * T1.11, AC-GLOW-13 [C]: a matched NPC behind a wall shows no "You found" title; in view it shows
 * one, once per server; a location change (the next run) shows it again (EC-GLOW-07); a title
 * allowed while a screen is open shows once the screen closes (EC-GLOW-08). R20: only the special
 * NPCs get it; a rule for another NPC still outlines it but shows no title.
 */
public class FoundTitleGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String FOUND = "You found Trinity";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			server.runCommand("summon minecraft:husk 0.5 -60 8.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:\"Trinity\"}");
			server.runCommand("fill -4 -60 4 4 -56 5 minecraft:stone");
			singleplayer.getConnection().waitForChunksRender();
			context.waitFor(client -> husk(client) != null, 100);

			NpcSearchModule module = context.computeOnClient(client -> ModuleManager.modules().stream()
					.filter(NpcSearchModule.class::isInstance).map(NpcSearchModule.class::cast).findFirst().orElseThrow());
			List<NpcRule> saved = context.computeOnClient(client -> module.rulesForTest());
			try {
				context.runOnClient(client -> {
					client.gui.hud.clearTitles();
					IslandTracker.forceIsland("Hub");
					NpcRule rule = new NpcRule();
					rule.label = "Trinity";
					rule.sourceId = "trinity";
					rule.fixed = false;
					rule.nameMatchMode = NameMatchMode.CONTAINS;
					rule.namePattern = "Trinity";
					rule.color = 0x55FF55;
					module.useRulesForTest(List.of(rule));
				});
				context.waitTicks(10);
				String behindWall = title(context);
				int matched = context.computeOnClient(client -> HighlightManager.getOutlineColorFromAny(husk(client)));

				server.runCommand("fill -4 -60 4 4 -56 5 minecraft:air");
				context.waitTicks(5);
				String inView = title(context);
				Integer inViewColour = titleColour(context);

				context.runOnClient(client -> client.gui.hud.clearTitles());
				context.waitTicks(10);
				String again = title(context);

				context.runOnClient(client -> IslandTracker.forceIsland("Dwarven Mines"));
				context.waitTicks(5);
				String nextRun = title(context);

				context.runOnClient(client -> {
					client.gui.hud.clearTitles();
					client.gui.setScreen(new ChatScreen("", false));
					IslandTracker.forceIsland("Hub");
				});
				context.waitTicks(5);
				String behindScreen = title(context);
				context.runOnClient(client -> client.gui.setScreen(null));
				context.waitTicks(2);
				String afterScreen = title(context);

				// R20: a hand-made rule for an NPC that is not special outlines it, but shows no title.
				context.runOnClient(client -> {
					client.gui.hud.clearTitles();
					NpcRule other = new NpcRule();
					other.label = "Mort";
					other.fixed = false;
					other.nameMatchMode = NameMatchMode.CONTAINS;
					other.namePattern = "Trinity";
					other.color = 0x55FF55;
					module.useRulesForTest(List.of(other));
					IslandTracker.forceIsland("Dwarven Mines");
				});
				context.waitTicks(10);
				String notSpecial = title(context);
				int otherMatched = context.computeOnClient(client -> HighlightManager.getOutlineColorFromAny(husk(client)));

				LOGGER.info("title: behind a wall {} (matched {}), in view {}, again {}, next run {}, behind a screen {}, after it {}, not special {} (matched {})",
						behindWall, Integer.toHexString(matched), inView, again, nextRun, behindScreen, afterScreen, notSpecial,
						Integer.toHexString(otherMatched));
				check(matched != 0, "the NPC behind the wall is matched");
				// R31: NPCs have no colour of their own; the rule's stored green shows nowhere.
				check(matched == 0xFFFFFFFF, "outlined in white, not the stored colour: " + Integer.toHexString(matched));
				check(inViewColour != null && inViewColour == 0xFFFFFF, "the title is white: " + inViewColour);
				check(behindWall == null, "no title while the NPC is behind a wall");
				check(FOUND.equals(inView), "the title shows once the NPC is in view");
				check(again == null, "the title shows once per server");
				check(FOUND.equals(nextRun), "a location change shows it again (EC-GLOW-07)");
				check(behindScreen == null, "no title while a screen is open");
				check(FOUND.equals(afterScreen), "the title shows after the screen closes (EC-GLOW-08)");
				check(otherMatched != 0, "a rule for another NPC still outlines it");
				check(notSpecial == null, "an NPC that is not special gets no title (R20)");
			} finally {
				context.runOnClient(client -> {
					client.gui.setScreen(null);
					client.gui.hud.clearTitles();
					module.useRulesForTest(saved);
					IslandTracker.forceIsland(null);
				});
				server.runCommand("kill @e[type=!minecraft:player]");
			}
		}
	}

	/** The RGB colour of the HUD title, or null when there is none or it has no colour. */
	private static Integer titleColour(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			try {
				Field field = Hud.class.getDeclaredField("title");
				field.setAccessible(true);
				Component title = (Component) field.get(client.gui.hud);
				return title == null || title.getStyle().getColor() == null ? null : title.getStyle().getColor().getValue();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("cannot read the HUD title", e);
			}
		});
	}

	private static String title(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			try {
				Field field = Hud.class.getDeclaredField("title");
				field.setAccessible(true);
				Component title = (Component) field.get(client.gui.hud);
				return title == null ? null : title.getString();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("cannot read the HUD title", e);
			}
		});
	}

	private static Husk husk(Minecraft client) {
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Husk husk) {
				return husk;
			}
		}
		return null;
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
