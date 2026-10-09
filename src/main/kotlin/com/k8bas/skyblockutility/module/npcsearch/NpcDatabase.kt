package com.k8bas.skyblockutility.module.npcsearch

import com.k8bas.skyblockutility.data.BundledData
import com.k8bas.skyblockutility.data.TableHandle
import com.k8bas.skyblockutility.data.TableState
import com.k8bas.skyblockutility.ui.option.RuleDatabase
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The NPC list for NPC Search (T3.8, REQ-NPCDB-03, REQ-NPCDB-04): the bundled `npcdb.npcs` table, so the picker
 * works offline from the first launch and nothing is fetched or cached. It is loading until the registry has
 * read the table, then ready, or unavailable if the table could not be read (the picker says so, EC-UI-08).
 */
object NpcDatabase {
	private val table: TableHandle<NpcList> by lazy {
		BundledData.table(NpcTable.SPEC).also { handle -> handle.onLoaded { LOADED_LISTENERS.forEach(Runnable::run) } }
	}
	private val LOADED_LISTENERS: MutableList<Runnable> = CopyOnWriteArrayList()

	/** Set by gametests: a list and state in place of the bundled table. */
	@Volatile
	private var testList: Pair<NpcList, RuleDatabase.State>? = null

	/** Set by gametests: another registry's table in place of the bundled one (AC-DATA-05). */
	@Volatile
	private var testTable: TableHandle<NpcList>? = null

	/** Registers the table with the registry; called once from the module, before the registry starts. */
	@JvmStatic
	fun register() {
		table
	}

	@JvmStatic
	fun state(): RuleDatabase.State = testList?.second ?: when ((testTable ?: table).state()) {
		is TableState.Loaded -> RuleDatabase.State.READY
		is TableState.Unavailable -> RuleDatabase.State.UNAVAILABLE
		else -> RuleDatabase.State.LOADING
	}

	private fun list(): NpcList = testList?.first ?: (testTable ?: table).value() ?: NpcList.EMPTY

	/** Every entry, in table order (empty until loaded). */
	@JvmStatic
	fun entries(): List<NpcDatabaseEntry> = list().entries

	/** The entry with this id or alias, or null if there is none (also while the list is still loading). */
	@JvmStatic
	fun byId(id: String?): NpcDatabaseEntry? = list().byId(id)

	/** Runs the listener on the client thread once the table has loaded. */
	@JvmStatic
	fun onLoaded(listener: Runnable) {
		LOADED_LISTENERS.add(listener)
	}

	/** For gametests: the list as if the table held it (or had failed); null goes back to the bundled table. */
	@JvmStatic
	fun useForTest(list: List<NpcDatabaseEntry>?, newState: RuleDatabase.State?) {
		testList = if (list == null || newState == null) null else NpcList.of(list) to newState
	}

	/** For gametests: read the list from [handle] (e.g. a registry without the mob table); null goes back. */
	@JvmStatic
	fun useTableForTest(handle: TableHandle<NpcList>?) {
		testTable = handle
	}
}
