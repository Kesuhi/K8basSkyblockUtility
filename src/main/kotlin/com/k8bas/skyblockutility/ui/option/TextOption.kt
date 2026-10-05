package com.k8bas.skyblockutility.ui.option

import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * A line of text, e.g. a rule's label or name pattern (REQ-UI-11). Typing applies at once; [invalid]
 * marks the field in the error colour while it holds (an invalid regular expression, REQ-UI-12).
 */
data class TextOption(
	private val id: String,
	private val storageKey: String,
	private val text: OptionText,
	private val defaultValue: String,
	@get:JvmName("maxLength") val maxLength: Int,
	@get:JvmName("placeholder") val placeholder: String,
	@get:JvmName("binding") val binding: Binding<String>,
	@get:JvmName("invalid") val invalid: BooleanSupplier,
	private val amber: Boolean,
	@get:JvmName("statusSource") val statusSource: Supplier<OptionStatus>,
) : Option {
	init {
		require(maxLength > 0) { "$id: maxLength $maxLength" }
	}

	override fun id(): String = id

	override fun storageKey(): String = storageKey

	override fun text(): OptionText = text

	override fun defaultValue(): String = defaultValue

	override fun amber(): Boolean = amber

	override fun status(): OptionStatus = statusSource.get()

	fun invalidWhen(condition: BooleanSupplier): TextOption = copy(invalid = condition)

	override fun toString(): String = "TextOption[id=$id, storageKey=$storageKey, text=$text, defaultValue=$defaultValue, maxLength=$maxLength, " +
		"placeholder=$placeholder, binding=$binding, invalid=$invalid, amber=$amber, statusSource=$statusSource]"

	companion object {
		@JvmStatic
		fun of(
			id: String,
			storageKey: String,
			text: OptionText,
			defaultValue: String,
			maxLength: Int,
			placeholder: String,
			binding: Binding<String>,
		): TextOption = TextOption(id, storageKey, text, defaultValue, maxLength, placeholder, binding, BooleanSupplier { false }, false,
			Supplier { OptionStatus.OK })
	}
}
