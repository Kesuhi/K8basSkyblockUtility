package com.k8bas.skyblockutility.render.marker;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.npcsearch.NpcRule;
import com.k8bas.skyblockutility.module.npcsearch.NpcWaypointMarkers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * T3.0b, the world marker toolkit in a real frame:
 * <ul>
 *   <li>AC-MARK-01, label part: legible behind stone, a glass pane, a 3-block water column and ice at
 *       30 blocks, and the same pixel height at 20 and 60 m.</li>
 *   <li>AC-MARK-02 [C]: an entity-anchored label (with its depth-tested background) is hidden behind
 *       stone, also behind stone 10 blocks before it at 30 m; a fixed label in the same spot is not.</li>
 *   <li>AC-MARK-04 [C] and EC-MARK-05: the distance in third person, back and front, is measured from
 *       the player.</li>
 *   <li>AC-MARK-05 [C]: turning NPC waypoints off leaves another provider's markers.</li>
 *   <li>AC-MARK-07 [C]: the first frame after a dimension change draws no marker of the old world.</li>
 *   <li>REQ-MARK-07 (a marker behind the camera is not submitted), EC-MARK-02 (a label beyond the render
 *       distance), EC-MARK-04 (an anchor inside a block), EC-MARK-06 (labels hide with F1) and
 *       EC-MARK-09 (duplicates).</li>
 * </ul>
 * Each label screenshot is paired with the same view without the label; their difference is the label.
 */
