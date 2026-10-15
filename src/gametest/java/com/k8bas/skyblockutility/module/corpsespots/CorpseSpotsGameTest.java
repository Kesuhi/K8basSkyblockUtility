package com.k8bas.skyblockutility.module.corpsespots;

import com.k8bas.skyblockutility.hud.HudRegistry;
import com.k8bas.skyblockutility.hud.HudText;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.module.mineshaftalert.MineshaftAlertModule;
import com.k8bas.skyblockutility.render.marker.WorldMarkers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

/**
 * T3.3 (R40) through the real module, mineshaft state and marker toolkit, with the dev-only shaft simulation:
 * <ul>
 *   <li>AC-CORPSE-03 [C]: in a simulated OPAL_1, one "Possible corpse #n" label per spot (5); walled in by stone, the
 *       labels ahead are still on screen (their cyan pixels, against a shot with the feature off), and none within 1 s
 *       with the feature OFF or after leaving;</li>
 *   <li>AC-CORPSE-05 [C]: the widget listing every corpse LOOTED hides them, a NOT LOOTED one brings them back;</li>
 *   <li>AC-CORPSE-11 [C] (R43 (b)): a block-category harp the server plays at the player with the captured pitch 1.968
 *       gives the "Corpse ding" HUD element "very close" while the labels stay plain; it clears 2 s later, and with the
 *       hint off a ding shows nothing;</li>
 *   <li>AC-CORPSE-06 [C]: `/ksu corpses` lists the 5 spots with the feature and the mineshaft entry alert both OFF: the
 *       command keeps the shaft read and answers once the code is in (here when the shaft is simulated again).</li>
 * </ul>
 */
