package com.k8bas.skyblockutility.render.marker

/**
 * One world marker: an anchor and what is drawn there, a label, a beacon beam and a ring, each
 * optional. This is where the see-through policy is enforced (REQ-MARK-02): a marker that is not at
 * fixed coordinates never shows through blocks, never shows a distance line and never gets a beam
 * (a beam rising from a hidden entity would show where it is, above every wall), whatever it asks for.
 * Rings are always depth-tested and flat, so they stay, but on an entity anchor only for a hologram
 * whose name tag vanilla displays (see [MarkerRing]).
 *
 * A class rather than a data class: the policy rewrites label and beam on construction, and a public
 * copy() would be a way round it. Java sees `anchor()`, `label()`, `beam()`, `ring()`.
 *
 * @param label the label, or null for none
 * @param beam  the beam, or null for none
 * @param ring  the ring, or null for none
 */
class Marker(
	@get:JvmName("anchor") val anchor: MarkerAnchor,
	label: MarkerLabel?,
	beam: MarkerBeam?,
	@get:JvmName("ring") val ring: MarkerRing?,
) {
	@get:JvmName("label")
	val label: MarkerLabel? = if (!anchor.isFixed() && label != null && (label.seeThrough || label.distanceLine)) label.depthTested() else label

	@get:JvmName("beam")
	val beam: MarkerBeam? = if (anchor.isFixed()) beam else null

	/** A marker with only a label. */
	constructor(anchor: MarkerAnchor, label: MarkerLabel?) : this(anchor, label, null, null)

	override fun equals(other: Any?): Boolean = other is Marker && anchor == other.anchor && label == other.label && beam == other.beam && ring == other.ring

	override fun hashCode(): Int = ((anchor.hashCode() * 31 + label.hashCode()) * 31 + beam.hashCode()) * 31 + ring.hashCode()

	override fun toString(): String = "Marker[anchor=$anchor, label=$label, beam=$beam, ring=$ring]"
}
