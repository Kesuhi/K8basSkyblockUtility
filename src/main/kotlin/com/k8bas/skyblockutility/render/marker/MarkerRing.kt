package com.k8bas.skyblockutility.render.marker

/**
 * A horizontal ring around the marker's anchor: an outline, a filled disc, or both (REQ-MARK-01). The
 * outline is always drawn opaque so it stays readable; the colour's alpha applies to the disc, so a
 * ring with a disc needs a non-zero alpha. Always depth-tested.
 *
 * On an entity anchor a ring is allowed only for a hologram whose name tag vanilla already displays,
 * such as a fishing hotspot (REQ-XC-RULES-04/05, R2): a ring reaches past the entity's outline, so
 * around any other entity behind a low wall it would show where the entity is. The provider checks that.
 *
 * @param radius in blocks
 */
@JvmRecord
data class MarkerRing(val radius: Double, val argb: Int, val style: Style) {
	enum class Style {
		OUTLINE,
		DISC,
		BOTH,
	}

	init {
		require(radius > 0 && radius.isFinite()) { "a ring needs a positive, finite radius, got $radius" }
		require(style == Style.OUTLINE || (argb ushr 24) != 0) { "a ring with a disc needs an alpha above 0, got ${Integer.toHexString(argb)}" }
	}

	override fun toString(): String = "MarkerRing[radius=$radius, argb=$argb, style=$style]"
}
