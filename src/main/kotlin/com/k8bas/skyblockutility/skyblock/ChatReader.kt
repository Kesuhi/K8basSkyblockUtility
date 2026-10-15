package com.k8bas.skyblockutility.skyblock

import com.k8bas.skyblockutility.util.ChatUtils
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.Event
import net.minecraft.resources.Identifier
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/**
 * The chat and action-bar reader (T3.0g, REQ-GS-04): every game message the server sends, once, with its
 * overlay flag, in the text the server sent. Its ALLOW_GAME listener runs in a phase before Fabric's
 * default, so it sees a line before other mods rewrite it (MODIFY_GAME comes after every ALLOW_GAME), and
 * Fabric calls it even for a line another listener cancels; it always lets the line through and never
 * changes it (REQ-XC-RULES-02). GAME and GAME_CANCELED, in a phase after the default, end the line's handling,
 * so lines other mods post meanwhile are not taken for server lines ([MessageDispatch]). A line hidden below
 * Fabric's events (a packet-level mixin) is not seen (EC-GS-04). Player chat never reaches subscribers
 * (REQ-GS-05).
 */
object ChatReader {
	/** Runs before Fabric's default phase, where other mods' listeners are. */
	@JvmField
	val EARLY_PHASE: Identifier = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "game_state_reader")

	/** Runs after Fabric's default phase. */
	@JvmField
	val LATE_PHASE: Identifier = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "game_state_reader_end")

	private val dispatch = MessageDispatch(BooleanSupplier { ChatUtils.isSending() })

	/** [listener] hears every server message (not player chat) while [active] is true. Client thread. */
	@JvmStatic
	fun subscribe(owner: String, active: BooleanSupplier, listener: Consumer<ServerMessage>) = dispatch.subscribe(owner, active, listener)

	/** How many messages reached subscribers. */
	@JvmStatic
	fun deliveredCount(): Int = dispatch.deliveredCount()

	internal fun tick() = dispatch.tick()

	internal fun register() {
		ClientReceiveMessageEvents.ALLOW_GAME.addPhaseOrdering(EARLY_PHASE, Event.DEFAULT_PHASE)
		ClientReceiveMessageEvents.ALLOW_GAME.register(EARLY_PHASE, ClientReceiveMessageEvents.AllowGame { message, overlay ->
			GameStateReaders.CLIENT_THREAD.run()
			dispatch.allow(message.string, overlay)
		})
		ClientReceiveMessageEvents.GAME.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE)
		ClientReceiveMessageEvents.GAME.register(LATE_PHASE, ClientReceiveMessageEvents.Game { _, _ -> dispatch.exit() })
		ClientReceiveMessageEvents.GAME_CANCELED.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE)
		ClientReceiveMessageEvents.GAME_CANCELED.register(LATE_PHASE, ClientReceiveMessageEvents.GameCanceled { _, _ -> dispatch.exit() })
	}
}
