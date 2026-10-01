package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.highlight.HighlightRule;
import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.settings.SettingsScreenFactory;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.ClothConfigTabButton;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import me.shedaniel.clothconfig2.gui.entries.TextListEntry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decision 2026-10-01 (G1, S-6), REQ-GLOW-10 [C]: a Mob Highlighter rule that ignores names and has
 * no entity type is marked in the rule editor (a warning sign in its title, a red explanation inside)
 * and does nothing; a valid rule next to it is not marked.
 */
public class RuleWarningGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		MobHighlighterModule module = context.computeOnClient(client -> ModuleManager.modules().stream()
				.filter(MobHighlighterModule.class::isInstance).map(MobHighlighterModule.class::cast).findFirst().orElseThrow());
		List<HighlightRule> saved = context.computeOnClient(client -> module.rulesForTest());
		try {
			context.runOnClient(client -> {
				HighlightRule everything = new HighlightRule();
				everything.label = "Everything";
				everything.nameMatchMode = NameMatchMode.NONE;
				HighlightRule zealot = new HighlightRule();
				zealot.label = "Zealot";
				zealot.nameMatchMode = NameMatchMode.CONTAINS;
				zealot.namePattern = "Zealot";
				module.useRulesForTest(List.of(everything, zealot));
				client.gui.setScreen(SettingsScreenFactory.build(null));
			});
			context.waitTicks(2);
			// Cloth's tab buttons are not in the screen's widget list; press the tab directly.
			context.runOnClient(client -> {
				try {
					java.lang.reflect.Field tabs = ClothConfigScreen.class.getDeclaredField("tabButtons");
					tabs.setAccessible(true);
					// Found first: pressing a tab re-initialises the screen and its tab list.
					ClothConfigTabButton mobTab = ((List<?>) tabs.get(client.gui.screen())).stream()
							.map(ClothConfigTabButton.class::cast)
							.filter(button -> "Mob Highlighter".equals(button.getMessage().getString()))
							.findFirst().orElseThrow();
					mobTab.onPress(null);
				} catch (ReflectiveOperationException e) {
					throw new AssertionError("cannot select the Mob Highlighter tab", e);
				}
			});
			context.waitTicks(2);

			String[] found = context.computeOnClient(client -> {
				ClothConfigScreen screen = (ClothConfigScreen) client.gui.screen();
				String everythingTitle = null;
				String zealotTitle = null;
				String explanation = null;
				boolean red = false; // the explanation's colour
				for (List<AbstractConfigEntry<?>> entries : screen.getCategorizedEntries().values()) {
					for (AbstractConfigEntry<?> entry : entries) {
						if (!(entry instanceof SubCategoryListEntry folder)) {
							continue;
						}
						String title = folder.getFieldName().getString();
						if (title.endsWith("Everything")) {
							everythingTitle = title;
							folder.setExpanded(true);
							for (AbstractConfigListEntry<?> inside : folder.getValue()) {
								if (inside instanceof TextListEntry text && explanation == null) {
									Component message = textOf(text);
									explanation = message.getString();
									red = TextColor.fromLegacyFormat(ChatFormatting.RED).equals(message.getStyle().getColor());
								}
							}
						} else if (title.endsWith("Zealot")) {
							zealotTitle = title;
						}
					}
				}
				return new String[]{everythingTitle, String.valueOf(red), zealotTitle, explanation};
			});
			context.waitTicks(2);
			context.takeScreenshot("s6-inert-rule-warning");
			LOGGER.info("inert rule editor: title '{}', valid rule '{}', explanation '{}' (red {})",
					found[0], found[2], found[3], found[1]);
			check("⚠ Everything".equals(found[0]), "the inert rule's title has a warning sign: " + found[0]);
			check(found[3] != null && found[3].contains("needs an entity type"), "the explanation says why: " + found[3]);
			check("true".equals(found[1]), "the explanation is red");
			check("Zealot".equals(found[2]), "the valid rule is not marked: " + found[2]);
		} finally {
			context.runOnClient(client -> {
				client.gui.setScreen(null);
				module.useRulesForTest(saved);
			});
		}
	}

	/** TextListEntry keeps its text private. */
	private static Component textOf(TextListEntry entry) {
		try {
			java.lang.reflect.Field field = TextListEntry.class.getDeclaredField("text");
			field.setAccessible(true);
			return (Component) field.get(entry);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read a text entry", e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
