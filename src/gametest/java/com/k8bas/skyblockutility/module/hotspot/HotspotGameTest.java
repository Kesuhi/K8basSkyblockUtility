package com.k8bas.skyblockutility.module.hotspot;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.hook.HookRegistry;
import com.k8bas.skyblockutility.hook.HookStatus;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.render.marker.WorldMarkers;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.Toggle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * T3.6 in a real world with SkyBlock forced (Hub) and two named stands, `HOTSPOT` and `+5α Sea Creature Chance`, above
 * a water pool:
 * <ul>
 *   <li>the particle observer is applied, and server dust particles in a 2.5-block circle set the radius (AC-HOT-07's
 *       observer part, REQ-HOT-04);</li>
 *   <li>AC-HOT-06: the ring is drawn at the water surface in dark aqua, and a stone wall between camera and ring hides
 *       it (depth-tested);</li>
 *   <li>AC-HOT-11: the module, ring and fill toggles change the drawn ring at once, and each value is stored.</li>
 *   <li>AC-HOT-12 (T3.6c, R41 (b)): with "Hide Hypixel's hotspot particles" on, the server's ring dust is not added to
 *       the client while the radius still samples it; with it off it is; the same dust far from a hotspot always is.</li>
 * </ul>
 */
public class HotspotGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final double CX = 0.5;
	private static final double CZ = 8.5;

	@Override
	public void runTest(ClientGameTestContext context) {
		check(context.computeOnClient(client -> HookStatus.REGISTRY.state(HotspotModule.PARTICLE_HOOK)) == HookRegistry.State.APPLIED,
				"the particle observer is in the game's packet handler");
		check(context.computeOnClient(client -> HookStatus.REGISTRY.state(HotspotModule.HIDE_HOOK)) == HookRegistry.State.APPLIED,
				"the particle hiding hook is in the game's packet handler");
		HotspotModule module = context.computeOnClient(client -> ModuleManager.modules().stream().filter(HotspotModule.class::isInstance)
				.map(HotspotModule.class::cast).findFirst().orElseThrow());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("fill -7 -61 2 8 -61 16 minecraft:water");
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:armor_stand %.1f -59.0 %.1f {Invisible:1b,NoGravity:1b,Marker:1b,"
					+ "CustomNameVisible:1b,CustomName:{text:\"HOTSPOT\",color:\"light_purple\",bold:true}}", CX, CZ));
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:armor_stand %.1f -59.3 %.1f {Invisible:1b,NoGravity:1b,Marker:1b,"
					+ "CustomNameVisible:1b,CustomName:{text:\"+5α Sea Creature Chance\",color:\"dark_aqua\"}}", CX, CZ));
			server.runCommand("tp @a 0.5 -60 0.5 0 40");
			context.runOnClient(client -> {
				client.options.cloudStatus().set(CloudStatus.OFF);
				IslandTracker.forceIsland("Hub");
			});
			singleplayer.getConnection().waitForChunksRender();
			context.waitFor(client -> module.hotspotsForTest().size() == 1, 40);
			HotspotTracker.Hotspot spot = context.computeOnClient(client -> module.hotspotsForTest().getFirst());
			check(spot.getBuff() == HotspotText.Buff.SEA_CREATURE_CHANCE, "the buff line under it: " + spot.getBuff());
			check(Math.abs(spot.surfaceY() - (-61 + 8 / 9.0)) < 0.01, "the ring sits on the water surface: " + spot.surfaceY());
			check(context.computeOnClient(client -> spot.radius("Hub")) == 3.0, "the Hub fallback until particles are seen");

			for (int i = 0; i < 24; i++) {
				double angle = i * Math.PI / 12;
				server.runCommand(String.format(Locale.ROOT, "particle minecraft:dust{color:[1.0,0.41,0.71],scale:1.0} %.3f -60.0 %.3f 0 0 0 0 1 force",
						CX + 2.5 * Math.cos(angle), CZ + 2.5 * Math.sin(angle)));
			}
			context.waitTicks(5);
			check(context.computeOnClient(client -> spot.sampleCount()) == 24, "the observer saw each ring particle packet once: " + spot.sampleCount());
			check(context.computeOnClient(client -> spot.radius("Hub")) == 2.5, "their distance is the radius: " + spot.radius("Hub"));

			// AC-HOT-12: hidden by default, still sampled; drawn with the option off; dust far from the hotspot always drawn.
			int hidden = addedParticles(context, server, CX + 2.5, CZ);
			check(hidden < 30 && context.computeOnClient(client -> spot.sampleCount()) == 25, "hidden but sampled: " + hidden + " added");
			int far = addedParticles(context, server, CX + 12.0, CZ);
			check(far >= 70, "the same dust 12 blocks out is drawn: " + far);
			set(context, "fishing_hotspots.hide_particles", false);
			int shown = addedParticles(context, server, CX + 2.5, CZ);
			check(shown >= 70 && context.computeOnClient(client -> spot.sampleCount()) == 26, "the option off: drawn and sampled: " + shown);
			set(context, "fishing_hotspots.hide_particles", true);

			// AC-HOT-06.
			int open = ringPixels(context, "t3.6-hotspot-ring");
			server.runCommand("fill -8 -60 3 9 -54 3 minecraft:stone");
			singleplayer.getConnection().waitForChunksRender();
			int walled = ringPixels(context, "t3.6-hotspot-behind-wall");
			server.runCommand("fill -8 -60 3 9 -54 3 minecraft:air");
			LOGGER.info("hotspot ring: {} dark aqua pixels in the open, {} behind a wall; radius {}", open, walled, spot.radius("Hub"));
			check(open > 100, "the ring is drawn in dark aqua: " + open);
			check(walled < 5, "a wall hides it: " + walled);

			// AC-HOT-11: each toggle changes the drawn ring on the next frame and is stored.
			singleplayer.getConnection().waitForChunksRender();
			check(rings(context) == 1, "one ring with the defaults");
			set(context, "fishing_hotspots.ring", false);
			check(rings(context) == 1, "\"Highlight ring\" off: the filled area stays");
			set(context, "fishing_hotspots.fill", false);
			check(rings(context) == 0, "and \"Filled area\" off: nothing");
			set(context, "fishing_hotspots.ring", true);
			set(context, "fishing_hotspots.fill", true);
			context.runOnClient(client -> ((IntSlider) option("fishing_hotspots.fill_opacity")).binding().set(0));
			check(rings(context) == 1, "0 % opacity: the ring alone");
			context.runOnClient(client -> ((IntSlider) option("fishing_hotspots.fill_opacity")).binding().set(HotspotConfig.DEFAULT_OPACITY));
			set(context, "fishing_hotspots.enabled", false);
			check(rings(context) == 0, "the module off: no ring");
			context.runOnClient(client -> {
				ConfigManager.save();
				ConfigManager.flush();
			});
			check(configFile(context).replaceAll("\\s", "").contains("\"fishing_hotspots\":{\"enabled\":false,\"ring\":true,\"fill\":true,\"fillOpacity\":25,"),
					"the values are stored");
			set(context, "fishing_hotspots.enabled", true);
			check(rings(context) == 1, "and back");
		} finally {
			context.runOnClient(client -> {
				IslandTracker.forceIsland(null);
				if (!module.isEnabled()) {
					module.setEnabled(true);
				}
			});
		}
	}

	/**
	 * How many particles the client added for one server packet of 100 ring-coloured dust particles at x, z (on the water),
	 * against the count just before: the engine counts per render group, and a few other particles are always about.
	 */
	private static int addedParticles(ClientGameTestContext context, TestServerContext server, double x, double z) {
		int before = context.computeOnClient(client -> particles(client));
		server.runCommand(String.format(Locale.ROOT, "particle minecraft:dust{color:[1.0,0.41,0.71],scale:1.0} %.3f -60.0 %.3f 0 0 0 0 100 force", x, z));
		context.waitTicks(2);
		return context.computeOnClient(client -> particles(client)) - before;
	}

	/** The client's particles: the sum of the per-group counts the debug screen shows (e.g. "SQ 3 T 3"). */
	private static int particles(Minecraft client) {
		return java.util.regex.Pattern.compile("\\d+").matcher(client.particleEngine.countParticles()).results().mapToInt(m -> Integer.parseInt(m.group())).sum();
	}

	/** The added dark aqua pixels with the ring against without it (ring and fill off for that shot, the hotspot kept). */
	private static int ringPixels(ClientGameTestContext context, String name) {
		context.runOnClient(client -> setHudHidden(client, true));
		set(context, "fishing_hotspots.ring", false);
		set(context, "fishing_hotspots.fill", false);
		context.waitTicks(3);
		Path without = context.takeScreenshot(name + "-without");
		set(context, "fishing_hotspots.ring", true);
		set(context, "fishing_hotspots.fill", true);
		context.waitTicks(3);
		Path with = context.takeScreenshot(name);
		context.runOnClient(client -> setHudHidden(client, false));
		try {
			BufferedImage a = ImageIO.read(with.toFile());
			BufferedImage b = ImageIO.read(without.toFile());
			int count = 0;
			for (int y = 0; y < a.getHeight(); y++) {
				for (int x = 0; x < a.getWidth(); x++) {
					int rgb = a.getRGB(x, y) & 0xFFFFFF;
					if (rgb != (b.getRGB(x, y) & 0xFFFFFF) && darkAqua(rgb)) {
						count++;
					}
				}
			}
			return count;
		} catch (IOException e) {
			throw new AssertionError("cannot read the screenshots", e);
		}
	}

	/** The opaque outline's colour, 0x00AAAA, give or take shading. */
	private static boolean darkAqua(int rgb) {
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		return r < 60 && g > 120 && b > 120 && Math.abs(g - b) < 30;
	}

	private static int rings(ClientGameTestContext context) {
		context.waitTicks(2);
		return context.computeOnClient(client -> WorldMarkers.lastFrame().rings());
	}

	private static Option option(String id) {
		return OptionCatalog.live().stream().flatMap(card -> card.all().stream()).filter(option -> option.id().equals(id)).findFirst().orElseThrow();
	}

	private static void set(ClientGameTestContext context, String id, boolean value) {
		context.runOnClient(client -> ((Toggle) option(id)).binding().set(value));
	}

	private static void setHudHidden(Minecraft client, boolean hidden) {
		if (client.gui.hud.isHidden() != hidden) {
			client.gui.hud.toggle();
		}
	}

	private static String configFile(ClientGameTestContext context) {
		try {
			return Files.readString(context.computeOnClient(client -> FabricLoader.getInstance().getConfigDir().resolve("k8bas_skyblock_utility.json")));
		} catch (IOException e) {
			throw new AssertionError("cannot read the config", e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
