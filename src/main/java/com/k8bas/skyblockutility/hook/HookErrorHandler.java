package com.k8bas.skyblockutility.hook;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Hears Mixin's errors (T3.0f): one in a mixin with a declared hook skips the hook, which then gets this mod's
 * one warning, so Mixin's own report of it drops to INFO. Not when the error was thrown before the class was
 * checked: then Mixin defines the class without any mod's changes, other mods' included, and its warning stays.
 * Errors of every other mod's mixins keep the action Mixin chose. A non-required config's prepare and validate
 * errors never come here: Mixin logs them itself (the hook then shows "could not be prepared"). Mixin creates it
 * by name.
 */
public final class HookErrorHandler implements IMixinErrorHandler {
	private static boolean installed;

	static synchronized void install() {
		if (!installed) {
			installed = true;
			Mixins.registerErrorHandlerClass(HookErrorHandler.class.getName());
		}
	}

	@Override
	public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
		return record(mixin, null, th, action);
	}

	@Override
	public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
		return record(mixin, targetClassName == null ? null : targetClassName.replace('/', '.'), th, action);
	}

	private static ErrorAction record(IMixinInfo mixin, String target, Throwable th, ErrorAction action) {
		if (mixin == null || !HookStatus.REGISTRY.owns(mixin.getClassName())) {
			return action;
		}
		boolean classLost = HookStatus.REGISTRY.failed(mixin.getClassName(), target, th.getClass().getSimpleName() + ": " + th.getMessage());
		// Never lower a fatal error (a required config's), nor one that cost other mods their changes too.
		return action == ErrorAction.WARN && !classLost ? ErrorAction.NONE : action;
	}
}
