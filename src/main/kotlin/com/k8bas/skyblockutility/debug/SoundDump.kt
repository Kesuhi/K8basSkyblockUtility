package com.k8bas.skyblockutility.debug

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient
import com.k8bas.skyblockutility.util.ChatUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundEventListener
import net.minecraft.client.sounds.WeighedSoundEvents

/**
 * `/ksu debug dump sounds on|off` (T3.3c, REQ-GS-13): while armed, every sound the client plays is written to
 * latest.log as one line (SoundDumpFormat), so the Organ Donor ding can be told apart from other sounds. It listens
 * the way vanilla's subtitles do (a sound event listener), so it only reads: no sound is changed, delayed or
 * cancelled, and nothing is sent. Disarms itself after 10 minutes (REQ-GS-12); writes nothing unless armed.
 */
object SoundDump {
	const val ARMED_MS: Long = 10L * 60 * 1000

	private var armedAt = 0L
	private val ARM = CaptureArm(System::currentTimeMillis, ARMED_MS)
	private var captureCount = 0

	private val LISTENER = SoundEventListener { sound: SoundInstance, _: WeighedSoundEvents, range: Float ->
		if (ARM.isArmed) {
			write(sound, range)
		}
	}

	@JvmStatic
	fun register() {
		// The sound manager exists once the client has started.
		ClientLifecycleEvents.CLIENT_STARTED.register { client -> client.soundManager.addListener(LISTENER) }
		ClientTickEvents.END_CLIENT_TICK.register {
			if (ARM.expiredNow()) {
				ChatUtils.chat("Sound dump disarmed after 10 minutes")
			}
		}
	}

	@JvmStatic
	fun arm() {
		ARM.arm()
		armedAt = System.currentTimeMillis()
		captureCount = 0
		ChatUtils.chat("Sound dump armed for 10 minutes; every sound you hear is written to latest.log")
	}

	@JvmStatic
	fun disarm() {
		ARM.disarm()
		ChatUtils.chat("Sound dump disarmed")
	}

	private fun write(sound: SoundInstance, range: Float) {
		val client = Minecraft.getInstance()
		val player = client.player?.let { doubleArrayOf(it.x, it.y, it.z) }
		// Resolved before the listeners run; "-" if a sound has none.
		val file = sound.sound?.location?.toString() ?: "-"
		val line = SoundDumpFormat.line(
			++captureCount, System.currentTimeMillis() - armedAt, client.level?.gameTime ?: 0,
			SoundDumpFormat.Sound(
				sound.identifier.toString(), file, sound.source.getName(), sound.x, sound.y, sound.z, sound.isRelative,
				sound.attenuation.name, sound.volume, sound.pitch, range,
			),
			player,
		)
		K8basSkyblockUtilityClient.LOGGER.info("{}", line)
	}
}
