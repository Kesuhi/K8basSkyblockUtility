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
import com.k8bas.skyblockutility.ui.option.IntSlider
import com.k8bas.skyblockutility.ui.option.Option
import com.k8bas.skyblockutility.ui.option.OptionStatus
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.Toggle
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

/** The hotspot settings (REQ-HOT-12, T3.6 part): all ON in a fresh config (D-6); the fill at 25 % opacity. */
class HotspotConfig {
	@JvmField
	var enabled = true

	@JvmField
	var ring = true

	@JvmField
	var fill = true

	@JvmField
	var fillOpacity = DEFAULT_OPACITY

	companion object {
		const val DEFAULT_OPACITY = 25
	}
}

/**
 * Fishing hotspots (T3.6, REQ-HOT-01..REQ-HOT-06, REQ-HOT-12, REQ-HOT-13): on SkyBlock, every hotspot stand whose
 * name tag vanilla displays gets a depth-tested ring at the liquid surface in its buff's colour, with an optional
 * filled disc. The radius comes from the server's ring particles, observed read-only (`mixin/ParticleObserverMixin`);
 * without them the island's fallback is used. Display only: nothing is sent (REQ-HOT-11).
 */
class HotspotModule : Module {
	private var config: HotspotConfig? = null
	private val cfg: HotspotConfig
		get() = config!!

	private val tracker = HotspotTracker(HotspotText { LOGGER.info("Fishing hotspot: unknown buff text '{}', shown white", it) }) { x, y, z -> surface(x, y, z) }
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

	override fun cards(): List<Card> = cards(cfg, Consumer { setEnabled(it) }, Supplier { status() }, Runnable { ConfigManager.putModuleSection(ID, cfg) })

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
		tracker.scan(stands, ticks)
	}

	private fun particle(options: ParticleOptions, x: Double, y: Double, z: Double) {
		if (!active()) {
			return
		}
		val kind = when (options.type) {
			ParticleTypes.DUST -> HotspotParticles.Kind.DUST
			ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE -> HotspotParticles.Kind.SMOKE
			else -> HotspotParticles.Kind.OTHER
		}
		val colour = (options as? DustParticleOptions)?.color
		if (HotspotParticles.isRing(kind, colour?.x ?: 0f, colour?.y ?: 0f, colour?.z ?: 0f, IslandTracker.getCurrentIsland())) {
			tracker.particle(x, y, z)
		}
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
		private val LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/hotspot")

		@Volatile
		private var instance: HotspotModule? = null

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
			)
			return java.util.List.of(Card(ID, Category.FISHING, "Fishing Hotspots", enabled, options))
		}
	}
}
