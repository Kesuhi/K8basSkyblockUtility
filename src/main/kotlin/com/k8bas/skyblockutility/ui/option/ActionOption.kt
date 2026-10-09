package com.k8bas.skyblockutility.ui.option

/**
 * A button that does something at once, e.g. the Remove in a rule's header (REQ-UI-11, R31). It stores no value of its
 * own; the screen writes the config after it ran (a discrete commit, REQ-UI-15).
 *
 * @param destructive drawn in the fixed destructive red, which never follows the accent (REQ-UI-17)
 */
data class ActionOption(
	private val id: String,
	private val text: OptionText,
	@get:JvmName("buttonLabel") val buttonLabel: String,
	@get:JvmName("destructive") val destructive: Boolean,
	@get:JvmName("action") val action: Runnable,
) : Option {
	override fun id(): String = id

	override fun storageKey(): String = "action:$id"

	override fun text(): OptionText = text

	override fun amber(): Boolean = false

	override fun status(): OptionStatus = OptionStatus.OK

	override fun defaultValue(): Any = ""

	override fun toString(): String = "ActionOption[id=$id, text=$text, buttonLabel=$buttonLabel, destructive=$destructive, action=$action]"
}