public class MarkerToolkitGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final int WHITE = 0xFFFFFF;
	private static final int BACKGROUND = 0x70202020;

	/** The second provider of AC-MARK-05, also used to place test markers. */
	private static final class TestProvider implements MarkerProvider {
		private final boolean clearsOnReset;
		volatile List<Marker> markers = List.of();

		TestProvider(boolean clearsOnReset) {
			this.clearsOnReset = clearsOnReset;
		}

		@Override
		public boolean isActive() {
			return !markers.isEmpty();
		}

		@Override
		public void collect(Consumer<Marker> out) {
			markers.forEach(out);
		}

		@Override
		public void reset() {
			if (clearsOnReset) {
				markers = List.of();
			}
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		TestProvider provider = new TestProvider(true);
		// Keeps its markers on reset, so only the toolkit's own check can drop its old-world entity marker.
		TestProvider keeping = new TestProvider(false);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @a 0.5 -60 0.5 0 0");
			context.runOnClient(client -> {
				WorldMarkers.add(provider);
				WorldMarkers.add(keeping);
				WorldMarkers.setHideLabelsWithHud(false);
				setHudHidden(client, true);
				client.options.cloudStatus().set(CloudStatus.OFF);
			});
			try {
				legibleBehindObstacles(context, singleplayer, server, provider);
				entityAnchorsAreDepthTested(context, singleplayer, server, provider);
				distanceFromThePlayerInThirdPerson(context, server, provider);
				providersAreIndependentAndCulled(context, provider);
				labelsHideWithF1(context, provider);
				aWorldChangeDropsTheOldWorldsMarkers(context, server, provider, keeping);
			} finally {
				context.runOnClient(client -> {
					WorldMarkers.remove(provider);
					WorldMarkers.remove(keeping);
					WorldMarkers.setHideLabelsWithHud(true);
					WorldMarkers.setRecordFrames(false);
					setHudHidden(client, false);
					client.options.setCameraType(CameraType.FIRST_PERSON);
					NpcWaypointMarkers.setActiveWaypoints(List.of());
					IslandTracker.forceIsland(null);
				});
				server.runCommand("kill @e[type=!minecraft:player]");
			}
		}
	}

	private static Marker whiteLabel(MarkerAnchor anchor, String text, boolean seeThrough) {
		MarkerLabel label = MarkerLabel.of(new MarkerLabel.Line(Component.literal(text), WHITE));
		return new Marker(anchor, seeThrough ? label.asSeeThrough() : label);
	}

	/** AC-MARK-01 (label part), EC-MARK-02 and EC-MARK-04. */
	private static void legibleBehindObstacles(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server,
			TestProvider provider) {
		Map<String, String[]> obstacles = new LinkedHashMap<>();
		obstacles.put("nothing", new String[0]);
		obstacles.put("stone", new String[] {"fill -8 -60 5 8 -52 5 minecraft:stone"});
		obstacles.put("glass pane", new String[] {"fill -8 -60 5 8 -52 5 minecraft:glass_pane"});
		// Clear glass keeps the water from flowing to the player.
		obstacles.put("water", new String[] {"fill -8 -60 3 8 -51 7 minecraft:glass", "fill -7 -60 4 7 -52 6 minecraft:water"});
		obstacles.put("ice", new String[] {"fill -8 -60 5 8 -52 5 minecraft:ice"});
		// EC-MARK-04: the anchor itself sits inside an opaque block.
		obstacles.put("anchor in a block", new String[] {"setblock 0 -59 30 minecraft:stone"});
		Map<String, Label> labels = new LinkedHashMap<>();
		for (Map.Entry<String, String[]> obstacle : obstacles.entrySet()) {
			server.runCommand("fill -8 -60 1 8 -50 70 minecraft:air");
			for (String command : obstacle.getValue()) {
				server.runCommand(command);
			}
			labels.put(obstacle.getKey(), measure(context, singleplayer, server, provider,
					whiteLabel(MarkerAnchor.fixed(0.5, -58.5, 30.5), "Marker Test", true), "t3.0b-30-" + obstacle.getKey().replace(' ', '-')));
		}
		server.runCommand("fill -8 -60 1 8 -50 70 minecraft:air");
		Label at20 = measure(context, singleplayer, server, provider, whiteLabel(MarkerAnchor.fixed(0.5, -58.5, 20.5), "Marker Test", true), "t3.0b-20");
		Label at60 = measure(context, singleplayer, server, provider, whiteLabel(MarkerAnchor.fixed(0.5, -58.5, 60.5), "Marker Test", true), "t3.0b-60");
		// EC-MARK-02: far beyond the render distance, in unloaded chunks.
		Label at400 = measure(context, singleplayer, server, provider, whiteLabel(MarkerAnchor.fixed(0.5, -58.5, 400.5), "Marker Test", true), "t3.0b-400");
		LOGGER.info("marker label at 30 blocks: {}; at 20 {}, at 60 {}, at 400 {}", labels, at20, at60, at400);

		Label open = labels.get("nothing");
		check(open.height > 5 && open.textPixels > 30, "the label is visible at 30 blocks: " + open);
		for (Map.Entry<String, Label> entry : labels.entrySet()) {
			Label behind = entry.getValue();
			check(Math.abs(behind.height - open.height) <= 2 && Math.abs(behind.width - open.width) <= 2,
					"same label box behind " + entry.getKey() + ": " + behind + " vs " + open);
			check(behind.textPixels >= open.textPixels * 0.9, "the text keeps its exact colour behind " + entry.getKey() + ": " + behind + " vs " + open);
		}
		check(at20.height > 0 && Math.abs(at60.height - at20.height) <= at20.height * 0.1,
				"the pixel height at 60 m (" + at60.height + ") matches 20 m (" + at20.height + ") within 10%");
		check(at400.textPixels >= open.textPixels * 0.9, "a label beyond the render distance is drawn (EC-MARK-02): " + at400);
	}

	/** AC-MARK-02 [C]: also the depth-tested background, and a hill between 10 blocks and the anchor. */
	private static void entityAnchorsAreDepthTested(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server,
			TestProvider provider) {
		server.runCommand("fill -8 -60 1 8 -50 70 minecraft:air");
		server.runCommand("summon minecraft:armor_stand 0.5 -60 8.5 {NoGravity:1b,Invisible:1b,Marker:1b,Tags:[\"near\"]}");
		server.runCommand("summon minecraft:armor_stand 0.5 -60 30.5 {NoGravity:1b,Invisible:1b,Marker:1b,Tags:[\"far\"]}");
		context.waitFor(client -> stand(client, 8.5) != null && stand(client, 30.5) != null, 100);
		Entity near = context.computeOnClient(client -> stand(client, 8.5));
		Entity far = context.computeOnClient(client -> stand(client, 30.5));
		// A see-through request with a background: both refused for an entity anchor (unit-tested in MarkerTest).
		Marker onNear = entityLabel(near);
		Marker onFar = entityLabel(far);
		Marker fixedThere = whiteLabel(MarkerAnchor.fixed(0.5, -58.5, 8.5), "Entity Label", true);
		Label nearOpen = measure(context, singleplayer, server, provider, onNear, "t3.0b-entity-open");
		Label farOpen = measure(context, singleplayer, server, provider, onFar, "t3.0b-entity-far-open");
		// The size reference: the same label with its background, see-through at 20 m (pulled in to the 10-block size).
		Marker reference = new Marker(MarkerAnchor.fixed(0.5, -58.5, 20.5),
				MarkerLabel.of(new MarkerLabel.Line(Component.literal("Entity Label"), WHITE)).withBackground(BACKGROUND).asSeeThrough());
		Label fixedAt20 = measure(context, singleplayer, server, provider, reference, "t3.0b-entity-size-reference");
		server.runCommand("fill -8 -60 4 8 -52 4 minecraft:stone");
		Label nearBehind = measure(context, singleplayer, server, provider, onNear, "t3.0b-entity-behind-stone");
		Label fixedBehind = measure(context, singleplayer, server, provider, fixedThere, "t3.0b-fixed-behind-stone");
		server.runCommand("fill -8 -60 1 8 -50 70 minecraft:air");
		// A hill 20 blocks out, beyond the 10 blocks a see-through label is pulled in to.
		server.runCommand("fill -8 -60 20 8 -50 20 minecraft:stone");
		Label farBehind = measure(context, singleplayer, server, provider, onFar, "t3.0b-entity-far-behind-stone");
		LOGGER.info("entity-anchored label: near open {}, near behind stone {}, far open {}, far behind a hill {}; fixed label behind stone {}, size reference {}",
				nearOpen, nearBehind, farOpen, farBehind, fixedBehind, fixedAt20);
		// Depth-tested text goes through the lightmap and lands at #FEFFFF, so these count every changed pixel.
		check(nearOpen.changedPixels > 30, "the entity-anchored label is visible in the open: " + nearOpen);
		check(nearBehind.changedPixels == 0, "the entity-anchored label is hidden behind stone: " + nearBehind);
		check(farOpen.changedPixels > 30, "the entity-anchored label 30 blocks out is visible in the open: " + farOpen);
		check(farBehind.changedPixels == 0, "the entity-anchored label 30 blocks out is hidden behind a hill at 20 blocks: " + farBehind);
		check(fixedBehind.textPixels > 30, "a fixed label in the same spot shows through the stone: " + fixedBehind);
		// REQ-MARK-03: grown instead of pulled in, the far label keeps the constant on-screen size of a
		// see-through label beyond 10 blocks (the near one, within 10 blocks, has its natural size).
		check(fixedAt20.changedHeight > 0 && Math.abs(farOpen.changedHeight - fixedAt20.changedHeight) <= Math.max(1, fixedAt20.changedHeight * 0.15),
				"the far entity label has the constant size: " + farOpen + " vs the fixed label at 20 m " + fixedAt20);
		server.runCommand("kill @e[type=minecraft:armor_stand]");
		server.runCommand("fill -8 -60 1 8 -50 70 minecraft:air");
	}

	private static Marker entityLabel(Entity entity) {
		MarkerLabel label = MarkerLabel.of(new MarkerLabel.Line(Component.literal("Entity Label"), WHITE)).withBackground(BACKGROUND).asSeeThrough();
		return new Marker(MarkerAnchor.entity(entity, 1.5), label);
	}

	/** AC-MARK-04 [C] and EC-MARK-05: the player at (0.5, -60, 0.5), the camera 4 blocks away, markers 100 blocks off. */
	private static void distanceFromThePlayerInThirdPerson(ClientGameTestContext context, TestServerContext server, TestProvider provider) {
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		MarkerLabel label = MarkerLabel.of(new MarkerLabel.Line(Component.literal("Far"), WHITE)).asSeeThrough().withDistance(WHITE);
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			WorldMarkers.setRecordFrames(true);
			provider.markers = List.of(new Marker(MarkerAnchor.fixed(0.5, -60, 100.5), label));
		});
		context.waitTicks(5);
		WorldMarkers.Frame back = context.computeOnClient(client -> WorldMarkers.lastFrame());
		// In the front view the camera looks back at the player, so the marker is put behind the player.
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			provider.markers = List.of(new Marker(MarkerAnchor.fixed(0.5, -60, -99.5), label));
		});
		context.waitTicks(5);
		WorldMarkers.Frame front = context.computeOnClient(client -> WorldMarkers.lastFrame());
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			provider.markers = List.of();
		});
		LOGGER.info("third person: back {}, front {}", back, front);
		check(back.distanceTexts().equals(List.of("100m")), "the distance line reads 100m in third person: " + back);
		check(front.distanceTexts().equals(List.of("100m")), "the distance line reads 100m in the front view (EC-MARK-05): " + front);
	}

	/** AC-MARK-05 [C], REQ-MARK-07 and EC-MARK-09. */
	private static void providersAreIndependentAndCulled(ClientGameTestContext context, TestProvider provider) {
		context.runOnClient(client -> {
			IslandTracker.forceIsland("Hub");
			NpcRule rule = new NpcRule();
			rule.label = "Npc Waypoint";
			rule.island = "Hub";
			rule.fixed = true;
			rule.x = -2;
			rule.y = -60;
			rule.z = 12.5;
			NpcWaypointMarkers.setActiveWaypoints(List.of(rule));
			provider.markers = List.of(whiteLabel(MarkerAnchor.fixed(3, -58.5, 12.5), "Second", true));
		});
		context.waitTicks(5);
		WorldMarkers.Frame both = context.computeOnClient(client -> WorldMarkers.lastFrame());
		context.runOnClient(client -> NpcWaypointMarkers.setActiveWaypoints(List.of()));
		context.waitTicks(5);
		WorldMarkers.Frame second = context.computeOnClient(client -> WorldMarkers.lastFrame());
		// Behind the camera: the player looks towards +z.
		context.runOnClient(client -> provider.markers = List.of(
				whiteLabel(MarkerAnchor.fixed(0.5, -58.5, 12.5), "Ahead", true), whiteLabel(MarkerAnchor.fixed(0.5, -58.5, -20.5), "Behind", true)));
		context.waitTicks(5);
		WorldMarkers.Frame ahead = context.computeOnClient(client -> WorldMarkers.lastFrame());
		Marker duplicate = whiteLabel(MarkerAnchor.fixed(0.5, -58.5, 12.5), "Twice", true);
		context.runOnClient(client -> provider.markers = List.of(duplicate, duplicate));
		context.waitTicks(5);
		WorldMarkers.Frame duplicates = context.computeOnClient(client -> WorldMarkers.lastFrame());
		context.runOnClient(client -> provider.markers = List.of());
		LOGGER.info("providers: both {}, NPC waypoints off {}; one ahead and one behind {}; duplicates {}", both, second, ahead, duplicates);
		check(both.submitted() == 2, "both providers' markers are drawn: " + both);
		check(second.submitted() == 1, "with NPC waypoints off, the other provider's marker is still drawn: " + second);
		check(ahead.submitted() == 1 && ahead.culled() == 1, "a marker behind the camera is not submitted: " + ahead);
		check(duplicates.submitted() == 2, "two markers at one position are both drawn (EC-MARK-09): " + duplicates);
	}

	/** EC-MARK-06: labels hide with F1, like name tags. */
	private static void labelsHideWithF1(ClientGameTestContext context, TestProvider provider) {
		context.runOnClient(client -> {
			WorldMarkers.setHideLabelsWithHud(true);
			provider.markers = List.of(whiteLabel(MarkerAnchor.fixed(0.5, -58.5, 12.5), "F1 Test", true));
		});
		context.waitTicks(5);
		WorldMarkers.Frame hidden = context.computeOnClient(client -> WorldMarkers.lastFrame());
		context.runOnClient(client -> setHudHidden(client, false));
		context.waitTicks(5);
		WorldMarkers.Frame shown = context.computeOnClient(client -> WorldMarkers.lastFrame());
		context.runOnClient(client -> {
			setHudHidden(client, true);
			WorldMarkers.setHideLabelsWithHud(false);
			provider.markers = List.of();
		});
		LOGGER.info("F1: hidden {}, shown {}", hidden, shown);
		check(hidden.submitted() == 0, "labels hide with the HUD (F1): " + hidden);
		check(shown.submitted() == 1, "labels come back with the HUD: " + shown);
	}

	/**
	 * AC-MARK-07 [C]: one provider forgets its markers on reset, the other keeps a marker on an entity of
	 * the old world; the first frame in the Nether draws neither. Runs last: it leaves the overworld.
	 */
	private static void aWorldChangeDropsTheOldWorldsMarkers(ClientGameTestContext context, TestServerContext server, TestProvider provider,
			TestProvider keeping) {
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		server.runCommand("summon minecraft:armor_stand 0.5 -60 8.5 {NoGravity:1b,Invisible:1b,Marker:1b}");
		context.waitFor(client -> stand(client, 8.5) != null, 100);
		Entity overworldStand = context.computeOnClient(client -> stand(client, 8.5));
		context.runOnClient(client -> {
			provider.markers = List.of(whiteLabel(MarkerAnchor.fixed(0.5, -58.5, 12.5), "Old World", true));
			keeping.markers = List.of(entityLabel(overworldStand));
		});
		context.waitTicks(5);
		WorldMarkers.Frame before = context.computeOnClient(client -> WorldMarkers.lastFrame());
		server.runCommand("execute in minecraft:the_nether run tp @a 0.5 64 0.5 0 0");
		context.waitFor(client -> client.level != null && client.level.dimension() == Level.NETHER, 200);
		context.waitTicks(5);
		WorldMarkers.Frame first = context.computeOnClient(client -> WorldMarkers.firstFrameAfterReset());
		context.runOnClient(client -> keeping.markers = List.of());
		LOGGER.info("world change: before {}, first frame in the new world {}", before, first);
		check(before.submitted() == 2, "both markers are drawn in the old world: " + before);
		check(first.submitted() == 0 && first.culled() == 0, "the first frame in the new world draws no marker of the old one: " + first);
	}

	private static Label measure(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server, TestProvider provider,
			Marker marker, String name) {
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		context.runOnClient(client -> provider.markers = List.of());
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(10);
		Path without = context.takeScreenshot(name + "-without");
		context.runOnClient(client -> provider.markers = List.of(marker));
		context.waitTicks(5);
		Path with = context.takeScreenshot(name);
		context.runOnClient(client -> provider.markers = List.of());
		return Label.of(with, without);
	}

	private static Entity stand(Minecraft client, double z) {
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand && Math.abs(entity.getZ() - z) < 0.1) {
				return entity;
			}
		}
		return null;
	}

	/**
	 * The label's text: the pixels that are exactly white with the label and not without it, and their
	 * bounding box; and every pixel that changed at all, with its box height and most common new colour.
	 */
	private record Label(int width, int height, int textPixels, int changedPixels, int changedHeight, String commonColour) {
		static Label of(Path with, Path without) {
			try {
				BufferedImage a = ImageIO.read(with.toFile());
				BufferedImage b = ImageIO.read(without.toFile());
				int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1, text = 0;
				int changed = 0, changedMinY = Integer.MAX_VALUE, changedMaxY = -1;
				Map<Integer, Integer> colours = new LinkedHashMap<>();
				for (int y = 0; y < a.getHeight(); y++) {
					for (int x = 0; x < a.getWidth(); x++) {
						int rgbWith = a.getRGB(x, y) & 0xFFFFFF;
						int rgbWithout = b.getRGB(x, y) & 0xFFFFFF;
						if (rgbWith != rgbWithout) {
							changed++;
							changedMinY = Math.min(changedMinY, y);
							changedMaxY = Math.max(changedMaxY, y);
							colours.merge(rgbWith, 1, Integer::sum);
						}
						if (rgbWith == WHITE && rgbWithout != WHITE) {
							minX = Math.min(minX, x);
							minY = Math.min(minY, y);
							maxX = Math.max(maxX, x);
							maxY = Math.max(maxY, y);
							text++;
						}
					}
				}
				String common = colours.entrySet().stream().max(Map.Entry.comparingByValue())
						.map(e -> String.format("%06x x%d", e.getKey(), e.getValue())).orElse("-");
				int changedHeight = changedMaxY < 0 ? 0 : changedMaxY - changedMinY + 1;
				return maxX < 0 ? new Label(0, 0, 0, changed, changedHeight, common)
						: new Label(maxX - minX + 1, maxY - minY + 1, text, changed, changedHeight, common);
			} catch (IOException e) {
				throw new AssertionError("cannot read screenshots", e);
			}
		}
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
