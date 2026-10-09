package com.k8bas.skyblockutility.module.bobberfix

/**
 * The bobber fix's decisions (T3.5, REQ-BOB-01..REQ-BOB-03, REQ-BOB-05, REQ-BOB-06), without any game type, so they
 * are tested as tables (AC-BOB-01, AC-BOB-02). Each applies only to the local player's own hook, on SkyBlock, with
 * the toggle on; everything else stays vanilla.
 */
object BobberFix {
	/** The fluid a hook is in, as far as floating goes. */
	enum class Fluid {
		WATER,
		LAVA,
		OTHER,
	}

	/**
	 * Whether the client skips attaching the hook to the entity the server reports it hooked into (REQ-BOB-01): only
	 * Hypixel's bite-timer stand, the armor stand spawned right after the hook (id = hook id + 1). A mob, a player or
	 * any other stand keeps vanilla attach and pull (REQ-BOB-03).
	 *
	 * @param linked     whether the server reports a hooked entity at all
	 * @param linkedId   that entity's id
	 * @param armorStand whether that entity is an armor stand
	 */
	@JvmStatic
	fun ignoresLink(onSkyBlock: Boolean, enabled: Boolean, ownHook: Boolean, hookId: Int, linked: Boolean, linkedId: Int, armorStand: Boolean): Boolean =
		onSkyBlock && enabled && ownHook && linked && armorStand && hookId != Int.MAX_VALUE && linkedId == hookId + 1

	/** Whether the hook floats in [fluid] (REQ-BOB-02): water always, as in vanilla; lava only under the fix. */
	@JvmStatic
	fun floats(fluid: Fluid, onSkyBlock: Boolean, enabled: Boolean, ownHook: Boolean): Boolean = when (fluid) {
		Fluid.WATER -> true
		Fluid.LAVA -> onSkyBlock && enabled && ownHook
		Fluid.OTHER -> false
	}
}
