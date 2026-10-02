package com.k8bas.skyblockutility.render.marker;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Where a marker sits. Only a fixed anchor (static data or a fixed point) may be drawn through blocks
 * or show a distance line; a marker anchored to, or derived from, a live entity is always depth-tested
 * (REQ-MARK-02, REQ-MARK-05, P1/P4).
 */
public sealed interface MarkerAnchor {
	/** The anchor's world position this frame. */
	Vec3 position(float partialTick);

	boolean isFixed();

	/**
	 * A fixed world coordinate from static data or a fixed point. Never pass coordinates read from an
	 * entity (its position, a hologram's name tag): a marker derived from a live entity must use
	 * {@link #entity}, so it stays depth-tested (REQ-MARK-02, "anchored to or derived from").
	 */
	static MarkerAnchor fixed(double x, double y, double z) {
		return new Fixed(new Vec3(x, y, z));
	}

	static MarkerAnchor entity(Entity entity, double yOffset) {
		return new OfEntity(Objects.requireNonNull(entity, "entity"), yOffset);
	}

	/** A fixed world coordinate; never one read from an entity (see {@link #fixed}). */
	record Fixed(Vec3 pos) implements MarkerAnchor {
		@Override
		public Vec3 position(float partialTick) {
			return pos;
		}

		@Override
		public boolean isFixed() {
			return true;
		}
	}

	/** A live entity, followed with its interpolated position, plus a height offset. */
	record OfEntity(Entity entity, double yOffset) implements MarkerAnchor {
		@Override
		public Vec3 position(float partialTick) {
			return entity.getPosition(partialTick).add(0, yOffset, 0);
		}

		@Override
		public boolean isFixed() {
			return false;
		}
	}
}
