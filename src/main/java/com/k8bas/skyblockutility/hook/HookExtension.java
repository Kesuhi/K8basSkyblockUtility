package com.k8bas.skyblockutility.hook;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.List;

/**
 * Looks at each class once Mixin has fully transformed it (T3.0f). MixinExtras injects {@code @WrapOperation},
 * {@code @ModifyExpressionValue} and {@code @WrapWithCondition} late, in its own extension after the config
 * plugins' {@code postApply}; this extension is added after it, so it sees the final class. It is added from the
 * config plugin's {@code onLoad}, which Mixin calls before it activates the extensions, through Mixin's own
 * extension registry, after MixinExtras is set up. It only reads.
 */
final class HookExtension implements IExtension {
	private static final String LATE = ".LateInjectionApplicatorExtension";
	private static boolean installed;

	private final Extensions extensions;

	private HookExtension(Extensions extensions) {
		this.extensions = extensions;
	}

	/** Adds the extension once; false if Mixin's transformer is not the expected one (no hook can then be checked). */
	static synchronized boolean install() {
		if (installed) {
			return true;
		}
		// MixinExtras sets itself up once; done first, its late injector comes before this extension.
		MixinExtrasBootstrap.init();
		if (MixinEnvironment.getDefaultEnvironment().getActiveTransformer() instanceof IMixinTransformer transformer
				&& transformer.getExtensions() instanceof Extensions registry) {
			registry.add(new HookExtension(registry));
			installed = true;
		}
		return installed;
	}

	@Override
	public boolean checkActive(MixinEnvironment environment) {
		return true;
	}

	@Override
	public void preApply(ITargetClassContext context) {
	}

	@Override
	public void postApply(ITargetClassContext context) {
		ClassNode node = context.getClassNode();
		HookStatus.REGISTRY.transformed(context.getClassInfo().getClassName(), node, afterLateInjections());
	}

	@Override
	public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {
	}

	/** True if no MixinExtras late injector runs after this extension. */
	private boolean afterLateInjections() {
		List<IExtension> active = extensions.getActiveExtensions();
		int self = active.indexOf(this);
		if (self < 0) {
			return false;
		}
		for (IExtension later : active.subList(self + 1, active.size())) {
			if (later.getClass().getName().endsWith(LATE)) {
				return false;
			}
		}
		return true;
	}
}
