package com.k8bas.skyblockutility.ui.option

import java.util.function.Supplier

/**
 * A value shown but not edited, e.g. a fixed NPC's coordinates (REQ-UI-11).
 *
 * @param wide takes the card's width but a short title's, for a sentence such as an update check's answer (T4.4b)
 */
data class InfoOption @JvmOverloads constructor(
	private val id: String,
	private val text: OptionText,
	@get:JvmName("value") val value: Supplier<String>,
	@get:JvmName("wide") val wide: Boolean = false,
) : Option {
	override fun id(): String = id

	override fun storageKey(): String = "info:$id"

	override fun text(): OptionText = text

	override fun amber(): Boolean = false

	override fun status(): OptionStatus = OptionStatus.OK

	override fun defaultValue(): Any = ""

	override fun toString(): String = "InfoOption[id=$id, text=$text, value=$value, wide=$wide]"
}
