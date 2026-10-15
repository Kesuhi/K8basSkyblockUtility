package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.gametest.TestWaypoints;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchModule;
import com.k8bas.skyblockutility.module.npcsearch.NpcWaypointMarkers;
import com.k8bas.skyblockutility.module.npcsearch.WaypointColors;
import com.k8bas.skyblockutility.render.marker.WorldMarkers;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * T3.4b, the beam colour settings in a real world and the real settings screen (AC-NPCWP-07 [C]): the Crimson Isle
 * swatch, set to green through the colour picker, reaches the waypoint markers on the tick of Save, and the beam in
 * the world is green right after the screen closes (well within 1 s). A fixed rule's own beam colour, set through its
 * card, wins; "Use island colour" drops it. Screenshots of the Waypoints category with the island swatches and the
 * rule's beam colour at GUI scale 2 in the 854x480 window.
 */
public class BeamColorSettingsGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String ISLAND = "Crimson Isle";
	private static final String SWATCH = "npc_search.island_color.crimson_isle";
	private static final int GREEN = 0x22DD22;
	private static final int ORANGE = 0xFFAA00;

	@Override
	public void runTest(ClientGameTestContext context) {
		NpcSearchModule module = context.computeOnClient(client -> ModuleManager.modules().stream()
				.filter(NpcSearchModule.class::isInstance).map(NpcSearchModule.class::cast).findFirst().orElseThrow());
		List<NpcRule> saved = context.computeOnClient(client -> module.rulesForTest());
		int guiScale = context.computeOnClient(client -> client.options.guiScale().get());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			NpcRule udel = TestWaypoints.fixedRule("Udel", ISLAND, 0x00AAFF, 0, -60, 20);
			context.runOnClient(client -> {
				WorldMarkers.setHideLabelsWithHud(false);
				client.options.cloudStatus().set(CloudStatus.OFF);
				IslandTracker.forceIsland(ISLAND);
				module.useRulesForTest(List.of(udel));
			});
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(10);
			Beam before = Beam.of(world(context, "t3.4b-beam-before"));
			check(before.red > 20 && before.green == 0, "the beam starts in the Crimson Isle colour (red): " + before);

			context.runOnClient(client -> client.options.guiScale().set(2));
			context.setScreen(() -> new ConfigScreen(null));
			context.waitForScreen(ConfigScreen.class);
			context.runOnClient(client -> screen(client).select(Category.WAYPOINTS));
			context.waitTicks(2);
			RuleCardsGameTest.pickColour(context, SWATCH, "#22DD22");
			check(context.computeOnClient(client -> NpcWaypointMarkers.settings().islandColors().get(ISLAND)) == GREEN,
					"Save hands the colour to the waypoint markers at once");
			context.runOnClient(client -> ConfigManager.flush());
			check(RuleCardsGameTest.configFile(context).replaceAll("\\s", "").contains("\"islandBeamColors\":{\"CrimsonIsle\":" + GREEN + "}"),
					"and Save writes it to the file at once, with the screen still open (a discrete commit)");
			context.waitTicks(2);
			context.takeScreenshot("t3.4b-island-swatches");

			// The rule's own beam colour: its card, expanded, then the picker.
			context.runOnClient(client -> screen(client).toggleRule(screen(client).page().page().rows().stream()
					.filter(row -> row instanceof ConfigLayout.RuleHeader header && header.rule().id().endsWith(udel.id))
					.map(row -> ((ConfigLayout.RuleHeader) row).rule()).findFirst().orElseThrow()));
			context.waitTicks(2);
			String beamField = "npc_search.rule." + udel.id + ".beam_color";
			RuleCardsGameTest.pickColour(context, beamField, "#FFAA00");
			check(Integer.valueOf(ORANGE).equals(udel.beamColor), "the rule's own beam colour is stored: " + udel.beamColor);
			RuleCardsGameTest.controlCentre(context, beamField);
			context.waitTicks(2);
			context.takeScreenshot("t3.4b-rule-beam-override");
			check(context.computeOnClient(client -> client.getWindow().getWidth() == 854 && client.getWindow().getHeight() == 480
					&& client.getWindow().getGuiScale() == 2), "at GUI scale 2 in the 854x480 window");
			Beam own = Beam.of(closedWorld(context, "t3.4b-beam-own"));
			check(own.orange > 20 && own.green == 0, "the rule's own colour wins over the island's: " + own);

			context.setScreen(() -> new ConfigScreen(null));
			context.waitForScreen(ConfigScreen.class);
			context.runOnClient(client -> screen(client).select(Category.WAYPOINTS));
			context.waitTicks(2);
			context.runOnClient(client -> screen(client).toggleRule(screen(client).page().page().rows().stream()
					.filter(row -> row instanceof ConfigLayout.RuleHeader header && header.rule().id().endsWith(udel.id))
					.map(row -> ((ConfigLayout.RuleHeader) row).rule()).findFirst().orElseThrow()));
			context.waitTicks(2);
			RuleCardsGameTest.click(context, RuleCardsGameTest.controlCentre(context, "npc_search.rule." + udel.id + ".use_island_color"));
			check(udel.beamColor == null, "\"Use island colour\" drops the rule's own colour");
			Beam after = Beam.of(closedWorld(context, "t3.4b-beam-after"));
			LOGGER.info("beam colours: before {}, own {}, after {}", before, own, after);
			check(after.green > 20 && after.red == 0 && after.orange == 0, "the beam follows the island's new colour (green): " + after);
			context.runOnClient(client -> ConfigManager.flush());
			check(RuleCardsGameTest.configFile(context).replaceAll("\\s", "").contains("\"islandBeamColors\":{\"CrimsonIsle\":" + GREEN + "}"),
					"the island colour is written");
		} finally {
			context.runOnClient(client -> {
				if (client.gui.screen() instanceof ConfigScreen) {
					client.gui.setScreen(null);
				}
				((ColorOption) OptionCatalog.live().stream().flatMap(card -> card.options().stream()).filter(option -> option.id().equals(SWATCH))
						.findFirst().orElseThrow()).binding().set(WaypointColors.ISLAND_DEFAULTS.get(ISLAND));
				module.useRulesForTest(saved);
				module.onSettingsClosed();
				ConfigManager.save();
				client.options.guiScale().set(guiScale);
				WorldMarkers.setHideLabelsWithHud(true);
				IslandTracker.forceIsland(null);
			});
		}
	}

	/** Closes the settings screen with Esc and takes the world's screenshot two ticks later. */
	private static Path closedWorld(ClientGameTestContext context, String name) {
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> client.gui.screen() == null), "the settings screen is closed");
		return world(context, name);
	}

	private static Path world(ClientGameTestContext context, String name) {
		context.runOnClient(client -> setHudHidden(client, true));
		context.waitTicks(2);
		Path shot = context.takeScreenshot(name);
		context.runOnClient(client -> setHudHidden(client, false));
		return shot;
	}

	private static void setHudHidden(Minecraft client, boolean hidden) {
		if (client.gui.hud.isHidden() != hidden) {
			client.gui.hud.toggle();
		}
	}

	/** Beam pixels in the top 8% of the image, above the label, where only sky is otherwise: red, orange and green hues. */
	private record Beam(int red, int green, int orange) {
		static Beam of(Path shot) {
			try {
				BufferedImage image = ImageIO.read(shot.toFile());
				int top = (int) (image.getHeight() * 0.08);
				int red = 0, green = 0, orange = 0;
				for (int y = 0; y < top; y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						int rgb = image.getRGB(x, y);
						float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
						if (hsb[1] < 0.5f || hsb[2] < 0.35f) {
							continue;
						}
						// The sky is blue (about 0.6); red is about 0, orange 0.11, green 0.33.
						float hue = hsb[0];
						if (hue < 0.04f || hue > 0.96f) {
							red++;
						} else if (hue > 0.07f && hue < 0.15f) {
							orange++;
						} else if (hue > 0.25f && hue < 0.42f) {
							green++;
						}
					}
				}
				return new Beam(red, green, orange);
			} catch (IOException e) {
				throw new AssertionError("cannot read " + shot, e);
			}
		}
	}

	private static ConfigScreen screen(Minecraft client) {
		if (!(client.gui.screen() instanceof ConfigScreen screen)) {
			throw new AssertionError("FAILED: the settings screen is not open: " + client.gui.screen());
		}
		return screen;
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
