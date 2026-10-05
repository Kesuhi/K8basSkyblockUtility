package com.k8bas.skyblockutility.debug

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import com.k8bas.skyblockutility.util.ChatUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.Objects

/**
 * `/ksu debug dump containers on|off` (T0.4b, REQ-GS-13). While armed, each allowlisted menu the
 * player opens is written to latest.log once its contents are stable. A command cannot be typed
 * while a menu is open, hence the arming. Passive: it only reads the open screen on the client
 * tick and never clicks, pages or opens anything. Disarms itself after 60 minutes (REQ-GS-12).
 */
object ContainerDump {
	private val ARM = CaptureArm(System::currentTimeMillis)
	private val STABLE = StableContents()
	private var trackedScreen: Screen? = null
	private var captureCount = 0

	@JvmStatic
	fun register() {
		ClientTickEvents.END_CLIENT_TICK.register { onTick(it) }
	}

	@JvmStatic
	fun arm() {
		ARM.arm()
		ChatUtils.chat("Container dump armed for 60 minutes; open the menus to capture")
	}

	@JvmStatic
	fun disarm() {
		ARM.disarm()
		ChatUtils.chat("Container dump disarmed")
	}

	private fun onTick(client: Minecraft) {
		if (ARM.expiredNow()) {
			ChatUtils.chat("Container dump disarmed after 60 minutes")
		}
		val screen = client.gui.screen()
		if (!ARM.isArmed || screen !is AbstractContainerScreen<*>) {
			trackedScreen = null
			return
		}
		val title = screen.title.string
		if (!CaptureAllowlist.matches(title)) {
			trackedScreen = null
			return
		}
		if (screen !== trackedScreen) {
			trackedScreen = screen
			STABLE.reset()
		}
		val menu = screen.menu
		val top = topSlots(menu)
		if (STABLE.onTick(menu.stateId, fingerprint(top), emptyFingerprint(top.size))) {
			write(++captureCount, title, menu.stateId, top)
		}
	}

	/** The menu's own slots, without the player inventory below it. */
	private fun topSlots(menu: AbstractContainerMenu): List<Slot> {
		val slots = ArrayList<Slot>()
		for (slot in menu.slots) {
			if (slot.container !is Inventory) {
				slots.add(slot)
			}
		}
		return slots
	}

	private fun fingerprint(slots: List<Slot>): Long {
		var hash = slots.size.toLong()
		for (slot in slots) {
			val stack = slot.item
			hash = 31 * hash + (
				if (stack.isEmpty) 0
				else Objects.hash(BuiltInRegistries.ITEM.getKey(stack.item), stack.count, stack.hoverName.string, loreLines(stack))
				)
		}
		return hash
	}

	private fun emptyFingerprint(slotCount: Int): Long {
		var hash = slotCount.toLong()
		for (i in 0 until slotCount) {
			hash *= 31
		}
		return hash
	}

	private fun write(capture: Int, title: String, stateId: Int, slots: List<Slot>) {
		val prefix = DebugCommand.DUMP_TAG + " container " + capture
		val masker = NameMasker.SESSION
		masker.learnFrom(Minecraft.getInstance())
		K8basSkyblockUtilityClient.LOGGER.info("{} title={} slots={} state={}", prefix, masker.mask(title), slots.size, stateId)
		for (i in slots.indices) {
			val stack = slots[i].item
			if (stack.isEmpty) {
				continue
			}
			K8basSkyblockUtilityClient.LOGGER.info(
				"{} slot {} item={} sbid={} count={} | {}", prefix, i,
				BuiltInRegistries.ITEM.getKey(stack.item), skyblockId(stack), stack.count, masker.mask(stack.hoverName.string),
			)
			val lore = loreLines(stack)
			for (line in lore.indices) {
				K8basSkyblockUtilityClient.LOGGER.info("{} slot {} lore {} | {}", prefix, i, line, masker.mask(lore[line]))
			}
		}
		ChatUtils.chat("Captured \"" + masker.mask(title) + "\" to latest.log")
	}

	private fun loreLines(stack: ItemStack): List<String> {
		val lore = stack.get(DataComponents.LORE)
		return if (lore == null) java.util.List.of() else lore.lines().stream().map { it.string }.toList()
	}

	/** SkyBlock's own item id from the custom data, or "-" for items without one. */
	private fun skyblockId(stack: ItemStack): String {
		val data = stack.get(DataComponents.CUSTOM_DATA)
		return if (data == null) "-" else data.copyTag().getStringOr("id", "-")
	}
}
