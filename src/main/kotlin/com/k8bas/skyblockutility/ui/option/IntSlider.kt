package com.k8bas.skyblockutility.ui.option

import java.util.function.Supplier

/**
 * A whole-number slider (REQ-UI-06).
 *
 * @param unit      shown after the value, e.g. "blocks"
 * @param zeroLabel shown instead of 0 when 0 has a meaning of its own (e.g. "Unlimited"), empty if not
 */
data class IntSlider(
	private val id: String,
	private val storageKey: String,
	private val text: OptionText,
	private val defaultValue: Int,
	@get:JvmName("min") val min: Int,
	@get:JvmName("max") val max: Int,
	@get:JvmName("step") val step: Int,
	@get:JvmName("unit") val unit: String,
	@get:JvmName("zeroLabel") val zeroLabel: String,
	@get:JvmName("binding") val binding: Binding<Int>,
	private val amber: Boolean,
	@get:JvmName("statusSource") val statusSource: Supplier<OptionStatus>,
) : Option {
	init {
		require(min <= max && defaultValue >= min && defaultValue <= max && step > 0) {
			"$id: default $defaultValue must lie in $min..$max with a positive step"
		}
	}

	override fun id(): String = id

	override fun storageKey(): String = storageKey

	override fun text(): OptionText = text

	override fun defaultValue(): Int = defaultValue

	override fun amber(): Boolean = amber

	override fun status(): OptionStatus = statusSource.get()

	/** How a value reads on the slider. */
	fun format(value: Int): String = when {
		value == 0 && zeroLabel.isNotEmpty() -> zeroLabel
		unit.isEmpty() -> value.toString()
		else -> "$value $unit"
	}

	override fun toString(): String = "IntSlider[id=$id, storageKey=$storageKey, text=$text, defaultValue=$defaultValue, min=$min, max=$max, " +
		"step=$step, unit=$unit, zeroLabel=$zeroLabel, binding=$binding, amber=$amber, statusSource=$statusSource]"

	companion object {
		@JvmStatic
		fun of(
			id: String,
			storageKey: String,
			text: OptionText,
			defaultValue: Int,
			min: Int,
			max: Int,
			step: Int,
			unit: String,
			zeroLabel: String,
			binding: Binding<Int>,
		): IntSlider = IntSlider(id, storageKey, text, defaultValue, min, max, step, unit, zeroLabel, binding, false, Supplier { OptionStatus.OK })
	}
}
