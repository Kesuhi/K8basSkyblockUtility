package com.k8bas.skyblockutility.ui.option

import java.util.function.Supplier

/**
 * A colour, picked with a swatch and the colour picker (REQ-UI-13): 0xRRGGBB, or 0xAARRGGBB when
 * [storesAlpha].
 */
data class ColorOption(
	private val id: String,
	private val storageKey: String,
	private val text: OptionText,
	private val defaultValue: Int,
	@get:JvmName("storesAlpha") val storesAlpha: Boolean,
	@get:JvmName("binding") val binding: Binding<Int>,
	private val amber: Boolean,
	@get:JvmName("statusSource") val statusSource: Supplier<OptionStatus>,
) : Option {
	override fun id(): String = id

	override fun storageKey(): String = storageKey

	override fun text(): OptionText = text

	override fun defaultValue(): Int = defaultValue

	override fun amber(): Boolean = amber

	override fun status(): OptionStatus = statusSource.get()

	override fun toString(): String = "ColorOption[id=$id, storageKey=$storageKey, text=$text, defaultValue=$defaultValue, " +
		"storesAlpha=$storesAlpha, binding=$binding, amber=$amber, statusSource=$statusSource]"

	companion object {
		@JvmStatic
		fun of(id: String, storageKey: String, text: OptionText, defaultValue: Int, storesAlpha: Boolean, binding: Binding<Int>): ColorOption =
			ColorOption(id, storageKey, text, defaultValue, storesAlpha, binding, false, Supplier { OptionStatus.OK })
	}
}
