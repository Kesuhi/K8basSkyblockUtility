package com.k8bas.skyblockutility.module.drillfix

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
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import java.util.function.Consumer
import java.util.function.Supplier

/** The drill fix's setting (REQ-DRILL-06): ON in a fresh config (D-4, D-6). */
class DrillFixConfig {
	@JvmField
	var enabled = true
}

/**
 * The drill re-equip fix (T3.7, REQ-DRILL-01..REQ-DRILL-08): on SkyBlock, when Hypixel refreshes the drill in your hand
 * (its fuel or lore changes), the first-person hand no longer lowers and raises. Display only: no block breaking, game
 * mode or packet is touched (REQ-DRILL-03); the fuel HUD of D-4 (c) is not built.
 */
class DrillFixModule : Module {
	private var config: DrillFixConfig? = null
	private val cfg: DrillFixConfig
		get() = config!!

	override fun id(): String = ID

	override fun onRegister() {
		config = ConfigManager.getModuleSection(ID, DrillFixConfig::class.java) { DrillFixConfig() }
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
		const val ID = "drill_fix"

		/** The hand-swap hook (REQ-DRILL-07). */
		const val HOOK = "drill.refresh"

		@Volatile
		private var instance: DrillFixModule? = null

		/** The components a refresh may change: the custom data (fuel), the lore and the damage. */
		// Lazy: the game's component registry is not there in unit tests, which build the card only.
		private val REFRESHED by lazy { setOf(DataComponents.CUSTOM_DATA, DataComponents.LORE, DataComponents.DAMAGE) }

		/** From the hook: vanilla's decision, or true for a refreshed copy of the same drill (DrillRefresh). */
		@JvmStatic
		fun instant(original: Boolean, shown: ItemStack, next: ItemStack): Boolean = DrillRefresh.instant(
			Supplier { view(shown) }, Supplier { view(next) }, IslandTracker.snapshot().onSkyBlock, instance?.cfg?.enabled == true, original,
		)

		/** What the decision compares; null for an empty stack or one without SkyBlock data. */
		private fun view(stack: ItemStack): DrillRefresh.Stack? {
			if (stack.isEmpty) {
				return null
			}
			val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
			val types = stack.components.keySet().mapTo(HashSet()) { BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(it).toString() }
			// What a refresh may change is left out (REQ-DRILL-01); every other value must stay the same.
			val others = HashMap<String, Any?>()
			for (component in stack.components) {
				if (component.type() !in REFRESHED) {
					others[BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).toString()] = component.value()
				}
			}
			return DrillRefresh.Stack(
				BuiltInRegistries.ITEM.getKey(stack.item).toString(), types, tag.getStringOr("id", ""), tag.getStringOr("uuid", ""),
				tag.contains(DrillRefresh.FUEL_KEY), others,
			)
		}

		@JvmStatic
		fun status(): OptionStatus = HookStatus.REGISTRY.status(HOOK)

		/** The Mining card (REQ-DRILL-06); the tooltip names the same fix in Skyblocker and NoFrills. */
		@JvmStatic
		@JvmOverloads
		fun cards(config: DrillFixConfig, setEnabled: Consumer<Boolean>, status: Supplier<OptionStatus> = Supplier { OptionStatus.OK }): List<Card> {
			val toggle = Toggle.of(
				"$ID.enabled", "modules.$ID.enabled",
				OptionText(
					"Drill re-equip fix", "Your drill no longer dips out of view and back each time its fuel changes.",
					"Only drills, and only the same drill: switching to another item or another drill still shows the swap. " +
						"Skyblocker (\"Cancel Component Update Animation\") and NoFrills (\"No Equip Animation\") offer the same; " +
						"turning this one off does not turn theirs off. Mining itself is not changed.",
					java.util.List.of("drill", "mining", "equip", "animation", "hand", "fuel"),
				),
				true, Binding.of({ config.enabled }, setEnabled),
			).withStatus(status)
			return java.util.List.of(Card(ID, Category.MINING, "Drill re-equip fix", toggle, java.util.List.of()))
		}
	}
}
