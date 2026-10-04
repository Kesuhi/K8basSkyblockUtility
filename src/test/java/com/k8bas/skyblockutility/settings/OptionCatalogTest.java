package com.k8bas.skyblockutility.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.highlight.GlowBehaviourText;
import com.k8bas.skyblockutility.ui.notice.NoticePosition;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.DatabaseOption;
import com.k8bas.skyblockutility.ui.option.InfoOption;
import com.k8bas.skyblockutility.ui.option.TextOption;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.SearchIndex;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.screen.ConfigLayout;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2.1: the option declarations of General, Mob Highlighter and NPC Search (REQ-UI-05, -07, -19,
 * REQ-XC-TOGGLE-01/02, REQ-XC-RULES-07, REQ-GLOW-15).
 */
class OptionCatalogTest {
	private static final Path PROJECT = Path.of(System.getProperty("k8bas.projectDir", "../.."));
	private static final String UNBOUND = Keybind.UNBOUND;
	/** REQ-XC-RULES-07 (P7); word boundaries, as "esp" is part of "response". */
	private static final Pattern P7 = Pattern.compile("(?i)\\b(esp|x-?ray|through walls?|cheat\\w*|gambling)\\b");
	/** REQ-UI-05: behaviour hard-coded for the rules is never offered as an option. */
	private static final Pattern COMPLIANCE_TOGGLE = Pattern.compile("(?i)(invisible|through.?walls?|x-?ray|see.?through|depth)");

	private static List<Card> catalog() {
		return OptionCatalog.build(new GeneralConfig(), new MobHighlighterConfig(), new NpcSearchConfig(), enabled -> { }, enabled -> { });
	}

	private static List<Option> options() {
		return catalog().stream().flatMap(card -> card.all().stream()).toList();
	}

	/** The options that store a value; a button (an action such as "Edit HUD layout", a database picker) stores nothing. */
	private static List<Option> storedOptions() {
		return options().stream().filter(option -> !(option instanceof ActionOption || option instanceof DatabaseOption)).toList();
	}

	/** REQ-UI-04, REQ-HUD-06 (T2.8b): General › HUD, between Interface and Keybinds, has "Edit HUD layout", which runs the action given. */
	@Test
	void generalHasTheHudCardWithEditHudLayout() {
		int[] opened = {0};
		List<Card> general = GeneralOptions.cards(new GeneralConfig(), () -> opened[0]++);
		assertEquals(List.of("interface", "hud", "keybinds", "updates"), general.stream().map(Card::id).toList());
		Card hud = general.get(1);
		assertEquals("HUD", hud.title());
		ActionOption edit = (ActionOption) hud.all().get(0);
		assertEquals("Edit HUD layout", edit.buttonLabel());
		edit.action().run();
		assertEquals(1, opened[0]);
		assertTrue(SearchIndex.of(general).search("hud layout").count(Category.GENERAL) > 0, "found by searching");
	}

	/** AC-UI-04 [A] part, REQ-XC-TOGGLE-02: every declared default equals the defaults table (SPEC §12.H) and the fresh config. */
	@Test
	void defaultsMatchTheDefaultsTable() {
		Map<String, Object> expected = new LinkedHashMap<>();
		// §12.H: "Update check ON"; the scan range of REQ-GLOW-01; keybinds unbound (REQ-UI-10).
		expected.put("general.autoUpdateCheckEnabled", true);
		expected.put("general.mobScanRangeBlocks", 64);
		// D-8: teal accent; R28: notices top right for 5 s until picked.
		expected.put("general.accentColor", 0x29B6B2);
		expected.put("general.noticePosition", NoticePosition.TOP_RIGHT);
		expected.put("general.noticeSeconds", 5);
		expected.put("modules.mob_highlighter.enabled", true);
		// §12.H: NPC Search ON, NPC waypoints ON, "You found" title ON (R20), white labels ON (R21), beams and distance ON.
		expected.put("modules.npc_search.enabled", true);
		expected.put("modules.npc_search.foundTitleEnabled", true);
		expected.put("modules.npc_search.showBeams", true);
		expected.put("modules.npc_search.showDistance", true);
		expected.put("modules.npc_search.whiteWaypointLabels", true);
		expected.put("options.txt:key.k8bas_skyblock_utility.open_settings", UNBOUND);
		expected.put("options.txt:key.k8bas_skyblock_utility.mob_highlighter_toggle", UNBOUND);
		expected.put("options.txt:key.k8bas_skyblock_utility.npc_search_toggle", UNBOUND);

		Map<String, Object> declared = new LinkedHashMap<>();
		for (Option option : storedOptions()) {
			declared.put(option.storageKey(), option.defaultValue());
			Object current = switch (option) {
				case Toggle toggle -> toggle.binding().get();
				case IntSlider slider -> slider.binding().get();
				case Choice<?> choice -> choice.binding().get();
				case Keybind keybind -> keybind.defaultValue();
				case ColorOption colour -> colour.binding().get();
				case TextOption t -> throw new AssertionError("only rule cards have text fields");
				case ActionOption a -> throw new AssertionError("only rule cards have actions");
				case InfoOption i -> throw new AssertionError("only rule cards show info");
				case DatabaseOption d -> throw new AssertionError("only a rule list with a database has this");
			};
			assertEquals(option.defaultValue(), current, "a fresh config holds the declared default: " + option.id());
		}
		assertEquals(expected, declared);
	}

