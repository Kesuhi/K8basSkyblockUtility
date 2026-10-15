package com.k8bas.skyblockutility.hook;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Finds a hook in its target class (T3.0f). Mixin says nothing when an optional injector finds no target
 * (defaultRequire 0), so this is how a skipped injection is noticed. Mixin renames a handler to
 * {@code <kind>$<uid>$<modid>$<name>}, so names are matched at their end. A handler that takes MixinExtras
 * sugar is called through {@code <handler>$mixinextras$bridge$<n>}, which exists whether or not the injection
 * went in; calls made inside the handler or its bridges therefore do not count.
 */
final class HookProbe {
	private HookProbe() {
	}

	/** True if some method of {@code target}, other than the handler and its bridges, calls the handler. */
	static boolean injected(ClassNode target, String handler) {
		String own = "$" + handler;
		String bridge = own + "$mixinextras$bridge$";
		for (MethodNode method : target.methods) {
			if (method.name.equals(handler) || method.name.endsWith(own) || method.name.contains(bridge)) {
				continue;
			}
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && (call.name.equals(handler) || call.name.endsWith(own) || call.name.contains(bridge))) {
					return true;
				}
			}
		}
		return false;
	}

	/** True if one of {@code methodNames}, the methods of the class as it was loaded, is the merged handler. */
	static boolean merged(Iterable<String> methodNames, String handler) {
		String own = "$" + handler;
		for (String name : methodNames) {
			if (name.equals(handler) || name.endsWith(own)) {
				return true;
			}
		}
		return false;
	}
}
