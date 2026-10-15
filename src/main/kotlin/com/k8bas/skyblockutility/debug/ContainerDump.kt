package com.k8bas.skyblockutility.debug

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import com.k8bas.skyblockutility.skyblock.MenuEvent
import com.k8bas.skyblockutility.skyblock.MenuReader
import com.k8bas.skyblockutility.skyblock.MenuSnapshot
import com.k8bas.skyblockutility.util.ChatUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.function.UnaryOperator

/**
 * `/ksu debug dump containers on|off` (T0.4b, REQ-GS-13). While armed, each allowlisted menu the
 * player opens is written to latest.log once its contents are stable. A command cannot be typed
 * while a menu is open, hence the arming. A subscriber of the menu reader (T3.0e), so it only reads
 * and never clicks, pages or opens anything. Disarms itself after 60 minutes (REQ-GS-12).
 */
object ContainerDump {
	private val ARM = CaptureArm(System::currentTimeMillis)
	private var captureCount = 0

	@JvmStatic
	fun register() {
		ClientTickEvents.END_CLIENT_TICK.register {
			if (ARM.expiredNow()) {
				ChatUtils.chat("Container dump disarmed after 60 minutes")
			}
		}
		MenuReader.subscribe("container-dump", { ARM.isArmed }, CaptureAllowlist::matches) { event ->
			if (event is MenuEvent.Shown) {
				write(++captureCount, event.menu)
			}
		}
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

	private fun write(capture: Int, menu: MenuSnapshot) {
		val masker = NameMasker.SESSION
		masker.learnFrom(Minecraft.getInstance())
		val mask = UnaryOperator<String> { masker.mask(it) ?: "" }
		for (line in ContainerDumpFormat.lines(capture, menu, mask)) {
			K8basSkyblockUtilityClient.LOGGER.info("{}", line)
		}
		ChatUtils.chat("Captured \"" + mask.apply(menu.title) + "\" to latest.log")
	}
}