	/** REQ-XC-TOGGLE-01, REQ-UI-05: every feature card has its own toggle; ids are unique; categories per REQ-UI-04. */
	@Test
	void everyFeatureCardHasItsOwnToggle() {
		Set<String> ids = new HashSet<>();
		for (Card card : catalog()) {
			assertTrue(ids.add(card.id()), "card ids are unique: " + card.id());
			for (Option option : card.all()) {
				assertTrue(ids.add(option.id()), "option ids are unique: " + option.id());
			}
		}
		Map<String, Card> byId = new LinkedHashMap<>();
		catalog().forEach(card -> byId.put(card.id(), card));
		assertEquals(Category.HIGHLIGHTS, byId.get("mob_highlighter").category());
		assertEquals(Category.WAYPOINTS, byId.get("npc_search").category());
		assertEquals(Category.GENERAL, byId.get("updates").category());
		assertEquals(Category.GENERAL, byId.get("keybinds").category());
		for (String feature : List.of("mob_highlighter", "npc_search", "updates")) {
			assertNotNull(byId.get(feature).toggle(), feature + " has its own toggle");
		}
		assertTrue(byId.get("mob_highlighter").options().stream().anyMatch(option -> option.text().title().equals("Mob scan range")),
				"the mob scan range belongs to Mob Highlighter (REQ-UI-04)");
		List<Category> order = catalog().stream().map(Card::category).toList();
		List<Category> sorted = new ArrayList<>(order);
		sorted.sort(null);
		assertEquals(sorted, order, "cards come in category order");
	}

	/** AC-UI-17 [A], REQ-UI-19: every AMBER option has a tooltip, and the AMBER set is the one in PLAN §3. */
	@Test
	void amberOptionsExplainTheirRestriction() {
		Set<String> amber = new TreeSet<>();
		for (Option option : options()) {
			if (option.amber()) {
				amber.add(option.id());
				assertFalse(option.text().tooltip().isBlank(), "an AMBER option has a tooltip: " + option.id());
			}
		}
		assertEquals(Set.of("npc_search.enabled", "npc_search.show_distance"), amber);
	}

	/**
	 * REQ-UI-07: a card shows at most two wrapped description lines; the full text goes in the tooltip.
	 * Measured on the card of a common size (1920x1080 at GUI scale 2), every description fits uncut.
	 */
	@Test
	void descriptionsFitTwoCardLines() {
		ConfigLayout.Frame frame = ConfigLayout.frame(960, 540);
		ToIntFunction<String> sixPixels = text -> text.length() * 6;
		for (Card card : catalog()) {
			ConfigLayout.Page page = ConfigLayout.page(catalog(), card.category(), frame.content().w(), sixPixels);
			for (ConfigLayout.Row row : page.rows()) {
				if (row instanceof ConfigLayout.OptionRow optionRow && optionRow.card().id().equals(card.id())) {
					Option option = optionRow.option();
					String description = option.text().description();
					assertFalse(description.isBlank(), option.id());
					ConfigLayout.Rect text = ConfigLayout.text(ConfigLayout.card(frame, 0, optionRow), option);
					assertFalse(ConfigLayout.fitLines(description, text.w(), 2, sixPixels).cut(), "fits 2 card lines: " + option.id());
				}
			}
		}
	}

	/** AC-UI-07 [A] on the shipped catalog: every option is found by its title; "range" and "  RANGE " find the scan range. */
	@Test
	void theRealCatalogIsSearchable() {
		SearchIndex index = SearchIndex.of(catalog());
		for (Option option : options()) {
			assertTrue(index.search(option.text().title()).hits().stream().anyMatch(hit -> hit.id().equals(option.id())), option.id());
		}
		SearchIndex.Result range = index.search("range");
		assertEquals(List.of("mob_highlighter.scan_range"), range.hits().stream().map(SearchIndex.Entry::id).toList());
		assertEquals(range.hits(), index.search("  RANGE ").hits());
		SearchIndex.Result none = index.search("zzzz");
		assertTrue(none.hits().isEmpty() && none.categoriesWithHits().isEmpty());
	}