public class CorpseSpotsGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		Module module = ModuleManager.modules().stream().filter(m -> m.id().equals(CorpseSpotsModule.ID)).findFirst().orElseThrow();
		Module alert = ModuleManager.modules().stream().filter(m -> m.id().equals(MineshaftAlertModule.ID)).findFirst().orElseThrow();
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamemode spectator @a");
			// Inside OPAL_1's spots, walled in by stone on every side, facing spot #1 (-136, 3, -167).
			server.runCommand("tp @a -150 12 -170 -78 32");
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("fill -153 10 -173 -147 15 -167 minecraft:stone hollow");
			singleplayer.getConnection().waitForChunksRender();
			context.waitFor(client -> CorpseSpotTable.current() != null, 200);

			command(context, "ksu debug island Glacite Mineshafts");
			command(context, "ksu debug shaft OPAL_1 Lapis:NOT_LOOTED Umber:NOT_LOOTED");
			context.waitTicks(5);
			check(labels(context) == 5, "one label per OPAL_1 spot: " + labels(context));
			int through = cyanPixels(context, "t3.3-corpse-spots-through-stone");
			context.runOnClient(client -> module.setEnabled(false));
			int without = cyanPixels(context, "t3.3-corpse-spots-off");
			context.runOnClient(client -> module.setEnabled(true));
			check(through > 20 && without == 0, "the labels ahead show through the stone: " + through + " cyan pixels, " + without + " without them");

			command(context, "ksu debug shaft OPAL_1 Lapis:LOOTED Umber:LOOTED");
			context.waitTicks(20);
			check(labels(context) == 0, "every corpse looted: hidden");
			command(context, "ksu debug shaft OPAL_1 Lapis:LOOTED Umber:NOT_LOOTED");
			context.waitTicks(20);
			check(labels(context) == 5, "one not looted again: back");

			server.runCommand("playsound minecraft:block.note_block.harp block @a -150 12 -170 1 1.968");
			context.waitTicks(3);
			check("§6Corpse ding: §every close".equals(dingLine(context, module)), "the HUD line: " + dingLine(context, module));
			check(context.computeOnClient(client -> CorpseDingHud.INSTANCE.content()) != null, "the shipped element shows it");
			check(texts(context, module).equals(List.of("Possible corpse #1", "Possible corpse #2", "Possible corpse #3", "Possible corpse #4",
					"Possible corpse #5")), "the labels stay plain while it dings: " + texts(context, module));
			// Registered for the HUD editor, with its preview.
			check(context.computeOnClient(client -> HudRegistry.elements().stream().anyMatch(element -> element.id().equals(CorpseDingHud.ID)
					&& element.displayName().equals("Corpse ding") && element.preview() instanceof HudText text
					&& text.lines().equals(List.of("§6Corpse ding: §every close")))), "the editor lists Corpse ding with its preview");
			context.waitTicks(45);
			check(dingLine(context, module) == null, "cleared 2 s after the ding");
			// The hint OFF: a ding changes nothing.
			option(context, "corpse_spots.ding_hint", false);
			server.runCommand("playsound minecraft:block.note_block.harp block @a -150 12 -170 1 1.968");
			context.waitTicks(3);
			check(dingLine(context, module) == null && !context.computeOnClient(client -> CorpseDingHud.INSTANCE.enabled()), "the hint off: nothing");
			option(context, "corpse_spots.ding_hint", true);
			// The corpse spots OFF: a ding shows nothing either.
			context.runOnClient(client -> module.setEnabled(false));
			server.runCommand("playsound minecraft:block.note_block.harp block @a -150 12 -170 1 1.968");
			context.waitTicks(3);
			check(dingLine(context, module) == null && !context.computeOnClient(client -> CorpseDingHud.INSTANCE.enabled()), "the feature off: nothing");
			context.runOnClient(client -> module.setEnabled(true));
			context.waitTicks(45);

			context.runOnClient(client -> {
				module.setEnabled(false);
				alert.setEnabled(false);
			});
			context.waitTicks(20);
			check(labels(context) == 0, "the feature off: none within 1 s");
			// With nothing reading the shaft the simulation is gone; the command waits for the shaft to be read again.
			command(context, "ksu corpses");
			context.waitTicks(5);
			check(chat(context).stream().noneMatch(line -> line.startsWith("Opal 1: ") || line.equals("Not in a Glacite Mineshaft")), "no answer yet: " + chat(context));
			command(context, "ksu debug shaft OPAL_1 Lapis:NOT_LOOTED Umber:NOT_LOOTED");
			context.waitTicks(5);
			List<String> lines = chat(context);
			check(lines.contains("Opal 1: 5 possible corpse spots") && lines.stream().filter(line -> line.startsWith("#")).count() == 5,
					"/ksu corpses lists the 5 spots with the feature off: " + lines);
			context.runOnClient(client -> {
				module.setEnabled(true);
				alert.setEnabled(true);
			});
			context.waitTicks(5);
			check(labels(context) == 5, "and on again");

			command(context, "ksu debug shaft clear");
			command(context, "ksu debug island clear");
			context.waitTicks(20);
			check(labels(context) == 0, "after leaving: none within 1 s");
			LOGGER.info("corpse spots: {}", lines);
		} finally {
			context.runOnClient(client -> {
				if (!module.isEnabled()) {
					module.setEnabled(true);
				}
				if (!alert.isEnabled()) {
					alert.setEnabled(true);
				}
				WorldMarkers.setHideLabelsWithHud(true);
			});
		}
	}

	/** The label colour's pixels in a shot with the HUD hidden and the labels kept (only the labels can be cyan in the stone box). */
	private static int cyanPixels(ClientGameTestContext context, String name) {
		context.runOnClient(client -> {
			WorldMarkers.setHideLabelsWithHud(false);
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		context.waitTicks(3);
		Path shot = context.takeScreenshot(name);
		context.runOnClient(client -> client.gui.hud.toggle());
		try {
			BufferedImage image = ImageIO.read(shot.toFile());
			int count = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);
					int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
					if (r < 140 && g > 180 && b > 180) {
						count++;
					}
				}
			}
			return count;
		} catch (IOException e) {
			throw new AssertionError("cannot read the screenshot", e);
		}
	}

	/** The labels of the last frame, in view or not (the corpse spots are this world's only markers). */
	private static int labels(ClientGameTestContext context) {
		context.waitTicks(2);
		return context.computeOnClient(client -> WorldMarkers.lastFrame().submitted() + WorldMarkers.lastFrame().culled());
	}

	private static void option(ClientGameTestContext context, String id, boolean value) {
		context.runOnClient(client -> ((com.k8bas.skyblockutility.ui.option.Toggle) com.k8bas.skyblockutility.settings.OptionCatalog.live().stream()
				.flatMap(card -> card.all().stream()).filter(option -> option.id().equals(id)).findFirst().orElseThrow()).binding().set(value));
	}

	/** The text of each shown label, as the markers draw it. */
	private static List<String> texts(ClientGameTestContext context, Module module) {
		return context.computeOnClient(client -> ((CorpseSpotsModule) module).labels().stream().map(pair -> pair.getSecond()).toList());
	}

	/** The "Corpse ding" HUD line now, or null. */
	private static String dingLine(ClientGameTestContext context, Module module) {
		return context.computeOnClient(client -> {
			var text = ((CorpseSpotsModule) module).dingText();
			return text == null ? null : text.lines().getFirst();
		});
	}

	/** The mod's chat lines, without their "[KSU] " prefix. */
	private static List<String> chat(ClientGameTestContext context) {
		return context.computeOnClient(client -> messages(client).stream().map(message -> message.content().getString())
				.filter(text -> text.startsWith("[KSU] ")).map(text -> text.substring("[KSU] ".length())).toList());
	}

	private static List<GuiMessage> messages(Minecraft client) {
		try {
			Field field = ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<GuiMessage> messages = (List<GuiMessage>) field.get(client.gui.hud.getChat());
			return List.copyOf(messages);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read the chat", e);
		}
	}

	private static void command(ClientGameTestContext context, String command) {
		context.runOnClient(client -> client.player.connection.sendCommand(command));
		context.waitTick();
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
