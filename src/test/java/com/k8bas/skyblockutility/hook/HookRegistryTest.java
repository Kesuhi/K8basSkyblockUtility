package com.k8bas.skyblockutility.hook;

import com.k8bas.skyblockutility.ui.option.OptionStatus;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hook-status registry (T3.0f, REQ-BOB-08, REQ-HOT-13): a hook is applied, or skipped with one
 * WARN and "unavailable" on its card when its target class is missing, its mixin failed, its handler is not
 * called in the fully transformed class, or the class was loaded without it. The class nodes are synthetic;
 * the handler names follow Mixin's renaming (MethodMapper.getHandlerName) and MixinExtras' bridges.
 */
class HookRegistryTest {
	private static final String TARGET = "net.example.Bobber";
	private static final String MIXIN = "com.k8bas.skyblockutility.mixin.BobberMixin";
	private static final String RENAMED = "wrapOperation$zzb000$k8bas_skyblock_utility$k8bas$smooth";

	private final HookRegistry registry = new HookRegistry();
	private final List<String> warnings = new ArrayList<>();

	private static MethodNode method(String name, String... calls) {
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "()V", null, null);
		for (String call : calls) {
			method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/example/Bobber", call, "()V"));
		}
		method.instructions.add(new InsnNode(Opcodes.RETURN));
		return method;
	}

	private static ClassNode target(MethodNode... methods) {
		ClassNode node = new ClassNode();
		node.name = "net/example/Bobber";
		node.methods.addAll(List.of(methods));
		return node;
	}

	/** tick() calls the renamed handler, as after the injection; the handler is merged into the class. */
	private static ClassNode injected() {
		return target(method("tick", "move", RENAMED), method(RENAMED));
	}

	private static Hook hook(String id) {
		return new Hook(id, MIXIN, TARGET, "k8bas$smooth", "");
	}

	private void declare(Hook hook) {
		registry.declare(hook);
		registry.targetsFound(List.of(hook), Set.of(hook.target()), target -> true);
	}

	@Test
	void aHookWhoseHandlerIsCalledIsApplied() {
		declare(hook("bobber"));
		registry.transformed(TARGET, injected(), true);
		registry.loaded(TARGET, List.of("tick", RENAMED));
		registry.settle(warnings::add);
		assertEquals(HookRegistry.State.APPLIED, registry.state("bobber"));
		assertEquals(OptionStatus.OK, registry.status("bobber"));
		assertEquals(List.of(), warnings);
	}

	@Test
	void aMissingTargetClassSkipsTheHookWithOneWarning() {
		registry.declare(hook("bobber"));
		registry.targetsFound(registry.hooks(), Set.of("net.example.Other"), target -> false);
		registry.settle(warnings::add);
		registry.settle(warnings::add);
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"));
		assertEquals(OptionStatus.State.UNAVAILABLE, registry.status("bobber").state());
		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("bobber") && warnings.get(0).contains(TARGET), warnings.get(0));
	}

	@Test
	void aHandlerMergedButNeverCalledIsSkipped() {
		// The injection point moved (a game update) or another mod replaced the method: Mixin is silent.
		declare(hook("bobber"));
		registry.transformed(TARGET, target(method("tick", "move"), method(RENAMED)), true);
		registry.settle(warnings::add);
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"));
		assertEquals(1, warnings.size());
	}

	@Test
	void aCallInsideTheSugarBridgeDoesNotCount() {
		// A handler with MixinExtras sugar gets a bridge that calls it whether or not the injection went in.
		declare(hook("bobber"));
		String bridge = RENAMED + "$mixinextras$bridge$1";
		registry.transformed(TARGET, target(method("tick", "move"), method(bridge, RENAMED), method(RENAMED)), true);
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"));
	}

	@Test
	void aCallThroughTheSugarBridgeOrAnotherModsWrapCounts() {
		declare(new Hook("sugar", MIXIN, TARGET, "k8bas$smooth", ""));
		String bridge = RENAMED + "$mixinextras$bridge$1";
		registry.transformed(TARGET, target(method("tick", bridge), method(bridge, RENAMED), method(RENAMED)), true);
		assertEquals(HookRegistry.State.APPLIED, registry.state("sugar"));

		// Another mod's @WrapOperation around ours moves our call into a MixinExtras operation bridge.
		declare(new Hook("wrapped", MIXIN, "net.example.Line", "k8bas$smooth", ""));
		String operation = "mixinextras$bridge$" + RENAMED + "$2";
		registry.transformed("net.example.Line", target(method("tick", "other$handler"), method(operation, RENAMED), method(RENAMED)), true);
		assertEquals(HookRegistry.State.APPLIED, registry.state("wrapped"));
	}

	@Test
	void anUncheckedHookIsDecidedByTheLoadedClass() {
		// A newer MixinExtras ran its late injections after the check: a missing call is no proof either way.
		declare(hook("late"));
		declare(new Hook("lost", MIXIN, "net.example.Line", "k8bas$line", ""));
		registry.transformed(TARGET, target(method("tick", "move"), method(RENAMED)), false);
		registry.transformed("net.example.Line", target(method("tick")), false);
		assertEquals(HookRegistry.State.PENDING, registry.state("late"));
		registry.loaded(TARGET, List.of("tick", RENAMED));
		registry.loaded("net.example.Line", List.of("tick"));
		registry.settle(warnings::add);
		assertEquals(HookRegistry.State.APPLIED, registry.state("late"), "merged: applied, unconfirmed");
		assertEquals(List.of("late"), registry.unconfirmed());
		assertEquals(HookRegistry.State.SKIPPED, registry.state("lost"));
	}

	@Test
	void aMixinThatCouldNotBePreparedSaysSo() {
		// Mixin logs a non-required config's prepare error itself; the class exists, so "not found" would be wrong.
		registry.declare(hook("bobber"));
		registry.targetsFound(registry.hooks(), Set.of(), target -> true);
		registry.settle(warnings::add);
		assertTrue(warnings.get(0).contains("could not be prepared"), warnings.get(0));
	}

	@Test
	void aClassLoadedWithoutTheHandlerLosesTheHook() {
		// A late injection of another mod failed after ours: Mixin defined the class with its original bytes.
		declare(hook("bobber"));
		registry.transformed(TARGET, injected(), true);
		registry.loaded(TARGET, List.of("tick"));
		registry.settle(warnings::add);
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"));
		assertTrue(warnings.get(0).contains("without any mod's changes"), warnings.get(0));
	}

	@Test
	void aClassLoadedBeforeItsHookWasCheckedLosesTheHook() {
		// MixinExtras' late phase threw: the extension never saw the class, and the game loaded the original.
		declare(hook("bobber"));
		registry.loaded(TARGET, List.of("tick"));
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"));
	}

	@Test
	void anErrorThrownBeforeTheCheckLosesTheClass() {
		// Our own late injector threw: the extension never ran, and Mixin defines the class without any mod's changes.
		declare(hook("bobber"));
		assertTrue(registry.failed(MIXIN, TARGET, "InvalidInjectionException: invalid signature"), "the class is lost: Mixin's warning stays");
		registry.settle(warnings::add);
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"));
		assertTrue(warnings.get(0).contains("InvalidInjectionException") && warnings.get(0).contains("without any mod's changes"), warnings.get(0));
	}

	@Test
	void anErrorReportedAfterTheCheckLeavesAppliedHooks() {
		// Mixin reports errors it suppressed after the class was checked; the hooks found then are still in it.
		declare(hook("bobber"));
		registry.transformed(TARGET, injected(), true);
		assertFalse(registry.failed(MIXIN, TARGET, "InvalidInjectionException: another injector of the mixin"));
		assertEquals(HookRegistry.State.APPLIED, registry.state("bobber"));
	}

	@Test
	void anErrorOnOneTargetLeavesTheMixinsOtherTargets() {
		declare(hook("bobber"));
		declare(new Hook("line", MIXIN, "net.example.Line", "k8bas$line", ""));
		registry.failed(MIXIN, "net.example.Line", "InvalidInjectionException");
		assertEquals(HookRegistry.State.PENDING, registry.state("bobber"));
		assertEquals(HookRegistry.State.SKIPPED, registry.state("line"));
		registry.failed(MIXIN, null, "a prepare error");
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"), "a prepare error has no target: every pending hook of the mixin");
	}

	@Test
	void aTargetThatFailsToLoadSkipsItsHooks() {
		declare(hook("bobber"));
		registry.targetFailed(TARGET, "loading failed (RuntimeException)");
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"));
	}

	@Test
	void aHookNeverTransformedBySettlingIsSkipped() {
		declare(hook("bobber"));
		assertEquals(HookRegistry.State.PENDING, registry.state("bobber"));
		assertEquals(OptionStatus.State.LOADING, registry.status("bobber").state());
		registry.settle(warnings::add);
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"));
		assertEquals(1, warnings.size());
	}

	@Test
	void aFailureAfterSettlingWarnsAtOnceAndOnlyOnce() {
		declare(hook("bobber"));
		registry.transformed(TARGET, injected(), true);
		registry.settle(warnings::add);
		registry.targetFailed(TARGET, "late");
		registry.targetFailed(TARGET, "again");
		assertEquals(HookRegistry.State.SKIPPED, registry.state("bobber"));
		assertEquals(1, warnings.size(), "one warning per hook");
	}

	@Test
	void aCardShowsTheHooksOwnMessageOrTheDefault() {
		registry.declare(new Hook("particles", MIXIN, TARGET, "k8bas$see", "Radius estimation unavailable"));
		registry.declare(new Hook("drill", MIXIN, "net.example.Drill", "k8bas$x", ""));
		registry.targetsFound(registry.hooks(), Set.of(), target -> false);
		registry.settle(warnings::add);
		assertEquals(new OptionStatus(OptionStatus.State.UNAVAILABLE, "Radius estimation unavailable"), registry.status("particles"));
		assertEquals(new OptionStatus(OptionStatus.State.UNAVAILABLE, "Unavailable: its game hook could not be applied"), registry.status("drill"));
	}

	@Test
	void otherTargetsAndMixinsDoNotCount() {
		declare(hook("bobber"));
		registry.transformed("net.example.Other", injected(), true);
		registry.failed("com.k8bas.skyblockutility.mixin.OtherMixin", TARGET, "x");
		assertEquals(HookRegistry.State.PENDING, registry.state("bobber"));
		assertTrue(registry.owns(MIXIN));
		assertFalse(registry.owns("com.k8bas.skyblockutility.mixin.OtherMixin"), "another mod's error keeps Mixin's action");
	}

	@Test
	void hookIdsAreUniqueAndKnown() {
		registry.declare(hook("bobber"));
		assertThrows(IllegalArgumentException.class, () -> registry.declare(hook("bobber")));
		assertThrows(IllegalArgumentException.class, () -> registry.status("nope"));
	}
}
