package com.k8bas.skyblockutility.highlight;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcRulesForTests;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchModule;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * Tier C check of the dungeon NPC glow (T3.1, AC-GLOW-11 [C], AC-GLOW-12 [B]): three player-type stand-ins with a
 * name line 2.0 above the feet and a "CLICK" line 1.7 above (SPEC's layout for Tomioka, where the label is nearer;
 * UNVERIFIED on Hypixel, whose captured NPCs have the name at the feet), the island forced to "Catacombs", and NPC
 * Search rules shaped like the user's (moving, "Catacombs", CONTAINS). Each resolves to its name and glows in view,
 * none glows behind a wall, and switching one rule off stops only that NPC's glow.
 */
public class DungeonNpcGlowGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String[] NAMES = {"Trinity", "Tomioka", "Duncan"};
	private static final double[] X = {-2.5, 0.5, 3.5};
	private static final int FIRST_ID = 2_000_000;
	/** NPC Search draws moving NPCs white (R31). */
	private static final IntPredicate IS_WHITE = rgb -> ((rgb >> 16) & 0xFF) >= 230 && ((rgb >> 8) & 0xFF) >= 230 && (rgb & 0xFF) >= 230;

	@Override
	public void runTest(ClientGameTestContext context) {
		List<NpcRule> before = context.computeOnClient(client -> NpcRulesForTests.rules());
		boolean wasEnabled = context.computeOnClient(client -> module().isEnabled());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			for (int i = 0; i < NAMES.length; i++) {
				// The name line 2.0 above the feet and the CLICK line nearer, 1.7 above (AC-GLOW-08's case).
				server.runCommand(String.format("summon minecraft:armor_stand %s -58 8.5 {Invisible:1b,NoGravity:1b,Marker:1b,CustomNameVisible:1b,CustomName:\"%s\"}",
						X[i], NAMES[i]));
				server.runCommand(String.format("summon minecraft:armor_stand %s -58.3 8.5 {Invisible:1b,NoGravity:1b,Marker:1b,CustomNameVisible:1b,CustomName:\"CLICK\"}",
						X[i]));
			}
			context.runOnClient(client -> {
				client.options.cloudStatus().set(CloudStatus.OFF);
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
				IslandTracker.forceIsland("Catacombs");
				module().setEnabled(true);
				NpcRulesForTests.use(List.of());
				for (int i = 0; i < NAMES.length; i++) {
					// Hypixel's NPCs are player entities with a non-version-4 UUID (AC-GLOW-10).
					RemotePlayer npc = new RemotePlayer(client.level, new GameProfile(new UUID(0x1b2c3d4e5f60_2a7bL + i, 0x8c9daebfc0d1e2f3L), NAMES[i]));
					npc.setId(FIRST_ID + i);
					npc.snapTo(X[i], -60, 8.5, 180, 0);
					client.level.addEntity(npc);
				}
			});
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(10);

			List<String> resolved = context.computeOnClient(client -> {
				List<String> names = new ArrayList<>();
				for (int i = 0; i < NAMES.length; i++) {
					names.add(HighlightManager.resolveNameTag(client.level.getEntity(FIRST_ID + i)));
				}
				return names;
			});
			check(resolved.equals(List.of(NAMES)), "each stand-in resolves to its name, not CLICK: " + resolved);

			Path off = context.takeScreenshot("t3.1-dungeon-npcs-no-rules");
			context.runOnClient(client -> NpcRulesForTests.use(List.of(rule("trinity", "Trinity"), rule("tomioka", "Tomioka"), rule("duncan", "Duncan"))));
			context.waitTicks(5);
			int[] colours = colours(context);
			int glowing = countNew(context.takeScreenshot("t3.1-dungeon-npcs-glow"), off);
			LOGGER.info("dungeon NPC glow: colours {} {} {}, {} new white pixels", Integer.toHexString(colours[0]), Integer.toHexString(colours[1]),
					Integer.toHexString(colours[2]), glowing);
			check(colours[0] != 0 && colours[1] != 0 && colours[2] != 0, "all three are matched in Catacombs");
			check(glowing > 150, "all three glow in view: " + glowing);

			// Behind a wall: matched, but blocks hide the glow (depth test).
			server.runCommand("fill -6 -60 4 6 -56 6 minecraft:stone");
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(client -> NpcRulesForTests.use(List.of()));
			context.waitTicks(5);
			Path wallOff = context.takeScreenshot("t3.1-dungeon-npcs-wall-no-rules");
			context.runOnClient(client -> NpcRulesForTests.use(List.of(rule("trinity", "Trinity"), rule("tomioka", "Tomioka"), rule("duncan", "Duncan"))));
			context.waitTicks(5);
			int behindWall = countNew(context.takeScreenshot("t3.1-dungeon-npcs-wall"), wallOff);
			check(behindWall == 0, "no glow behind the wall: " + behindWall);
			server.runCommand("fill -6 -60 4 6 -56 6 minecraft:air");
			singleplayer.getConnection().waitForChunksRender();

			// AC-GLOW-12 [B]: switching Tomioka's rule off stops only Tomioka's glow.
			context.runOnClient(client -> {
				NpcRule tomiokaOff = rule("tomioka", "Tomioka");
				tomiokaOff.enabled = false;
				NpcRulesForTests.use(List.of(rule("trinity", "Trinity"), tomiokaOff, rule("duncan", "Duncan")));
			});
			context.waitTicks(5);
			int[] after = colours(context);
			check(after[0] != 0 && after[1] == 0 && after[2] != 0, "only Tomioka stops glowing: " + after[0] + " " + after[1] + " " + after[2]);
			LOGGER.info("dungeon NPC glow: behind a wall {} new white pixels; Tomioka's rule off stops only Tomioka", behindWall);
		} finally {
			context.runOnClient(client -> {
				IslandTracker.forceIsland(null);
				NpcRulesForTests.use(before);
				module().setEnabled(wasEnabled);
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
		}
	}

	/** A moving-NPC rule as the user's 1.0.1 config has it: island "Catacombs", CONTAINS the name. */
	private static NpcRule rule(String id, String name) {
		NpcRule rule = new NpcRule();
		rule.id = id;
		rule.label = name;
		rule.namePattern = name;
		rule.island = "Catacombs";
		rule.fixed = false;
		rule.enabled = true;
		return rule;
	}

	private static int[] colours(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			int[] colours = new int[NAMES.length];
			for (int i = 0; i < NAMES.length; i++) {
				Entity npc = client.level.getEntity(FIRST_ID + i);
				colours[i] = HighlightManager.getOutlineColorFromAny(npc);
			}
			return colours;
		});
	}

	private static NpcSearchModule module() {
		return ModuleManager.modules().stream().filter(module -> module instanceof NpcSearchModule).map(module -> (NpcSearchModule) module)
				.findFirst().orElseThrow();
	}

	/** White pixels in one shot that are not white in the other. */
	private static int countNew(Path with, Path without) {
		BufferedImage a = read(with);
		BufferedImage b = read(without);
		int n = 0;
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				if (IS_WHITE.test(a.getRGB(x, y)) && !IS_WHITE.test(b.getRGB(x, y))) {
					n++;
				}
			}
		}
		return n;
	}

	private static BufferedImage read(Path shot) {
		try {
			return ImageIO.read(shot.toFile());
		} catch (IOException e) {
			throw new AssertionError("cannot read " + shot, e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
