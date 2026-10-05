package com.k8bas.skyblockutility.ui.option

import java.util.function.Function
import java.util.function.Supplier

/**
 * A single-choice dropdown (REQ-UI-06); its entry labels are searchable (REQ-UI-08).
 *
 * A class rather than a data class: the values are copied on construction. Java sees the record-style
 * accessors `values()`, `label()`, `binding()`, `statusSource()`.
 */
class Choice<E : Any>(
	private val id: String,
	private val storageKey: String,
	private val text: OptionText,
	private val defaultValue: E,
	values: List<E>,
	@get:JvmName("label") val label: Function<E, String>,
	@get:JvmName("binding") val binding: Binding<E>,
	private val amber: Boolean,
	@get:JvmName("statusSource") val statusSource: Supplier<OptionStatus>,
) : Option {
	@get:JvmName("values")
	val values: List<E> = java.util.List.copyOf(values)

	init {
		require(this.values.contains(defaultValue)) { "$id: the default must be one of the choices" }
	}

	override fun id(): String = id

	override fun storageKey(): String = storageKey

	override fun text(): OptionText = text

	override fun defaultValue(): E = defaultValue

	override fun amber(): Boolean = amber

	override fun status(): OptionStatus = statusSource.get()

	/** Unmodifiable, as the Java stream's toList() was. */
	override fun searchLabels(): List<String> = values.stream().map(label).toList()

	override fun equals(other: Any?): Boolean = other is Choice<*> && id == other.id && storageKey == other.storageKey && text == other.text &&
		defaultValue == other.defaultValue && values == other.values && label == other.label && binding == other.binding &&
		amber == other.amber && statusSource == other.statusSource

	override fun hashCode(): Int = listOf(id, storageKey, text, defaultValue, values, label, binding, amber, statusSource).hashCode()

	override fun toString(): String = "Choice[id=$id, storageKey=$storageKey, text=$text, defaultValue=$defaultValue, values=$values, " +
		"label=$label, binding=$binding, amber=$amber, statusSource=$statusSource]"

	companion object {
		@JvmStatic
		fun <E : Any> of(
			id: String,
			storageKey: String,
			text: OptionText,
			defaultValue: E,
			values: List<E>,
			label: Function<E, String>,
			binding: Binding<E>,
		): Choice<E> = Choice(id, storageKey, text, defaultValue, values, label, binding, false, Supplier { OptionStatus.OK })
	}
}
