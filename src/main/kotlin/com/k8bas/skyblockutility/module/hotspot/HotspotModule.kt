package com.k8bas.skyblockutility.module.hotspot

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.hook.HookStatus
import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.render.marker.Marker
import com.k8bas.skyblockutility.render.marker.MarkerAnchor
import com.k8bas.skyblockutility.render.marker.MarkerProvider
import com.k8bas.skyblockutility.render.marker.MarkerRing
import com.k8bas.skyblockutility.render.marker.WorldMarkers
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.Choice
import com.k8bas.skyblockutility.ui.option.IntSlider
import com.k8bas.skyblockutility.ui.option.Option
import com.k8bas.skyblockutility.ui.option.OptionStatus
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.Toggle
import com.k8bas.skyblockutility.ui.notice.Notice
import com.k8bas.skyblockutility.ui.notice.NoticeChannel
import com.k8bas.skyblockutility.ui.notice.Notices
import com.k8bas.skyblockutility.util.ChatUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * The hotspot settings (REQ-HOT-12): the rings (T3.6) and the "hotspot gone" warning with its channels (T3.6b), ON in
 * a fresh config (D-6) except the notice (R12); the fill at 25 % opacity, the sound at full volume.
 */
class HotspotConfig {
	@JvmField
	var enabled = true

	@JvmField
	var ring = true

	@JvmField
	var fill = true

	@JvmField
	var fillOpacity = DEFAULT_OPACITY

	/** Hypixel's own ring particles are not drawn (REQ-HOT-14, R41 (b)). */
	@JvmField
	var hideParticles = true

	@JvmField
	var warning = true

	@JvmField
	var warnTitle = true

	@JvmField
	var warnSound = true

	@JvmField
	var warnChat = true

	@JvmField
	var warnNotice = false

	/** Null only in a file Gson could not map (an unknown name); played as the default. */
	@JvmField
	var sound: HotspotSound? = HotspotSound.PLING

	@JvmField
	var soundVolume = 100

	companion object {
		const val DEFAULT_OPACITY = 25
	}
}

/**
 * Fishing hotspots (T3.6, REQ-HOT-01..REQ-HOT-06, REQ-HOT-12, REQ-HOT-13): on SkyBlock, every hotspot stand whose
 * name tag vanilla displays gets a depth-tested ring at the liquid surface in its buff's colour, with an optional
 * filled disc. The radius comes from the server's ring particles, observed read-only (`mixin/ParticleObserverMixin`);
 * without them the island's fallback is used. When a hotspot you were fishing in is gone, a local warning (T3.6b,
 * REQ-HOT-07..REQ-HOT-10). Display only: nothing is sent (REQ-HOT-11).
 */
class HotspotModule : Module {
	private var config: HotspotConfig? = null
	private val cfg: HotspotConfig
		get() = config!!

