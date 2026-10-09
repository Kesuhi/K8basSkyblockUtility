package com.k8bas.skyblockutility.ui.render;

/** Easing curves for UI motion (REQ-UI-17: ~150 ms transitions). */
public final class Easing {
	private Easing() {
	}

	/** Fast start, gentle stop; t is clamped to 0..1. */
	public static float outCubic(float t) {
		float clamped = Math.max(0F, Math.min(1F, t));
		float rest = 1F - clamped;
		return 1F - rest * rest * rest;
	}
}
