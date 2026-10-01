package com.k8bas.skyblockutility.highlight;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

/**
 * T1.10: the glow Render Chest draws for our rules, measured in screenshots. A matched zombie (a husk) glows
 * magenta in view and not behind a wall (AC-GLOW-02, AC-XC-02); an invisible one, also wearing a
 * helmet, never glows or triggers a match (AC-GLOW-03); server glow keeps its own colour
 * (AC-GLOW-04); black is a colour (AC-GLOW-17); players and the local player never glow, a
 * player-type NPC does (AC-GLOW-10).
 */
public class GlowGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final int MAGENTA = 0xFF00FF;
	private static final IntPredicate IS_MAGENTA = rgb -> red(rgb) >= 200 && green(rgb) <= 80 && blue(rgb) >= 200;
	private static final IntPredicate IS_WHITE = rgb -> red(rgb) >= 230 && green(rgb) >= 230 && blue(rgb) >= 230;
	private static final IntPredicate IS_BLACK = rgb -> red(rgb) <= 40 && green(rgb) <= 40 && blue(rgb) <= 40;
	private static final int PLAYER_ID = 1_000_000;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			// A husk is a zombie that does not burn in daylight, so no fire animation changes the shots.
			// Beyond reach, so the crosshair never targets it and its name is never drawn.
			server.runCommand("summon minecraft:husk 0.5 -60 8.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:\"Glow Target\"}");
			context.runOnClient(client -> {
				setHudHidden(client, true);
				client.options.cloudStatus().set(CloudStatus.OFF);
			});
			singleplayer.getConnection().waitForChunksRender();
			context.waitFor(client -> zombie(client) != null, 100);

			HighlightManager manager = new HighlightManager();
			AtomicInteger matches = new AtomicInteger();
			manager.setOnMatchListener(rule -> matches.incrementAndGet());
			try {
				useRule(context, manager, "Glow Target", MAGENTA);
				inViewAndBehindAWall(context, singleplayer, server, manager);
				invisible(context, server, matches);
				serverGlow(context, server, manager);
				black(context, manager);
				players(context, server, manager);
			} finally {
				context.runOnClient(client -> {
					manager.setEnabled(false);
					client.options.setCameraType(CameraType.FIRST_PERSON);
					setHudHidden(client, false);
					if (client.level.getEntity(PLAYER_ID) != null) {
						client.level.removeEntity(PLAYER_ID, Entity.RemovalReason.DISCARDED);
					}
				});
				server.runCommand("kill @e[type=!minecraft:player]");
			}
		}
	}

	/** AC-GLOW-02, AC-XC-02 (named zombie behind stone). */
	private static void inViewAndBehindAWall(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			TestServerContext server, HighlightManager manager) {
		context.runOnClient(client -> manager.setEnabled(false));
		context.waitTicks(3);
		int off = count(context.takeScreenshot("t1.10-glow-off"), IS_MAGENTA);
		context.runOnClient(client -> manager.setEnabled(true));
		context.waitTicks(3);
		int inView = count(context.takeScreenshot("t1.10-glow-in-view"), IS_MAGENTA);

		server.runCommand("fill -4 -60 4 4 -57 6 minecraft:stone");
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(3);
		int behindWall = count(context.takeScreenshot("t1.10-glow-behind-stone"), IS_MAGENTA);
		int cached = context.computeOnClient(client -> HighlightManager.getOutlineColorFromAny(zombie(client)));
		server.runCommand("fill -4 -60 4 4 -57 6 minecraft:air");
		singleplayer.getConnection().waitForChunksRender();

		LOGGER.info("magenta pixels: rule off {}, in view {}, behind 3 blocks of stone {} (cached colour {})",
				off, inView, behindWall, Integer.toHexString(cached));
		check(off == 0, "no magenta without the rule: " + off);
		check(inView > 50, "a matched zombie in view glows: " + inView);
		check(cached == 0xFFFF00FF, "the zombie behind the wall is still matched");
		check(behindWall == 0, "blocks hide the glow (depth test): " + behindWall);
	}

	/** AC-GLOW-03 [C], AC-XC-02 (invisible): also with a visible helmet; glow returns within 1 s. */
	private static void invisible(ClientGameTestContext context, TestServerContext server, AtomicInteger matches) {
		server.runCommand("item replace entity @e[type=minecraft:husk] armor.head with minecraft:diamond_helmet");
		server.runCommand("effect give @e[type=minecraft:husk] minecraft:invisibility infinite 0 true");
		context.waitFor(client -> zombie(client).isInvisible(), 40);
		context.waitTicks(3);
		context.runOnClient(client -> matches.set(0));
		context.waitTicks(5);
		int matchedWhileInvisible = matches.get();
		int invisible = count(context.takeScreenshot("t1.10-glow-invisible-helmet"), IS_MAGENTA);

		server.runCommand("effect clear @e[type=minecraft:husk] minecraft:invisibility");
		context.waitTicks(20);
		int visibleAgain = count(context.takeScreenshot("t1.10-glow-visible-again"), IS_MAGENTA);
		server.runCommand("item replace entity @e[type=minecraft:husk] armor.head with minecraft:air");

		LOGGER.info("invisible with a helmet: {} magenta pixels, {} matches; 1 s after the effect ends: {}",
				invisible, matchedWhileInvisible, visibleAgain);
		check(invisible == 0, "an invisible zombie with a helmet does not glow: " + invisible);
		check(matchedWhileInvisible == 0, "an invisible zombie triggers no match (so no title)");
		check(visibleAgain > 50, "glow is back within 1 s of the effect ending: " + visibleAgain);
	}

	/** AC-GLOW-04: the vanilla Glowing effect keeps its white outline; ours is not drawn over it. */
	private static void serverGlow(ClientGameTestContext context, TestServerContext server, HighlightManager manager) {
		context.runOnClient(client -> manager.setEnabled(false));
		context.waitTicks(3);
		Path plain = context.takeScreenshot("t1.10-server-glow-before");
		server.runCommand("effect give @e[type=minecraft:husk] minecraft:glowing infinite 0 true");
		context.waitFor(client -> zombie(client).isCurrentlyGlowing(), 40);
		context.waitTicks(3);
		Path vanilla = context.takeScreenshot("t1.10-server-glow-rule-off");
		context.runOnClient(client -> manager.setEnabled(true));
		context.waitTicks(3);
		Path withRule = context.takeScreenshot("t1.10-server-glow-rule-on");
		server.runCommand("effect clear @e[type=minecraft:husk] minecraft:glowing");
		context.waitFor(client -> !zombie(client).isCurrentlyGlowing(), 40);

		int white = countNew(vanilla, plain, IS_WHITE);
		int magenta = count(withRule, IS_MAGENTA);
		int whiteWithRule = countNew(withRule, plain, IS_WHITE);
		LOGGER.info("server glow: {} new white pixels; with our rule on {} white, {} magenta", white, whiteWithRule, magenta);
		check(white > 50, "the vanilla Glowing outline is white: " + white);
		check(magenta == 0, "our colour is not drawn on a server-glowing entity: " + magenta);
		check(Math.abs(whiteWithRule - white) <= white / 10, "the white outline stays as the server sets it");
	}

	/** AC-GLOW-17 [C]: colour 0x000000 draws a black outline. */
	private static void black(ClientGameTestContext context, HighlightManager manager) {
		useRule(context, manager, "Glow Target", 0x000000);
		context.runOnClient(client -> manager.setEnabled(false));
		context.waitTicks(3);
		Path off = context.takeScreenshot("t1.10-black-off");
		context.runOnClient(client -> manager.setEnabled(true));
		context.waitTicks(3);
		Path on = context.takeScreenshot("t1.10-black-on");
		int black = countNew(on, off, IS_BLACK);
		LOGGER.info("black rule: {} new black pixels", black);
		check(black > 50, "a black rule draws a black outline: " + black);
	}

	/** AC-GLOW-10 [C]: next to a matched name tag, a real player and the local player (third person)
	 *  never glow; a player-type NPC (non-version-4 UUID) does. */
	private static void players(ClientGameTestContext context, TestServerContext server, HighlightManager manager) {
		useRule(context, manager, "NPC Name", MAGENTA);
		server.runCommand("summon minecraft:armor_stand 3.5 -58 8.5 {Invisible:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:\"NPC Name\"}");
		server.runCommand("summon minecraft:armor_stand 0.5 -58 0.5 {Invisible:1b,NoGravity:1b,CustomNameVisible:1b,CustomName:\"NPC Name\"}");
		context.waitTicks(5);

		UUID realPlayer = new UUID(0x1b2c3d4e5f60_4a7bL, 0x8c9daebfc0d1e2f3L);
		UUID npc = new UUID(0x1b2c3d4e5f60_2a7bL, 0x8c9daebfc0d1e2f3L);
		check(realPlayer.version() == 4 && npc.version() == 2, "test UUID versions");

		addPlayer(context, realPlayer);
		context.waitTicks(3);
		int playerColour = context.computeOnClient(client -> HighlightManager.getOutlineColorFromAny(client.level.getEntity(PLAYER_ID)));
		int playerPixels = count(context.takeScreenshot("t1.10-real-player"), IS_MAGENTA);
		context.runOnClient(client -> client.level.removeEntity(PLAYER_ID, Entity.RemovalReason.DISCARDED));

		addPlayer(context, npc);
		context.waitTicks(3);
		int npcColour = context.computeOnClient(client -> HighlightManager.getOutlineColorFromAny(client.level.getEntity(PLAYER_ID)));
		int npcPixels = count(context.takeScreenshot("t1.10-player-npc"), IS_MAGENTA);
		context.runOnClient(client -> client.level.removeEntity(PLAYER_ID, Entity.RemovalReason.DISCARDED));

		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(5);
		int localColour = context.computeOnClient(client -> HighlightManager.getOutlineColorFromAny(client.player));
		int localPixels = count(context.takeScreenshot("t1.10-local-player-third-person"), IS_MAGENTA);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		LOGGER.info("next to a matched tag: real player {} ({} px), player-type NPC {} ({} px), local player {} ({} px)",
				Integer.toHexString(playerColour), playerPixels, Integer.toHexString(npcColour), npcPixels,
				Integer.toHexString(localColour), localPixels);
		check(playerColour == 0 && playerPixels == 0, "a real player never matches a name-tag rule");
		check(npcColour == 0xFFFF00FF && npcPixels > 50, "a player-type NPC still matches");
		check(localColour == 0 && localPixels == 0, "the local player never glows, also in third person");
	}

	private static void addPlayer(ClientGameTestContext context, UUID uuid) {
		context.runOnClient(client -> {
			RemotePlayer player = new RemotePlayer(client.level, new GameProfile(uuid, "Tester"));
			player.setId(PLAYER_ID);
			player.snapTo(3.5, -60, 8.5, 180, 0);
			client.level.addEntity(player);
		});
	}

	private static void useRule(ClientGameTestContext context, HighlightManager manager, String name, int colour) {
		context.runOnClient(client -> {
			HighlightRule rule = new HighlightRule();
			rule.nameMatchMode = NameMatchMode.CONTAINS;
			rule.namePattern = name;
			rule.color = colour;
			manager.rebuild(List.of(rule));
		});
	}

	private static Zombie zombie(Minecraft client) {
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Zombie zombie) {
				return zombie;
			}
		}
		return null;
	}

	private static int count(Path shot, IntPredicate colour) {
		BufferedImage image = read(shot);
		int n = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (colour.test(image.getRGB(x, y))) {
					n++;
				}
			}
		}
		return n;
	}

	/** Pixels that have the colour in one shot and not in the other. */
	private static int countNew(Path with, Path without, IntPredicate colour) {
		BufferedImage a = read(with);
		BufferedImage b = read(without);
		int n = 0;
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				if (colour.test(a.getRGB(x, y)) && !colour.test(b.getRGB(x, y))) {
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

	private static int red(int rgb) {
		return (rgb >> 16) & 0xFF;
	}

	private static int green(int rgb) {
		return (rgb >> 8) & 0xFF;
	}

	private static int blue(int rgb) {
		return rgb & 0xFF;
	}

	private static void setHudHidden(Minecraft client, boolean hidden) {
		if (client.gui.hud.isHidden() != hidden) {
			client.gui.hud.toggle();
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
