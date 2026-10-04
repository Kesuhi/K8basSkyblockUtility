package com.k8bas.skyblockutility.hud;

/** For gametests in other packages: what the in-game HUD pass drew. Call on the client thread. */
public final class HudForTests {
	private HudForTests() {
	}

	public static HudRect lastDrawn(String id) {
		return HudRenderer.lastDrawn(id);
	}

	public static void forgetDrawn() {
		HudRenderer.forgetDrawn();
	}
}
