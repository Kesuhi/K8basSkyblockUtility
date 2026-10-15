package com.k8bas.skyblockutility.gametest.hook;

import com.k8bas.skyblockutility.hook.HookRegistry;
import com.k8bas.skyblockutility.hook.HookStatus;
import com.k8bas.skyblockutility.ui.option.OptionStatus;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Tier C check of the optional hooks (T3.0f, REQ-BOB-08, AC-BOB-07 missing-target part [B]). The game started
 * with test hooks whose target class does not exist, whose call site does not exist, and one lost because
 * another mixin on its class failed late. Those are skipped, their cards show unavailable and each has exactly
 * one WARN line in the log; the two that went in are active and change what their target returns. A hook of
 * ours that fails late itself costs the class every mod's changes: then Mixin's warning stays beside ours,
 * which says so. No ERROR line names any test hook.
 */
public class HookStatusGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		check(HookStatus.REGISTRY.state("gametest.return") == HookRegistry.State.APPLIED, "the @ModifyReturnValue hook is active");
		check(HookStatus.REGISTRY.state("gametest.wrap") == HookRegistry.State.APPLIED, "the late @WrapOperation hook is active");
		check(new HookTarget().value() == 44, "both hooks change the result: " + new HookTarget().value());

		check(HookStatus.REGISTRY.state("gametest.unmatched") == HookRegistry.State.SKIPPED, "a call site that is gone is skipped");
		check(HookStatus.REGISTRY.state("gametest.missing") == HookRegistry.State.SKIPPED, "a missing target class is skipped");
		check(new RevertedTarget().value() == 7, "Mixin loaded the class with its original bytes: " + new RevertedTarget().value());
		check(HookStatus.REGISTRY.state("gametest.reverted") == HookRegistry.State.SKIPPED, "a hook lost with its class is skipped");
		check(new OwnLateTarget().value() == 5, "our failed late hook cost the other mixin its change: " + new OwnLateTarget().value());
		check(HookStatus.REGISTRY.state("gametest.ownlate") == HookRegistry.State.SKIPPED, "our failed late hook is skipped");

		check(HookStatus.status("gametest.return").equals(OptionStatus.OK), "an active hook's card is OK");
		check(HookStatus.status("gametest.missing").equals(new OptionStatus(OptionStatus.State.UNAVAILABLE, "Test feature unavailable")),
				"the card shows the hook's own text: " + HookStatus.status("gametest.missing"));
		check(HookStatus.status("gametest.unmatched").state() == OptionStatus.State.UNAVAILABLE, "unavailable on the card");

		// Exactly one WARN line names each skipped hook's mixin or target: this mod's, not also Mixin's.
		List<String> warnings = latestLog().lines().filter(line -> line.contains("/WARN]")).toList();
		Map<String, String> skipped = Map.of("gametest.unmatched", "UnmatchedHookMixin", "gametest.missing", "NoSuchClass",
				"gametest.reverted", "RevertedTargetMixin");
		skipped.forEach((id, name) -> {
			List<String> lines = warnings.stream().filter(line -> line.contains(name)).toList();
			check(lines.size() == 1 && lines.get(0).contains("Optional hook " + id + " "), "one WARN for " + id + ": " + lines);
		});
		check(warnings.stream().noneMatch(line -> line.contains("Optional hook gametest.return ") || line.contains("Optional hook gametest.wrap ")),
				"no warning for the hooks that went in");
		List<String> ownLate = warnings.stream().filter(line -> line.contains("OwnBadLateMixin")).toList();
		check(ownLate.size() == 2 && ownLate.stream().anyMatch(line -> line.contains("(FabricLoader/Mixin)"))
						&& ownLate.stream().anyMatch(line -> line.contains("Optional hook gametest.ownlate ") && line.contains("without any mod's changes")),
				"Mixin's warning stays beside ours when the class lost every mod's changes: " + ownLate);
		List<String> errors = latestLog().lines()
				.filter(line -> line.contains("/ERROR]") && (line.contains("gametest.hook.") || line.contains("gametest.mixin."))).toList();
		check(errors.isEmpty(), "no ERROR line names a test hook: " + errors);
	}

	private static String latestLog() {
		try {
			return Files.readString(FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError("FAILED: latest.log unreadable", e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
