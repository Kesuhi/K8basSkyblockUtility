package com.k8bas.skyblockutility.ui.option

import java.util.function.Supplier

/** An on/off switch; a feature card's own toggle is one (REQ-UI-05, REQ-XC-TOGGLE-01). */
data class Toggle(
	private val id: String,
	private val storageKey: String,
	private val text: OptionText,
	private val defaultValue: Boolean,
	@get:JvmName("binding") val binding: Binding<Boolean>,
	private val amber: Boolean,
	@get:JvmName("statusSource") val statusSource: Supplier<OptionStatus>,
) : Option {
	override fun id(): String = id

	override fun storageKey(): String = storageKey

	override fun text(): OptionText = text

	override fun defaultValue(): Boolean = defaultValue

	override fun amber(): Boolean = amber

	override fun status(): OptionStatus = statusSource.get()

	fun asAmber(): Toggle = copy(amber = true)

	fun withStatus(source: Supplier<OptionStatus>): Toggle = copy(statusSource = source)

	override fun toString(): String =
		"Toggle[id=$id, storageKey=$storageKey, text=$text, defaultValue=$defaultValue, binding=$binding, amber=$amber, statusSource=$statusSource]"

	companion object {
		@JvmStatic
		fun of(id: String, storageKey: String, text: OptionText, defaultValue: Boolean, binding: Binding<Boolean>): Toggle =
			Toggle(id, storageKey, text, defaultValue, binding, false, Supplier { OptionStatus.OK })
	}
}
