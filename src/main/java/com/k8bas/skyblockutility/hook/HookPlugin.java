package com.k8bas.skyblockutility.hook;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * The config plugin of an optional mixin config (T3.0f, AD-9): declares the config's hooks, sets up the error
 * handler and the extension that checks each target class once fully transformed, and tells the registry which
 * target classes exist. It never turns a mixin off. Lives outside the mixin package, as Mixin forbids loading
 * classes from there.
 */
public abstract class HookPlugin implements IMixinConfigPlugin {
	private List<Hook> hooks = List.of();

	/** The hooks of this config. */
	protected abstract List<Hook> hooks();

	@Override
	public void onLoad(String mixinPackage) {
		hooks = List.copyOf(hooks());
		hooks.forEach(HookStatus.REGISTRY::declare);
		HookErrorHandler.install();
		if (!HookExtension.install()) {
			hooks.forEach(hook -> HookStatus.REGISTRY.failed(hook.mixin(), null, "Mixin's transformer could not be inspected"));
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	/**
	 * Drops a hook's target class that does not exist before Mixin looks it up, which would log its own warning;
	 * the hook is skipped with this mod's one warning instead. Every other mixin and target is left to Mixin.
	 */
	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String target = targetClassName.replace('/', '.');
		boolean ours = hooks.stream().anyMatch(hook -> hook.mixin().equals(mixinClassName) && hook.target().equals(target));
		if (ours && !classExists(target)) {
			HookStatus.REGISTRY.targetFailed(target, "target class " + target + " not found");
			return false;
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
		HookStatus.REGISTRY.targetsFound(hooks, myTargets, HookPlugin::classExists);
	}

	private static boolean classExists(String target) {
		return HookPlugin.class.getClassLoader().getResource(target.replace('.', '/') + ".class") != null;
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	/** Not yet final here: MixinExtras' late injections come after; {@link HookExtension} checks the class. */
	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
