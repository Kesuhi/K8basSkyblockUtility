package com.k8bas.skyblockutility.hud;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The HUD elements, in the order they are drawn (REQ-HUD-01). Shipped features register theirs at
 * startup; test elements live only in the gametest source set (REQ-HUD-10).
 */
public final class HudRegistry {
	private static final List<HudElement> ELEMENTS = new CopyOnWriteArrayList<>();

	private HudRegistry() {
	}

	public static void register(HudElement element) {
		for (HudElement registered : ELEMENTS) {
			if (registered.id().equals(element.id())) {
				throw new IllegalArgumentException("HUD element id already registered: " + element.id());
			}
		}
		ELEMENTS.add(element);
	}

	/** Removes an element again (a gametest's own). */
	public static void unregister(String id) {
		ELEMENTS.removeIf(element -> element.id().equals(id));
	}

	public static List<HudElement> elements() {
		return List.copyOf(ELEMENTS);
	}
}