	private val tracker = HotspotTracker(
		HotspotText { LOGGER.info("Fishing hotspot: unknown buff text '{}', shown white", it) },
		{ x, y, z -> surface(x, y, z) },
		{ spot -> gone(spot) },
	)
	private var ticks = 0L

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, HotspotConfig::class.java) { HotspotConfig() }
		instance = this
		ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
		// Every location change is a server change (REQ-HOT-06); a world change the location service does not see too.
		IslandTracker.onChange { tracker.clear() }
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ -> tracker.clear() }
		ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { tracker.clear() } }
		WorldMarkers.add(PROVIDER)
	}

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		cfg.enabled = enabled
		if (!enabled) {
			tracker.clear()
		}
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
	}

	override fun cards(): List<Card> = cards(cfg, Consumer { setEnabled(it) }, Supplier { status() }, Runnable { ConfigManager.putModuleSection(ID, cfg) },
		Supplier { HookStatus.REGISTRY.status(HIDE_HOOK) })

	override fun onSettingsClosed() {
		ConfigManager.putModuleSection(ID, cfg)
	}

	/** For gametests: the hotspots now. */
	fun hotspotsForTest(): List<HotspotTracker.Hotspot> = tracker.hotspots()

	private fun active(): Boolean = cfg.enabled && IslandTracker.snapshot().onSkyBlock

	private fun tick(client: Minecraft) {
		ticks++
		val level = client.level
		if (!active() || level == null) {
			tracker.clear()
			return
		}
		val stands = ArrayList<HotspotTracker.Stand>()
		for (entity in level.entitiesForRendering()) {
			if (entity is ArmorStand) {
				val name = entity.customName
				stands.add(HotspotTracker.Stand(entity.id, entity.x, entity.y, entity.z, name?.string ?: "", name != null && entity.isCustomNameVisible))
			}
		}
		val player = client.player
		tracker.scan(stands, ticks, player?.let { HotspotTracker.Position(it.x, it.y, it.z) })
		// Your own bobber only (R9); every tick, so at least every 10 (REQ-HOT-07).
		player?.fishing?.let { tracker.fished(it.x, it.y, it.z, ticks, IslandTracker.getCurrentIsland()) }
	}

	/** A hotspot you were fishing in is gone: the warning, if it and the module are on, on SkyBlock (REQ-HOT-08 (1)). */
	private fun gone(spot: HotspotTracker.Hotspot) {
		if (cfg.warning && active()) {
			HotspotAlert.fire(spot.buff, cfg, GAME_OUTPUT)
		}
	}

	private fun particle(options: ParticleOptions, x: Double, y: Double, z: Double) {
		if (active() && ringKind(options)) {
			tracker.particle(x, y, z)
		}
	}

	/**
	 * REQ-HOT-14: a ring particle of a hotspot whose ring this client draws now (ring or disc on, its name tag shown),
	 * while "Hide Hypixel's hotspot particles" is on. Without a drawn ring the particles stay, so the area never vanishes.
	 */
	private fun hides(options: ParticleOptions, x: Double, y: Double, z: Double): Boolean {
		if (!cfg.hideParticles || !active() || !ringKind(options)) {
			return false
		}
		val island = IslandTracker.getCurrentIsland()
		val spot = tracker.ringParticleOf(x, y, z, island) ?: return false
		return ringDrawn(spot, island)
	}

	/** Whether [markers] draws [spot]'s ring this frame: a ring or disc is on and vanilla shows its name tag (R2). */
	private fun ringDrawn(spot: HotspotTracker.Hotspot, island: String?): Boolean {
		val client = Minecraft.getInstance()
		val stand = client.level?.getEntity(spot.standId) as? ArmorStand ?: return false
		val camera = client.cameraEntity ?: return false
		return nameTagShown(stand, camera) && ring(cfg, spot.buff.color, spot.radius(island)) != null
	}

	private fun ringKind(options: ParticleOptions): Boolean {
		val kind = when (options.type) {
			ParticleTypes.DUST -> HotspotParticles.Kind.DUST
			ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE -> HotspotParticles.Kind.SMOKE
			else -> HotspotParticles.Kind.OTHER
		}
		val colour = (options as? DustParticleOptions)?.color
		return HotspotParticles.isRing(kind, colour?.x ?: 0f, colour?.y ?: 0f, colour?.z ?: 0f, IslandTracker.getCurrentIsland())
	}

	/** The water or lava surface under a stand (REQ-HOT-03, HotspotSurface); null while none is loaded. */
	private fun surface(x: Double, y: Double, z: Double): Double? {
		val level: Level = Minecraft.getInstance().level ?: return null
		val pos = BlockPos.MutableBlockPos()
		return HotspotSurface.find(y) { blockY ->
			pos.set(Math.floor(x).toInt(), blockY, Math.floor(z).toInt())
			val fluid = level.getFluidState(pos)
			if (fluid.`is`(FluidTags.WATER) || fluid.`is`(FluidTags.LAVA)) fluid.getHeight(level, pos) else null
		}
	}

	private fun markers(out: Consumer<Marker>) {
		val client = Minecraft.getInstance()
		val level = client.level ?: return
		val camera = client.cameraEntity ?: return
		val island = IslandTracker.getCurrentIsland()
		for (spot in tracker.hotspots()) {
			if (spot.removedAt != null) {
				continue
			}
			// Only on the hologram itself, while vanilla shows its name tag (R2, MarkerRing): its flag and its distance.
			// The particle hiding asks the same (ringDrawn).
			val stand = level.getEntity(spot.standId) as? ArmorStand ?: continue
			if (!nameTagShown(stand, camera)) {
				continue
			}
			val ring = ring(cfg, spot.buff.color, spot.radius(island)) ?: continue
			out.accept(Marker(MarkerAnchor.entity(stand, spot.surfaceY() - stand.y), null, null, ring))
		}
	}

	companion object {
		const val ID = "fishing_hotspots"

		/** The read-only particle observer (REQ-HOT-13). */
		const val PARTICLE_HOOK = "hotspot.particles"

		/** The hook that skips drawing Hypixel's ring particles (REQ-HOT-14). */
		const val HIDE_HOOK = "hotspot.hide"
		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/hotspot")

		@Volatile
		private var instance: HotspotModule? = null

		/** The game's local channels (REQ-HOT-10, REQ-HOT-11): the HUD title, a UI sound, the chat helper, a notice. */
		private val GAME_OUTPUT = object : HotspotAlert.Output {
			override fun title(title: String, subtitle: String) {
				val hud = Minecraft.getInstance().gui.hud
				// Vanilla's own timings (3.5 s on screen, so at least 2 s, REQ-HOT-10); the shared title timings are left at
				// vanilla's defaults for every later title, as the NPC Search title does.
				hud.resetTitleTimes()
				hud.setSubtitle(Component.literal(subtitle).withStyle(ChatFormatting.YELLOW))
				hud.setTitle(Component.literal(title).withStyle(ChatFormatting.GOLD))
			}

			override fun sound(sound: HotspotSound, volume: Float) {
				val event = when (sound) {
					HotspotSound.PLING -> SoundEvents.NOTE_BLOCK_PLING.value()
					HotspotSound.BELL -> SoundEvents.NOTE_BLOCK_BELL.value()
					HotspotSound.CHIME -> SoundEvents.NOTE_BLOCK_CHIME.value()
					HotspotSound.EXPERIENCE -> SoundEvents.EXPERIENCE_ORB_PICKUP
					HotspotSound.ANVIL -> SoundEvents.ANVIL_LAND
				}
				Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event, 1.0f, volume))
			}

			override fun chat(line: String) {
				ChatUtils.chat(line)
			}

			override fun notice(title: String, body: String) {
				Notices.post(Notice(title, java.util.List.of(body)))
			}
		}

		private val PROVIDER = object : MarkerProvider {
			override fun isActive(): Boolean = instance?.let { it.active() && (it.cfg.ring || it.cfg.fill) } == true

			override fun collect(out: Consumer<Marker>) {
				instance?.markers(out)
			}

			override fun reset() {
				instance?.tracker?.clear()
			}
		}

		/**
		 * Whether vanilla draws the stand's name tag: custom name visible and nearer the camera than its name tag
		 * distance (ArmorStandRenderer.shouldShowName and EntityRenderer.extractNameTags).
		 */
		private fun nameTagShown(stand: ArmorStand, camera: net.minecraft.world.entity.Entity): Boolean {
			val range = stand.getAttributeValue(Attributes.NAME_TAG_DISTANCE)
			return stand.isCustomNameVisible && camera.distanceToSqr(stand) < range * range
		}

		/** From the particle observer, on the client thread, once per packet; it never changes the packet. */
		@JvmStatic
		fun onParticles(options: ParticleOptions, x: Double, y: Double, z: Double) {
			instance?.particle(options, x, y, z)
		}

		/**
		 * From the particle hook, on the client thread, for each particle a packet would add: true to skip drawing it
		 * (REQ-HOT-14). The packet itself was handled and observed as always; only the client's particle is not made.
		 */
		@JvmStatic
		fun hidesParticle(options: ParticleOptions, x: Double, y: Double, z: Double): Boolean = instance?.hides(options, x, y, z) == true

		/**
		 * The ring a hotspot gets (REQ-HOT-05): the outline when "Highlight ring" is on, the disc when "Filled area" is
		 * on at an opacity above 0 % (a disc at alpha 0 would be refused); null when neither is drawn.
		 */
		@JvmStatic
		fun ring(config: HotspotConfig, rgb: Int, radius: Double): MarkerRing? {
			val disc = config.fill && config.fillOpacity > 0
			val style = when {
				config.ring && disc -> MarkerRing.Style.BOTH
				config.ring -> MarkerRing.Style.OUTLINE
				disc -> MarkerRing.Style.DISC
				else -> return null
			}
			val alpha = if (disc) Math.round(config.fillOpacity.coerceIn(0, 100) * 255 / 100f) else 0
			return MarkerRing(radius, (alpha shl 24) or (rgb and 0xFFFFFF), style)
		}

		/** Partial when the particle observer was skipped: rings then use the island's fallback radius (REQ-HOT-13). */
		@JvmStatic
		fun status(): OptionStatus = when (HookStatus.REGISTRY.status(PARTICLE_HOOK).state) {
			OptionStatus.State.UNAVAILABLE -> OptionStatus(OptionStatus.State.PARTIAL, "Radius estimation unavailable: rings use each island's usual size")
			else -> OptionStatus.OK
		}

		/** The Fishing card (REQ-HOT-12, the T3.6 part); every change applies on the next frame. */
		@JvmStatic
		@JvmOverloads
		fun cards(
			config: HotspotConfig,
			setEnabled: Consumer<Boolean>,
			status: Supplier<OptionStatus> = Supplier { OptionStatus.OK },
			changed: Runnable = Runnable { },
			hideStatus: Supplier<OptionStatus> = Supplier { OptionStatus.OK },
		): List<Card> {
			val keywords = java.util.List.of("hotspot", "fishing", "ring", "buff")
			val enabled = Toggle.of(
				"$ID.enabled", "modules.$ID.enabled",
				OptionText(
					"Fishing Hotspots", "A ring on the water or lava around each fishing hotspot, in its buff's colour.",
					"Rings sit on the liquid around the hotspots whose name you can see, and walls hide them. Their size comes " +
						"from the hotspot's own particles.",
					keywords,
				),
				true, Binding.of({ config.enabled }, setEnabled),
			).withStatus(status)
			val warnings = java.util.List.of("hotspot", "fishing", "warning", "gone", "alert")
			val options: List<Option> = java.util.List.of(
				Toggle.of(
					"$ID.ring", "modules.$ID.ring",
					OptionText("Highlight ring", "The outline of each hotspot's area.", "", keywords),
					true, Binding.of({ config.ring }, { value -> config.ring = value; changed.run() }),
				),
				Toggle.of(
					"$ID.fill", "modules.$ID.fill",
					OptionText("Filled area", "A see-through disc over each hotspot's area.", "", keywords),
					true, Binding.of({ config.fill }, { value -> config.fill = value; changed.run() }),
				),
				IntSlider.of(
					"$ID.fill_opacity", "modules.$ID.fillOpacity",
					OptionText("Fill opacity", "How strongly the filled area is drawn. 0 % leaves only the ring.", "", keywords),
					HotspotConfig.DEFAULT_OPACITY, 0, 100, 5, "%", "",
					Binding.of({ config.fillOpacity }, { value -> config.fillOpacity = value; changed.run() }),
				),
				Toggle.of(
					"$ID.hide_particles", "modules.$ID.hideParticles",
					OptionText(
						"Hide Hypixel's hotspot particles", "The pink (or smoke) particle ring around a hotspot is not drawn; the ring shows the area.",
						"Only the ring particles of a hotspot whose ring or filled area is drawn are skipped, on your screen only: with both " +
							"off they show again. The game still receives them, and the ring's size is still measured from them.",
						keywords,
					),
					true, Binding.of({ config.hideParticles }, { value -> config.hideParticles = value; changed.run() }),
				).withStatus(hideStatus),
				Toggle.of(
					"$ID.warning", "modules.$ID.warning",
					OptionText(
						"Hotspot gone warning", "Tells you when the hotspot you are fishing in disappears.",
						"Only for a hotspot your bobber was in during the last 30 seconds, while you are within 40 blocks of it. " +
							"Leaving the island or the server is not counted. Only you see or hear it.",
						warnings,
					),
					true, Binding.of({ config.warning }, { value -> config.warning = value; changed.run() }),
				),
				channel("$ID.warn_title", "modules.$ID.warnTitle", "Warning title", "\"Hotspot gone!\" on screen for a few seconds, with the buff.",
					warnings, true, Binding.of({ config.warnTitle }, { value -> config.warnTitle = value; changed.run() })),
				channel("$ID.warn_sound", "modules.$ID.warnSound", "Warning sound", "A short sound that only you hear.",
					warnings, true, Binding.of({ config.warnSound }, { value -> config.warnSound = value; changed.run() })),
				Choice.of(
					"$ID.sound", "modules.$ID.sound", OptionText("Sound", "Which sound the warning plays.", "", warnings),
					HotspotSound.PLING, java.util.List.of(*HotspotSound.entries.toTypedArray()), java.util.function.Function { it.label },
					Binding.of({ config.sound ?: HotspotSound.PLING }, { value -> config.sound = value; changed.run() }),
				),
				IntSlider.of(
					"$ID.sound_volume", "modules.$ID.soundVolume", OptionText("Sound volume", "How loud the warning sound is.", "", warnings),
					100, 0, 100, 5, "%", "", Binding.of({ config.soundVolume }, { value -> config.soundVolume = value; changed.run() }),
				),
				channel("$ID.warn_chat", "modules.$ID.warnChat", "Warning chat message", "A chat line that only you see.",
					warnings, true, Binding.of({ config.warnChat }, { value -> config.warnChat = value; changed.run() })),
				// The shared "also show as a notice" channel (T2.3d, REQ-UI-21), OFF (R12).
				NoticeChannel.option("$ID.warn_notice", "modules.$ID.warnNotice", "the hotspot you are fishing in is gone",
					Binding.of({ config.warnNotice }, { value -> config.warnNotice = value; changed.run() })),
			)
			return java.util.List.of(Card(ID, Category.FISHING, "Fishing Hotspots", enabled, options))
		}

		private fun channel(id: String, key: String, title: String, description: String, keywords: List<String>, default: Boolean,
			binding: Binding<Boolean>): Toggle = Toggle.of(id, key, OptionText(title, description, "", keywords), default, binding)
	}
}
