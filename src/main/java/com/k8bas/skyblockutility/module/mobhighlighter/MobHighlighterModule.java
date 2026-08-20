package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.Module;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rule fields used to live inside a NestedListListEntry cell (a list-of-lists, two levels of
 * nesting) — keyboard text input didn't reliably reach fields at that depth, while click-based
 * controls (toggles, the enum selector) worked fine. Each rule is now its own SubCategory
 * (one level of nesting, same depth as General's already-working fields) instead. Cloth Config
 * has no plain button widget (checked directly), so add/delete/open-database are all
 * "toggle then press Save" actions — the toggle firing its action and resetting to unchecked
 * afterward is expected (it's a momentary trigger, not persisted state), not a bug.
 */
public final class MobHighlighterModule implements Module {
	public static final String ID = "mob_highlighter";

	private static final int[] COLOR_PALETTE = {
			0xFF4500, 0x00BFFF, 0x00FF00, 0xFFD700, 0xFF00FF, 0x1E90FF, 0xFF69B4, 0x7FFF00
	};

	private MobHighlighterConfig config;
	private final List<HighlightRule> pendingDeletions = new ArrayList<>();
	private boolean pendingAdd = false;
	private boolean pendingOpenDatabase = false;
	private final Set<String> pendingMobAdds = new LinkedHashSet<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String displayName() {
		return "Mob Highlighter";
	}

	@Override
	public void onRegister() {
		config = ConfigManager.getModuleSection(ID, MobHighlighterConfig.class, MobHighlighterConfig::new);
		HighlightManager.setEnabled(config.enabled);
		HighlightManager.rebuild(config.rules);
		MobDatabase.fetchInBackground();
	}

	@Override
	public boolean isEnabled() {
		return config.enabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		config.enabled = enabled;
		HighlightManager.setEnabled(enabled);
		ConfigManager.putModuleSection(ID, config);
		ConfigManager.save();
	}

	@Override
	public void buildConfigScreen(ConfigCategory category, ConfigEntryBuilder entryBuilder) {
		pendingDeletions.clear();
		pendingAdd = false;
		pendingOpenDatabase = false;
		pendingMobAdds.clear();

		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("Enabled"), config.enabled)
				.setSaveConsumer(this::setEnabled)
				.build());

		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("Open Mob Database (toggle, then Save)"), false)
				.setSaveConsumer(value -> pendingOpenDatabase = value)
				.build());

		category.addEntry(entryBuilder.startBooleanToggle(Component.literal("Add a blank new rule (toggle, then Save)"), false)
				.setSaveConsumer(shouldAdd -> pendingAdd = shouldAdd)
				.build());

		for (HighlightRule rule : new ArrayList<>(config.rules)) {
			category.addEntry(buildRuleSubCategory(rule, entryBuilder));
		}
	}

	@Override
	public void onConfigScreenSaved() {
		config.rules.removeAll(pendingDeletions);
		pendingDeletions.clear();
		if (pendingAdd) {
			config.rules.add(new HighlightRule());
			pendingAdd = false;
		}
		ConfigManager.putModuleSection(ID, config);
		HighlightManager.rebuild(config.rules);

		if (pendingOpenDatabase) {
			pendingOpenDatabase = false;
			Minecraft client = Minecraft.getInstance();
			// Deferred to next tick, same reasoning as the settings keybind/command fix: opening
			// a screen synchronously here risks a stray input event closing it immediately while
			// Cloth Config's own screen-close transition is still resolving.
			client.execute(() -> client.setScreen(buildMobPickerScreen(client.screen)));
		}
	}

	/** A separate screen, opened via the toggle above, instead of inline in the main category —
	 *  with 200+ database entries, showing them all the time would swamp the module's own
	 *  settings. Reuses Cloth Config's own built-in screen search (verified against the jar) for
	 *  "searchable", rather than building custom filter UI. */
	private Screen buildMobPickerScreen(Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("Mob Database"))
				.setSavingRunnable(this::reconcileMobAdds);

		ConfigEntryBuilder entryBuilder = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.literal("Mob Database"));

		Map<String, List<MobDatabaseEntry>> byIsland = MobDatabase.byIsland();
		if (byIsland.isEmpty()) {
			category.addEntry(entryBuilder.startTextDescription(Component.literal(
					"Mob database hasn't finished loading yet — close and reopen this screen in a moment.")).build());
		}
		for (Map.Entry<String, List<MobDatabaseEntry>> island : byIsland.entrySet()) {
			category.addEntry(buildIslandSubCategory(island.getKey(), island.getValue(), entryBuilder));
		}

		return builder.build();
	}

	private AbstractConfigListEntry<?> buildIslandSubCategory(String island, List<MobDatabaseEntry> mobs, ConfigEntryBuilder entryBuilder) {
		SubCategoryBuilder sub = entryBuilder.startSubCategory(Component.literal(island)).setExpanded(false);

		Map<String, List<MobDatabaseEntry>> bySubfolder = new LinkedHashMap<>();
		List<MobDatabaseEntry> direct = new ArrayList<>();
		for (MobDatabaseEntry mob : mobs) {
			if (mob.subfolder != null) {
				bySubfolder.computeIfAbsent(mob.subfolder, key -> new ArrayList<>()).add(mob);
			} else {
				direct.add(mob);
			}
		}

		for (MobDatabaseEntry mob : direct) {
			sub.add(buildMobToggle(mob, entryBuilder));
		}
		for (Map.Entry<String, List<MobDatabaseEntry>> event : bySubfolder.entrySet()) {
			SubCategoryBuilder eventSub = entryBuilder.startSubCategory(Component.literal(event.getKey())).setExpanded(false);
			for (MobDatabaseEntry mob : event.getValue()) {
				eventSub.add(buildMobToggle(mob, entryBuilder));
			}
			sub.add(eventSub.build());
		}

		return sub.build();
	}

	private AbstractConfigListEntry<?> buildMobToggle(MobDatabaseEntry mob, ConfigEntryBuilder entryBuilder) {
		return entryBuilder.startBooleanToggle(Component.literal("Add rule: " + mob.displayName), false)
				.setSaveConsumer(shouldAdd -> {
					if (shouldAdd) {
						pendingMobAdds.add(mob.id);
					} else {
						pendingMobAdds.remove(mob.id);
					}
				})
				.build();
	}

	private void reconcileMobAdds() {
		int added = 0;
		for (String mobId : pendingMobAdds) {
			MobDatabaseEntry mob = MobDatabase.entries().stream()
					.filter(entry -> entry.id.equals(mobId))
					.findFirst()
					.orElse(null);
			if (mob != null) {
				config.rules.add(createRuleForMob(mob));
				added++;
			}
		}
		pendingMobAdds.clear();

		if (added > 0) {
			ConfigManager.putModuleSection(ID, config);
			ConfigManager.save();
			HighlightManager.rebuild(config.rules);

			int addedCount = added;
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal(
						"Added " + addedCount + " highlight rule" + (addedCount == 1 ? "" : "s") + " from the mob database."));
			}
		}
	}

	private HighlightRule createRuleForMob(MobDatabaseEntry mob) {
		HighlightRule rule = new HighlightRule();
		rule.label = mob.displayName;
		rule.namePattern = mob.matchText;
		rule.nameMatchMode = NameMatchMode.CONTAINS;
		rule.color = COLOR_PALETTE[config.rules.size() % COLOR_PALETTE.length];
		return rule;
	}

	private AbstractConfigListEntry<?> buildRuleSubCategory(HighlightRule rule, ConfigEntryBuilder entryBuilder) {
		SubCategoryBuilder sub = entryBuilder.startSubCategory(Component.literal(rule.label)).setExpanded(false);

		sub.add(entryBuilder.startStrField(Component.literal("Label"), rule.label)
				.setSaveConsumer(value -> rule.label = value)
				.build());
		sub.add(entryBuilder.startBooleanToggle(Component.literal("Enabled"), rule.enabled)
				.setSaveConsumer(value -> rule.enabled = value)
				.build());
		sub.add(entryBuilder.startStrField(Component.literal("Entity Type (blank = any)"), rule.entityTypeId == null ? "" : rule.entityTypeId)
				.setSaveConsumer(value -> rule.entityTypeId = value.isBlank() ? null : value)
				.build());
		sub.add(entryBuilder.startEnumSelector(Component.literal("Name Match Mode"), NameMatchMode.class, rule.nameMatchMode)
				.setSaveConsumer(value -> rule.nameMatchMode = value)
				.build());
		sub.add(entryBuilder.startStrField(Component.literal("Name Pattern"), rule.namePattern)
				.setSaveConsumer(value -> rule.namePattern = value)
				.build());
		// Hex/preview/R/G/B all ultimately write the same rule.color int. Each captures its own
		// initial value at build time and only applies on save if it actually changed — otherwise,
		// whichever field the user *didn't* touch would unconditionally re-apply its stale
		// snapshot and clobber an edit made through one of the others (e.g. editing R/G/B would
		// get silently undone by hex's untouched original value re-saving over it, or vice versa).
		int initialColor = rule.color;
		String initialHex = String.format("%06X", initialColor & 0xFFFFFF);
		int initialRed = (initialColor >> 16) & 0xFF;
		int initialGreen = (initialColor >> 8) & 0xFF;
		int initialBlue = initialColor & 0xFF;

		sub.add(entryBuilder.startStrField(Component.literal("Color (hex RRGGBB)"), initialHex)
				.setErrorSupplier(value -> isValidHexColor(value) ? Optional.empty() : Optional.of(Component.literal("Expected 6 hex digits, e.g. FF4500")))
				.setSaveConsumer(value -> {
					if (isValidHexColor(value) && !value.equalsIgnoreCase(initialHex)) {
						rule.color = Integer.parseInt(value, 16);
					}
				})
				.build());
		sub.add(entryBuilder.startColorField(Component.literal("Color Preview"), initialColor)
				.setSaveConsumer(value -> {
					if (value != initialColor) {
						rule.color = value;
					}
				})
				.build());
		sub.add(entryBuilder.startIntField(Component.literal("Color Red"), initialRed)
				.setMin(0).setMax(255)
				.setSaveConsumer(value -> {
					if (value != initialRed) {
						rule.color = (rule.color & 0x00FFFF) | (value << 16);
					}
				})
				.build());
		sub.add(entryBuilder.startIntField(Component.literal("Color Green"), initialGreen)
				.setMin(0).setMax(255)
				.setSaveConsumer(value -> {
					if (value != initialGreen) {
						rule.color = (rule.color & 0xFF00FF) | (value << 8);
					}
				})
				.build());
		sub.add(entryBuilder.startIntField(Component.literal("Color Blue"), initialBlue)
				.setMin(0).setMax(255)
				.setSaveConsumer(value -> {
					if (value != initialBlue) {
						rule.color = (rule.color & 0xFFFF00) | value;
					}
				})
				.build());
		sub.add(entryBuilder.startDoubleField(Component.literal("Max Distance (0 = unlimited)"), rule.maxDistance)
				.setMin(0)
				.setSaveConsumer(value -> rule.maxDistance = value)
				.build());
		sub.add(entryBuilder.startBooleanToggle(Component.literal("Delete this rule (toggle, then Save)"), false)
				.setSaveConsumer(shouldDelete -> {
					if (shouldDelete) {
						pendingDeletions.add(rule);
					} else {
						pendingDeletions.remove(rule);
					}
				})
				.build());

		return sub.build();
	}

	private static boolean isValidHexColor(String value) {
		return value != null && value.matches("[0-9A-Fa-f]{6}");
	}
}