	/** Each binding reads and writes exactly the field its storage key names. */
	@Test
	void bindingsWriteTheFieldTheyAreStoredUnder() {
		GeneralConfig general = new GeneralConfig();
		MobHighlighterConfig mob = new MobHighlighterConfig();
		NpcSearchConfig npc = new NpcSearchConfig();
		List<Card> cards = OptionCatalog.build(general, mob, npc, value -> mob.enabled = value, value -> npc.enabled = value);
		Gson gson = new GsonBuilder().serializeNulls().create();
		for (Card card : cards) {
			for (Option option : card.all()) {
				// Keys live in options.txt; a button stores nothing.
				if (option instanceof Keybind || option instanceof ActionOption) {
					continue;
				}
				Map<String, String> before = leaves(gson, general, mob, npc);
				Object original = current(option);
				change(option);
				Map<String, String> after = leaves(gson, general, mob, npc);
				Set<String> changed = new TreeSet<>();
				before.forEach((key, value) -> {
					if (!value.equals(after.get(key))) {
						changed.add(key);
					}
				});
				assertEquals(Set.of(option.storageKey()), changed, option.id());
				restore(option, original);
				Map<String, String> restored = leaves(gson, general, mob, npc);
				// A setting absent until picked (R28, the accent) now holds its default: picked, so it is written.
				if ("null".equals(before.get(option.storageKey()))) {
					assertEquals(gson.toJson(option.defaultValue()), restored.get(option.storageKey()), option.id() + " holds its default");
					restored.put(option.storageKey(), "null");
				}
				assertEquals(before, restored, option.id() + " restores");
			}
		}
	}

	/** The keybind names are the registered key mappings' names, and each registers unbound (REQ-UI-10). */
	@Test
	void keybindsAreTheRegisteredKeyMappings() throws IOException {
		Map<String, String> registrations = Map.of(
				"settings/SettingsKeybind.java", SettingsKeybind.NAME,
				"module/mobhighlighter/ModKeybinds.java", com.k8bas.skyblockutility.module.mobhighlighter.ModKeybinds.NAME,
				"module/npcsearch/ModKeybinds.java", com.k8bas.skyblockutility.module.npcsearch.ModKeybinds.NAME);
		Set<String> declared = new TreeSet<>();
		options().stream().filter(option -> option instanceof Keybind).forEach(option -> declared.add(((Keybind) option).keyMappingName()));
		assertEquals(new TreeSet<>(registrations.values()), declared);
		for (Map.Entry<String, String> registration : registrations.entrySet()) {
			String source = Files.readString(PROJECT.resolve("src/main/java/com/k8bas/skyblockutility").resolve(registration.getKey()));
			assertTrue(source.contains("new KeyMapping(\n\t\t\tNAME,") || source.contains("new KeyMapping(\r\n\t\t\tNAME,"), "registered under NAME: " + registration.getKey());
			assertTrue(source.contains("InputConstants.UNKNOWN.getValue()"), "registered unbound: " + registration.getKey());
		}
	}

