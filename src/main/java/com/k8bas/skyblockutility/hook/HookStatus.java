package com.k8bas.skyblockutility.hook;

import com.k8bas.skyblockutility.ui.option.OptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * The optional cosmetic hooks' status for feature cards (T3.0f, AD-9). Mixin feeds {@link #REGISTRY} through
 * {@link HookPlugin} and {@link HookErrorHandler}; {@link #settle} runs at the end of client start-up.
 */
public final class HookStatus {
	public static final HookRegistry REGISTRY = new HookRegistry();
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/hook");

	private HookStatus() {
	}

	/** The card status of hook {@code id}: OK, or unavailable with the hook's text. */
	public static OptionStatus status(String id) {
		return REGISTRY.status(id);
	}

	/**
	 * Loads each hook's target class without initialising it, so Mixin applies the hooks now rather than when the
	 * game first uses the class, checks that the loaded class has the handlers, then writes one warning per hook
	 * that did not go in. Nothing here can stop the game from starting (REQ-BOB-08).
	 */
	public static void settle() {
		ClassLoader loader = HookStatus.class.getClassLoader();
		for (String target : REGISTRY.hooks().stream().map(Hook::target).distinct().toList()) {
			Class<?> loaded;
			try {
				loaded = Class.forName(target, false, loader);
			} catch (ClassNotFoundException e) {
				// A missing target is already known from Mixin.
				continue;
			} catch (RuntimeException | LinkageError e) {
				// Fabric reports a failed transformation as a RuntimeException.
				REGISTRY.targetFailed(target, "loading " + target + " failed (" + e.getClass().getSimpleName() + ")");
				continue;
			}
			try {
				REGISTRY.loaded(target, Arrays.stream(loaded.getDeclaredMethods()).map(Method::getName).toList());
			} catch (RuntimeException | LinkageError e) {
				// The methods' types could not be resolved; the hooks keep what the transformation showed.
			}
		}
		REGISTRY.settle(LOGGER::warn);
		for (String id : REGISTRY.unconfirmed()) {
			LOGGER.info("Optional hook {} is merged, but whether it was injected could not be checked (another MixinExtras ran its late injections after this mod's check)", id);
		}
		if (!REGISTRY.hooks().isEmpty()) {
			long applied = REGISTRY.hooks().stream().filter(hook -> REGISTRY.state(hook.id()) == HookRegistry.State.APPLIED).count();
			LOGGER.info("Optional hooks: {} of {} active", applied, REGISTRY.hooks().size());
		}
	}
}
