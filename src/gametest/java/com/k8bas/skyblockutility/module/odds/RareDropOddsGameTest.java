package com.k8bas.skyblockutility.module.odds;

import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.OptionStatus;
import com.k8bas.skyblockutility.ui.screen.ConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * T3.9h in the real client: the bundled odds tables loaded, the five cases follow the master toggle, and with the
 * Vanguard table unreadable only the Vanguard case is off while the card names it (AC-ODDS-09 [B]). Screenshots of the
 * Odds &amp; Trackers card for AC-ODDS-01 [C].
 */
public class RareDropOddsGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		Module module = ModuleManager.modules().stream().filter(m -> m.id().equals(RareDropOddsModule.ID)).findFirst().orElseThrow();
		boolean wasOn = context.computeOnClient(client -> module.isEnabled());
		try {
			context.waitFor(client -> !OddsTables.rngScores().isEmpty() && OddsTables.vanguardLoot() != null, 200);
			check(context.computeOnClient(client -> OddsTables.vanguardLoot().totalWeight()) == 3515, "the Vanguard table is loaded");
			context.runOnClient(client -> module.setEnabled(false));
			check(context.computeOnClient(client -> List.of(OddsCase.values()).stream().noneMatch(RareDropOddsModule::active)),
					"nothing runs with the feature OFF (its default)");
			context.runOnClient(client -> module.setEnabled(true));
			check(context.computeOnClient(client -> List.of(OddsCase.values()).stream().allMatch(RareDropOddsModule::active)), "every case runs when ON");

			context.runOnClient(client -> OddsTables.treatAsUnreadableForTest(Set.of(OddsTables.VANGUARD)));
			check(!context.computeOnClient(client -> RareDropOddsModule.active(OddsCase.VANGUARD)), "the Vanguard case is off without its table");
			check(context.computeOnClient(client -> List.of(OddsCase.SCATHA, OddsCase.CROESUS, OddsCase.CORPSES, OddsCase.SLAYER).stream()
					.allMatch(RareDropOddsModule::active)), "the other cases still run");
			OptionStatus status = context.computeOnClient(client -> OptionCatalog.live().stream().filter(card -> card.id().equals(RareDropOddsModule.ID))
					.findFirst().orElseThrow().toggle().status());
			check(status.state() == OptionStatus.State.PARTIAL && status.message().contains("Vanguard corpses"), "the card says which case is off: " + status);

			context.setScreen(() -> new ConfigScreen(null));
			context.waitForScreen(ConfigScreen.class);
			context.runOnClient(client -> ((ConfigScreen) client.gui.screen()).select(Category.ODDS_TRACKERS));
			context.waitTicks(3);
			context.takeScreenshot("t3.9h-odds-card-vanguard-unreadable");
			context.runOnClient(client -> OddsTables.treatAsUnreadableForTest(Set.of()));
			context.waitTicks(3);
			context.takeScreenshot("t3.9h-odds-card");
			context.setScreen(() -> null);
			LOGGER.info("rare drop odds: tables loaded, cases follow the toggles, a missing table turns off only its case ({})", status.message());
		} finally {
			context.runOnClient(client -> {
				OddsTables.treatAsUnreadableForTest(Set.of());
				module.setEnabled(wasOn);
			});
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
