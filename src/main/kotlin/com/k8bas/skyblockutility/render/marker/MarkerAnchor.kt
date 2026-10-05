package com.k8bas.skyblockutility.render.marker

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * Where a marker sits. Only a fixed anchor (static data or a fixed point) may be drawn through blocks
 * or show a distance line; a marker anchored to, or derived from, a live entity is always depth-tested
 * (REQ-MARK-02, REQ-MARK-05, P1/P4).
 */
sealed interface MarkerAnchor {
	/** The anchor's world position this frame (null only for a [Fixed] built without one, as the Java record allowed). */
	fun position(partialTick: Float): Vec3?

	fun isFixed(): Boolean

	/** A fixed world coordinate; never one read from an entity (see [fixed]). Nullable as the Java record's was. */
	@JvmRecord
	data class Fixed(val pos: Vec3?) : MarkerAnchor {
		override fun position(partialTick: Float): Vec3? = pos

		override fun isFixed(): Boolean = true

		override fun toString(): String = "Fixed[pos=$pos]"
	}

	/**
	 * A live entity, followed with its interpolated position, plus a height offset. The entity is nullable
	 * as the Java record's was (tests build one without a game); [entity] is the checked way in.
	 */
	@JvmRecord
	data class OfEntity(val entity: Entity?, val yOffset: Double) : MarkerAnchor {
		override fun position(partialTick: Float): Vec3 = entity!!.getPosition(partialTick).add(0.0, yOffset, 0.0)

		override fun isFixed(): Boolean = false

		override fun toString(): String = "OfEntity[entity=$entity, yOffset=$yOffset]"
	}

	/** Static on the interface for Java (`MarkerAnchor.fixed(...)`), as before. */
	companion object {
		/**
		 * A fixed world coordinate from static data or a fixed point. Never pass coordinates read from an
		 * entity (its position, a hologram's name tag): a marker derived from a live entity must use
		 * [entity], so it stays depth-tested (REQ-MARK-02, "anchored to or derived from").
		 */
		@JvmStatic
		fun fixed(x: Double, y: Double, z: Double): MarkerAnchor = Fixed(Vec3(x, y, z))

		@JvmStatic
		fun entity(entity: Entity, yOffset: Double): MarkerAnchor = OfEntity(entity, yOffset)
	}
}
