package com.k8bas.skyblockutility.ui.option

/** The "Add from database" button of a rule list: it opens the picker over [database] (REQ-UI-11). */
data class DatabaseOption(
	private val id: String,
	private val text: OptionText,
	@get:JvmName("buttonLabel") val buttonLabel: String,
	@get:JvmName("database") val database: RuleDatabase,
) : Option {
	override fun id(): String = id

	override fun storageKey(): String = "database:$id"

	override fun text(): OptionText = text

	override fun amber(): Boolean = false

	override fun status(): OptionStatus = OptionStatus.OK

	override fun defaultValue(): Any = ""

	override fun toString(): String = "DatabaseOption[id=$id, text=$text, buttonLabel=$buttonLabel, database=$database]"
}
