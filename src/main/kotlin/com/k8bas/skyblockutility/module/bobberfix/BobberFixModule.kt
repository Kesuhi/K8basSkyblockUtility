package com.k8bas.skyblockutility.module.bobberfix

import com.k8bas.skyblockutility.config.ConfigManager
import com.k8bas.skyblockutility.hook.HookStatus
import com.k8bas.skyblockutility.location.IslandTracker
import com.k8bas.skyblockutility.module.Module
import com.k8bas.skyblockutility.ui.option.Binding
import com.k8bas.skyblockutility.ui.option.Card
import com.k8bas.skyblockutility.ui.option.Category
import com.k8bas.skyblockutility.ui.option.OptionStatus
import com.k8bas.skyblockutility.ui.option.OptionText
import com.k8bas.skyblockutility.ui.option.Toggle
import java.util.function.Consumer
import java.util.function.Supplier

/** The bobber fix's setting (REQ-BOB-07): ON in a fresh config (D-6). */
class BobberFixConfig {
	@JvmField
	var enabled = true
}

/**
 * The bobber rubber-band fix (T3.5, REQ-BOB-01..REQ-BOB-08): on SkyBlock, your own bobber is not attached to
 * Hypixel's bite-timer stand and floats on lava as on water. Two optional hooks in FishingHook do the work
 * (`mixin/FishingHookMixin`); they ask [enabled] and [onSkyBlock] each time, so a change applies at the next data
 * update or cast.
 * Presentation only: the entity data is applied unchanged, nothing is sent (REQ-BOB-04, REQ-XC-RULES-02).
 * Courtesy credit: SkyOcean has the same fix; no code is copied.
 */
class BobberFixModule : Module {
	private var config: BobberFixConfig? = null
	private val cfg: BobberFixConfig
		get() = config!!

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, BobberFixConfig::class.java) { BobberFixConfig() }
		instance = this
	}

	override fun isEnabled(): Boolean = cfg.enabled

	override fun setEnabled(enabled: Boolean) {
		cfg.enabled = enabled
		ConfigManager.putModuleSection(ID, cfg)
		ConfigManager.save()
	}

	override fun cards(): List<Card> = cards(cfg, Consumer { setEnabled(it) }, Supplier { status() })

	override fun onSettingsClosed() {
		ConfigManager.putModuleSection(ID, cfg)
	}

	companion object {
		const val ID = "bobber_fix"

		/** The hook that skips the attach to the timer stand, and the one that floats the hook on lava. */
		const val LINK_HOOK = "bobber.link"
		const val LAVA_HOOK = "bobber.lava"

		@Volatile
		private var instance: BobberFixModule? = null

		/** The toggle now; off until the module is registered. */
		@JvmStatic
		fun enabled(): Boolean = instance?.cfg?.enabled == true

		/** On SkyBlock by the location service; an unknown location is not (REQ-BOB-05, EC-BOB-06). */
		@JvmStatic
		fun onSkyBlock(): Boolean = IslandTracker.snapshot().onSkyBlock

		/** Unavailable when either hook was skipped (REQ-BOB-08); the first skipped one's text. */
		@JvmStatic
		fun status(): OptionStatus {
			val states = listOf(HookStatus.REGISTRY.status(LINK_HOOK), HookStatus.REGISTRY.status(LAVA_HOOK))
			return states.firstOrNull { it.state == OptionStatus.State.UNAVAILABLE }
				?: states.firstOrNull { it.state != OptionStatus.State.OK }
				?: OptionStatus.OK
		}

		/** The Fishing card (REQ-BOB-07), with the AMBER tooltip (P6); [status] shows a skipped hook. */
		@JvmStatic
		@JvmOverloads
		fun cards(config: BobberFixConfig, setEnabled: Consumer<Boolean>, status: Supplier<OptionStatus> = Supplier { OptionStatus.OK }): List<Card> {
			val toggle = Toggle.of(
				"$ID.enabled", "modules.$ID.enabled",
				OptionText(
					"Bobber Fix", "Your bobber floats smoothly on SkyBlock, also on lava, instead of jumping back every moment.",
					"Only how your own bobber is drawn changes: it is no longer pinned to Hypixel's bite-timer stand, and it floats " +
						"on lava as on water. The server still decides where it is, when a fish bites and what you catch. " +
						"Applies from your next cast. Other players' bobbers and other servers stay unchanged. Thanks to SkyOcean " +
						"for finding the cause.",
					java.util.List.of("bobber", "fishing", "rod", "lava", "jitter", "rubber band"),
				),
				true, Binding.of({ config.enabled }, setEnabled),
			).asAmber().withStatus(status)
			return java.util.List.of(Card(ID, Category.FISHING, "Bobber Fix", toggle, java.util.List.of()))
		}
	}
}
