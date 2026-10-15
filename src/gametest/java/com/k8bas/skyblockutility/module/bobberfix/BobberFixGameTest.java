package com.k8bas.skyblockutility.module.bobberfix;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.hook.HookRegistry;
import com.k8bas.skyblockutility.hook.HookStatus;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;

/**
 * T3.5 in a real client (Tier B), with the player's own rod cast into a pool in front of them:
 * <ul>
 *   <li>both optional hooks are in the game's FishingHook (REQ-BOB-08's applied side);</li>
 *   <li>AC-BOB-03: off SkyBlock with the toggle ON, the own hook bobs in water and never in lava, as vanilla;</li>
 *   <li>REQ-BOB-02: on SkyBlock (forced) with the toggle ON, the own hook bobs on lava;</li>
 *   <li>AC-BOB-08: switched off, the value is saved and the next cast into lava is vanilla again, without a restart.</li>
 * </ul>
 * "Bobs" is the client hook's own state machine reaching BOBBING, which needs its "in water?" check to pass; the
 * integrated server's hook (another owner object) stays vanilla throughout.
 */
public class BobberFixGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		check(context.computeOnClient(client -> HookStatus.REGISTRY.state(BobberFixModule.LINK_HOOK)) == HookRegistry.State.APPLIED
				&& context.computeOnClient(client -> HookStatus.REGISTRY.state(BobberFixModule.LAVA_HOOK)) == HookRegistry.State.APPLIED,
				"both bobber hooks are in the game's FishingHook");
		Module module = ModuleManager.modules().stream().filter(m -> m.id().equals(BobberFixModule.ID)).findFirst().orElseThrow();
		boolean wasOn = context.computeOnClient(client -> module.isEnabled());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("give @p minecraft:fishing_rod");
			context.waitFor(client -> client.player.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD), 40);
			context.runOnClient(client -> module.setEnabled(true));

			check(cast(context, singleplayer, server, "water"), "off SkyBlock, toggle ON: the hook bobs in water (vanilla)");
			check(!cast(context, singleplayer, server, "lava"), "off SkyBlock, toggle ON: the hook does not bob in lava (vanilla)");

			context.runOnClient(client -> IslandTracker.forceIsland("Crimson Isle"));
			check(cast(context, singleplayer, server, "lava"), "on SkyBlock, toggle ON: the own hook bobs on lava");

			context.runOnClient(client -> module.setEnabled(false));
			context.runOnClient(client -> ConfigManager.flush());
			check(configFile(context).replaceAll("\\s", "").contains("\"bobber_fix\":{\"enabled\":false}"), "switched off and saved");
			check(!cast(context, singleplayer, server, "lava"), "the next cast into lava is vanilla again, without a restart");
			LOGGER.info("bobber fix: hooks applied; water bobs, lava bobs only on SkyBlock with the fix on");
		} finally {
			context.runOnClient(client -> {
				IslandTracker.forceIsland(null);
				module.setEnabled(wasOn);
			});
		}
	}

	/**
	 * A fresh 13x12 pool of [liquid] ahead, the rod cast into it, and whether the client's own hook reached BOBBING
	 * within 3 s; the hook is reeled in after.
	 */
	private static boolean cast(ClientGameTestContext context, TestSingleplayerContext singleplayer, TestServerContext server, String liquid) {
		server.runCommand("fill -6 -63 3 6 -61 14 minecraft:" + liquid);
		server.runCommand("tp @a 0.5 -60 0.5 0 25");
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(5);
		context.runOnClient(client -> client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND));
		context.waitFor(client -> client.player.fishing != null, 40);
		boolean bobbed = false;
		for (int tick = 0; tick < 60 && !bobbed; tick++) {
			context.waitTick();
			bobbed = context.computeOnClient(client -> client.player.fishing != null && "BOBBING".equals(state(client.player.fishing)));
		}
		context.runOnClient(client -> {
			if (client.player.fishing != null) {
				client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
			}
		});
		context.waitFor(client -> client.player.fishing == null, 40);
		server.runCommand("fill -6 -63 3 6 -61 14 minecraft:air");
		return bobbed;
	}

	private static String state(FishingHook hook) {
		try {
			Field field = FishingHook.class.getDeclaredField("currentState");
			field.setAccessible(true);
			return String.valueOf(field.get(hook));
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read the hook's state", e);
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
