package com.k8bas.skyblockutility.hook;

import com.k8bas.skyblockutility.ui.option.OptionStatus;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Which optional hooks went in (T3.0f, REQ-BOB-08, REQ-HOT-13). A hook starts pending. It is
 * applied when its target class, fully transformed (late MixinExtras injections included), calls its handler,
 * and stays so only if the class the game then loads has the handler merged in (Mixin defines the original
 * class when a step after ours fails). It is skipped when its target class is missing, its mixin failed, the
 * handler is not called, or the loaded class lacks it. {@link #settle} skips what is still pending and writes
 * one warning per skipped hook; a hook skipped later is warned about at once. Feature cards read
 * {@link #status}. Mixin may call in from any thread that loads a class, so every method is synchronised.
 * Plain Java: Mixin's config plugin uses it before the mods are initialised.
 */
public final class HookRegistry {
	public enum State {
		PENDING,
		APPLIED,
		SKIPPED,
	}

	private static final class Entry {
		final Hook hook;
		State state = State.PENDING;
		String reason = "";
		boolean warned;
		/** The transformation was seen before MixinExtras' late injections (a newer MixinExtras took over after us). */
		boolean unchecked;

		Entry(Hook hook) {
			this.hook = hook;
		}
	}

	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private Consumer<String> warn;

	public synchronized void declare(Hook hook) {
		if (entries.containsKey(hook.id())) {
			throw new IllegalArgumentException("hook declared twice: " + hook.id());
		}
		entries.put(hook.id(), new Entry(hook));
	}

	public synchronized List<Hook> hooks() {
		List<Hook> hooks = new ArrayList<>();
		entries.values().forEach(entry -> hooks.add(entry.hook));
		return List.copyOf(hooks);
	}

	/** True if {@code mixin} has a declared hook: its errors are this mod's to report. */
	public synchronized boolean owns(String mixin) {
		return entries.values().stream().anyMatch(entry -> entry.hook.mixin().equals(mixin));
	}

	/**
	 * Mixin found these target classes for a config: its hooks aimed at any other class are skipped. A class that
	 * exists but is not among them belongs to a mixin Mixin could not prepare; Mixin logs that error itself.
	 */
	public synchronized void targetsFound(Collection<Hook> hooks, Set<String> found, Predicate<String> classExists) {
		for (Hook hook : hooks) {
			if (!found.contains(hook.target())) {
				skip(entries.get(hook.id()), classExists.test(hook.target())
						? "its mixin could not be prepared (see Mixin's error in the log)"
						: "target class " + hook.target() + " not found");
			}
		}
	}

	/**
	 * Mixin finished transforming {@code target} into {@code node}: each pending hook there is applied if the class
	 * calls its handler. With {@code checked} false the late injections may not have run yet, so a missing call
	 * proves nothing: the hook stays pending and {@link #loaded} decides from the class the game loads.
	 */
	public synchronized void transformed(String target, ClassNode node, boolean checked) {
		for (Entry entry : entries.values()) {
			if (entry.state != State.PENDING || !entry.hook.target().equals(target)) {
				continue;
			}
			boolean found;
			try {
				found = HookProbe.injected(node, entry.hook.handler());
			} catch (RuntimeException e) {
				found = false;
			}
			if (found) {
				entry.state = State.APPLIED;
			} else if (checked) {
				skip(entry, "its handler is not called in " + target + " (the game code moved, or another mod changed it)");
			} else {
				entry.unchecked = true;
			}
		}
	}

	/**
	 * The game loaded {@code target} with these methods. An applied hook whose handler is not among them is lost, as
	 * is a hook still pending: the transformation failed before the end, and Mixin defined the original class. A
	 * hook seen only before the late injections counts as applied when its handler is merged, unconfirmed.
	 */
	public synchronized void loaded(String target, Iterable<String> methodNames) {
		for (Entry entry : entries.values()) {
			if (!entry.hook.target().equals(target)) {
				continue;
			}
			boolean merged = HookProbe.merged(methodNames, entry.hook.handler());
			if (entry.state == State.PENDING && entry.unchecked && merged) {
				entry.state = State.APPLIED;
			} else if (entry.state == State.PENDING || entry.state == State.APPLIED && !merged) {
				skip(entry, "the game loaded " + target + " without any mod's changes to it (a mixin on that class failed)");
			}
		}
	}

	/** The ids of hooks applied without their injection being confirmed (see {@link #transformed}). */
	public synchronized List<String> unconfirmed() {
		return entries.values().stream().filter(entry -> entry.state == State.APPLIED && entry.unchecked).map(entry -> entry.hook.id()).toList();
	}

	/**
	 * Mixin reported an error for {@code mixin} on {@code target} (null: while preparing it). Only hooks still
	 * pending are skipped: Mixin reports an error it suppressed after the class was checked, and the hooks found
	 * then are still in it. Returns true if such a pending hook was on {@code target}: the error was thrown before
	 * the class was checked, so Mixin defines that class without any mod's changes.
	 */
	public synchronized boolean failed(String mixin, String target, String reason) {
		boolean pendingOnTarget = false;
		for (Entry entry : entries.values()) {
			if (entry.state == State.PENDING && entry.hook.mixin().equals(mixin) && (target == null || entry.hook.target().equals(target))) {
				pendingOnTarget |= target != null;
				skip(entry, target == null ? reason
						: reason + "; the game loads " + target + " without any mod's changes to it");
			}
		}
		return pendingOnTarget;
	}

	/** Loading {@code target} failed: its hooks are skipped. */
	public synchronized void targetFailed(String target, String reason) {
		for (Entry entry : entries.values()) {
			if (entry.hook.target().equals(target)) {
				skip(entry, reason);
			}
		}
	}

	/** Every target was loaded: what is still pending did not go in. Writes one warning per skipped hook. */
	public synchronized void settle(Consumer<String> warn) {
		this.warn = warn;
		for (Entry entry : entries.values()) {
			if (entry.state == State.PENDING) {
				skip(entry, "not applied");
			} else if (entry.state == State.SKIPPED) {
				warnOnce(entry);
			}
		}
	}

	public synchronized State state(String id) {
		return entry(id).state;
	}

	/** For the feature's card: OK when applied, unavailable with the hook's text when skipped. */
	public synchronized OptionStatus status(String id) {
		Entry entry = entry(id);
		return switch (entry.state) {
			case APPLIED -> OptionStatus.OK;
			case PENDING -> new OptionStatus(OptionStatus.State.LOADING, "");
			case SKIPPED -> new OptionStatus(OptionStatus.State.UNAVAILABLE, entry.hook.unavailableText());
		};
	}

	private Entry entry(String id) {
		Entry entry = entries.get(id);
		if (entry == null) {
			throw new IllegalArgumentException("unknown hook: " + id);
		}
		return entry;
	}

	private void skip(Entry entry, String reason) {
		if (entry == null || entry.state == State.SKIPPED) {
			return;
		}
		entry.state = State.SKIPPED;
		entry.reason = reason;
		if (warn != null) {
			warnOnce(entry);
		}
	}

	private void warnOnce(Entry entry) {
		if (!entry.warned) {
			entry.warned = true;
			warn.accept("Optional hook " + entry.hook.id() + " (" + entry.hook.mixin() + " into " + entry.hook.target()
					+ ") is not active: " + entry.reason + "; the feature shows as unavailable");
		}
	}
}