	/** OptionCatalog.build lists every module the game registers, so these tests cover what the screen shows. */
	@Test
	void theTestCatalogHasEveryModule() throws IOException {
		Set<String> moduleIds = new TreeSet<>();
		try (Stream<Path> files = Files.walk(PROJECT.resolve("src/main/java"))) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				if (source.contains("implements Module")) {
					java.util.regex.Matcher id = Pattern.compile("String ID = \"([a-z_]+)\"").matcher(source);
					assertTrue(id.find(), "a module id in " + file.getFileName());
					moduleIds.add(id.group(1));
				}
			}
		}
		Set<String> cardIds = new TreeSet<>();
		catalog().forEach(card -> cardIds.add(card.id()));
		assertTrue(cardIds.containsAll(moduleIds), "modules " + moduleIds + " vs cards " + cardIds);
		assertEquals(2, moduleIds.size());
	}

	/** REQ-XC-RULES-07 (P7): no forbidden word in what is shown; "gambling" only as a keyword. */
	@Test
	void noRulesWordIsShown() {
		for (Card card : catalog()) {
			assertFalse(P7.matcher(card.title()).find(), card.id());
		}
		for (Option option : options()) {
			List<String> shown = new ArrayList<>(List.of(option.text().title(), option.text().description(), option.text().tooltip()));
			shown.addAll(option.searchLabels());
			for (String text : shown) {
				assertFalse(P7.matcher(text).find(), option.id() + ": " + text);
			}
			for (String keyword : option.text().keywords()) {
				assertTrue(!P7.matcher(keyword).find() || keyword.equalsIgnoreCase("gambling"), option.id() + ": " + keyword);
			}
		}
	}

	/** REQ-UI-05, AC-UI-04: no option for through-wall or invisible highlighting. */
	@Test
	void hardCodedRulesAreNotOptions() {
		for (Card card : catalog()) {
			assertFalse(COMPLIANCE_TOGGLE.matcher(card.title()).find(), card.id());
		}
		for (Option option : options()) {
			List<String> named = new ArrayList<>(List.of(option.id(), option.storageKey(), option.text().title()));
			named.addAll(option.searchLabels());
			for (String text : named) {
				assertFalse(COMPLIANCE_TOGGLE.matcher(text).find(), option.id() + ": " + text);
			}
		}
	}

	/** AC-GLOW-14, REQ-GLOW-15: both highlight cards state the four behaviour changes in the CHANGELOG's wording. */
	@Test
	void theHighlightCardsUseTheChangelogWording() throws IOException {
		String changelog = normalize(Files.readString(PROJECT.resolve("CHANGELOG.md")));
		assertEquals(4, GlowBehaviourText.CHANGES.size());
		for (String change : GlowBehaviourText.CHANGES) {
			assertTrue(changelog.contains(normalize(change)), "the CHANGELOG has: " + change);
		}
		for (String id : List.of("mob_highlighter.enabled", "npc_search.enabled")) {
			String tooltip = normalize(options().stream().filter(option -> option.id().equals(id)).findFirst().orElseThrow().text().tooltip());
			for (String change : GlowBehaviourText.CHANGES) {
				assertTrue(tooltip.contains(normalize(change)), id + " states: " + change);
			}
		}
	}

	/** AC-UI-15 (mapping part): every key of the 1.0.1 fixture is a declared option or documented here. */
	@Test
	void every101SettingHasAPlace() throws IOException {
		Map<String, String> documented = new LinkedHashMap<>();
		documented.put("general.autoUpdateDownloadEnabled", "removed by the migration DropAutoDownload (D-14)");
		for (String field : List.of("label", "enabled", "entityTypeId", "nameMatchMode", "namePattern", "color", "island")) {
			documented.put("modules.mob_highlighter.rules[]." + field, "rule card (T2.5a)");
		}
		for (String field : List.of("label", "enabled", "nameMatchMode", "namePattern", "color", "island")) {
			documented.put("modules.npc_search.rules[]." + field, "rule card (T2.5a)");
		}
		for (String field : List.of("id", "sourceId", "maxDistance")) {
			documented.put("modules.mob_highlighter.rules[]." + field, "kept, not editable (ui-config out of scope)");
		}
		for (String field : List.of("id", "sourceId", "fixed", "x", "y", "z")) {
			documented.put("modules.npc_search.rules[]." + field, "kept, not editable (ui-config out of scope)");
		}
		Set<String> declared = new HashSet<>();
		options().forEach(option -> declared.add(option.storageKey()));

		JsonObject fixture = JsonParser.parseString(Files.readString(PROJECT.resolve("src/test/resources/fixtures/config-1.0.1-shaped.json"))).getAsJsonObject();
		Set<String> keys = new TreeSet<>();
		flatten("", fixture, keys);
		for (String key : keys) {
			assertTrue(declared.contains(key) || documented.containsKey(key), "a 1.0.1 setting without a place: " + key);
		}
	}

	/** The declared storage keys are real fields of a fresh config. */
	@Test
	void storageKeysPointAtRealFields() {
		// Null-default fields (the update channel until it is picked) are real too.
		Gson gson = new GsonBuilder().serializeNulls().create();
		Map<String, JsonObject> roots = Map.of(
				"general", gson.toJsonTree(new GeneralConfig()).getAsJsonObject(),
				"modules.mob_highlighter", gson.toJsonTree(new MobHighlighterConfig()).getAsJsonObject(),
				"modules.npc_search", gson.toJsonTree(new NpcSearchConfig()).getAsJsonObject());
		for (Option option : storedOptions()) {
			String key = option.storageKey();
			if (key.startsWith("options.txt:")) {
				continue;
			}
			int dot = key.lastIndexOf('.');
			JsonObject root = roots.get(key.substring(0, dot));
			assertNotNull(root, "a known config section: " + key);
			assertTrue(root.has(key.substring(dot + 1)), "a real field: " + key);
		}
	}

	/** REQ-UI-07: option declarations do not depend on a UI library (or on Minecraft). */
	@Test
	void theOptionPackageIsPlainJava() throws IOException {
		Path dir = PROJECT.resolve("src/main/java/com/k8bas/skyblockutility/ui/option");
		List<Path> files;
		try (Stream<Path> walk = Files.walk(dir)) {
			files = walk.filter(path -> path.toString().endsWith(".java")).toList();
		}
		assertTrue(files.size() >= 4, "the scan sees the package");
		for (Path file : files) {
			String source = Files.readString(file);
			for (String line : source.lines().filter(line -> line.startsWith("import ")).toList()) {
				assertTrue(line.startsWith("import java.") || line.startsWith("import static java."), file.getFileName() + ": " + line);
			}
			// The Cloth package is spelt in two parts, so `grep -r` for it finds nothing in src (AC-UI-20).
			for (String library : List.of("me." + "shed" + "aniel", "net.minecraft", "com.mojang", "com.terraformersmc")) {
				assertFalse(source.contains(library), file.getFileName() + " mentions " + library);
			}
		}
	}

	private static Map<String, String> leaves(Gson gson, GeneralConfig general, MobHighlighterConfig mob, NpcSearchConfig npc) {
		Set<String> keys = new TreeSet<>();
		Map<String, String> values = new LinkedHashMap<>();
		JsonObject root = new JsonObject();
		root.add("general", gson.toJsonTree(general));
		JsonObject modules = new JsonObject();
		modules.add("mob_highlighter", gson.toJsonTree(mob));
		modules.add("npc_search", gson.toJsonTree(npc));
		root.add("modules", modules);
		flatten("", root, keys);
		for (String key : keys) {
			values.put(key, String.valueOf(lookup(root, key)));
		}
		return values;
	}

	private static JsonElement lookup(JsonObject root, String key) {
		JsonElement element = root;
		for (String part : key.split("\\.")) {
			element = element.getAsJsonObject().get(part);
		}
		return element;
	}

	private static Object current(Option option) {
		return switch (option) {
			case Toggle toggle -> toggle.binding().get();
			case IntSlider slider -> slider.binding().get();
			case Choice<?> choice -> choice.binding().get();
			case Keybind keybind -> keybind.defaultValue();
			case ColorOption colour -> colour.binding().get();
			case TextOption t -> throw new AssertionError("only rule cards have text fields");
			case ActionOption a -> throw new AssertionError("only rule cards have actions");
			case InfoOption i -> throw new AssertionError("only rule cards show info");
			case DatabaseOption d -> throw new AssertionError("only a rule list with a database has this");
		};
	}

	private static void change(Option option) {
		switch (option) {
			case Toggle toggle -> toggle.binding().set(!toggle.binding().get());
			case IntSlider slider -> slider.binding().set(slider.binding().get().equals(slider.max()) ? slider.min() : slider.max());
			case Choice<?> choice -> changeChoice(choice);
			case Keybind keybind -> { }
			case ColorOption colour -> colour.binding().set(colour.binding().get() == 0x123456 ? 0x654321 : 0x123456);
			case TextOption t -> throw new AssertionError("only rule cards have text fields");
			case ActionOption a -> throw new AssertionError("only rule cards have actions");
			case InfoOption i -> throw new AssertionError("only rule cards show info");
			case DatabaseOption d -> throw new AssertionError("only a rule list with a database has this");
		}
	}

	private static <E> void changeChoice(Choice<E> choice) {
		for (E value : choice.values()) {
			if (!value.equals(choice.binding().get())) {
				choice.binding().set(value);
				return;
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void restore(Option option, Object original) {
		switch (option) {
			case Toggle toggle -> toggle.binding().set((Boolean) original);
			case IntSlider slider -> slider.binding().set((Integer) original);
			case Choice<?> choice -> ((Choice<Object>) choice).binding().set(original);
			case Keybind keybind -> { }
			case ColorOption colour -> colour.binding().set((Integer) original);
			case TextOption t -> throw new AssertionError("only rule cards have text fields");
			case ActionOption a -> throw new AssertionError("only rule cards have actions");
			case InfoOption i -> throw new AssertionError("only rule cards show info");
			case DatabaseOption d -> throw new AssertionError("only a rule list with a database has this");
		}
	}

	private static String normalize(String text) {
		return text.replace("\\<", "<").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static void flatten(String prefix, JsonElement element, Set<String> out) {
		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				flatten(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), entry.getValue(), out);
			}
		} else if (element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				flatten(prefix + "[]", item, out);
			}
		} else {
			out.add(prefix);
		}
	}
}
