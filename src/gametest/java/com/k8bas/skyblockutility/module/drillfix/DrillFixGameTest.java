package com.k8bas.skyblockutility.module.drillfix;

import com.k8bas.skyblockutility.hook.HookRegistry;
import com.k8bas.skyblockutility.hook.HookStatus;
import com.k8bas.skyblockutility.location.IslandTracker;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * T3.7 in a real client (Tier B): the hand swap hook is applied, and the first-person main hand's height shows whether
 * the dip played when the held drill is replaced by command:
 * <ul>
 *   <li>AC-DRILL-05: off SkyBlock with the toggle ON, a refreshed copy (fuel and lore changed) dips, as vanilla;</li>
 *   <li>REQ-DRILL-01: on SkyBlock (forced), the same refresh does not dip;</li>
 *   <li>REQ-DRILL-02 / EC-DRILL-01: another drill (another uuid) dips;</li>
 *   <li>the toggle off: the refresh dips again.</li>
 * </ul>
 */
public class DrillFixGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		check(context.computeOnClient(client -> HookStatus.REGISTRY.state(DrillFixModule.HOOK)) == HookRegistry.State.APPLIED,
				"the hand swap hook is in the game's ItemInHandRenderer");
		Module module = ModuleManager.modules().stream().filter(m -> m.id().equals(DrillFixModule.ID)).findFirst().orElseThrow();
		boolean wasOn = context.computeOnClient(client -> module.isEnabled());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			context.runOnClient(client -> module.setEnabled(true));

			check(dipped(context, server, "a-1", 3000, "a-1", 2990), "off SkyBlock: a fuel refresh dips as in vanilla (AC-DRILL-05)");
			context.runOnClient(client -> IslandTracker.forceIsland("Dwarven Mines"));
			check(!dipped(context, server, "a-1", 3000, "a-1", 2990), "on SkyBlock: the same drill refreshed does not dip");
			check(dipped(context, server, "a-1", 3000, "b-2", 3000), "on SkyBlock: another drill still dips");
			context.runOnClient(client -> module.setEnabled(false));
			check(dipped(context, server, "a-1", 3000, "a-1", 2990), "the toggle off: the refresh dips again");
			LOGGER.info("drill re-equip fix: the refresh keeps the drill in hand only on SkyBlock with the fix on");
		} finally {
			context.runOnClient(client -> {
				IslandTracker.forceIsland(null);
				module.setEnabled(wasOn);
			});
		}
	}

	/** Holds a drill, lets the hand settle, replaces it, and whether the hand went down within 10 ticks. */
	private static boolean dipped(ClientGameTestContext context, TestServerContext server, String uuid, int fuel, String nextUuid, int nextFuel) {
		server.runCommand(drill(uuid, fuel));
		context.waitTicks(20);
		check(context.computeOnClient(client -> height(client)) > 0.99f, "the hand is up before the refresh");
		server.runCommand(drill(nextUuid, nextFuel));
		float lowest = 1f;
		for (int tick = 0; tick < 10; tick++) {
			context.waitTick();
			lowest = Math.min(lowest, context.computeOnClient(DrillFixGameTest::height));
		}
		return lowest < 0.9f;
	}

	private static String drill(String uuid, int fuel) {
		return "item replace entity @p weapon.mainhand with minecraft:prismarine_shard[minecraft:custom_data={id:\"TITANIUM_DRILL_2\",uuid:\""
				+ uuid + "\",drill_fuel:" + fuel + "},minecraft:lore=[\"Fuel: " + fuel + "/3k\"]]";
	}

	private static float height(Minecraft client) {
		try {
			Field field = ItemInHandRenderer.class.getDeclaredField("mainHandHeight");
			field.setAccessible(true);
			return field.getFloat(client.gameRenderer.itemInHandRenderer);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read the hand height", e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
