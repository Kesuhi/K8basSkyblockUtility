package com.k8bas.skyblockutility.ui.screen;

import com.google.gson.Gson;
import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.module.mobhighlighter.MobHighlighterConfig;
import com.k8bas.skyblockutility.module.npcsearch.NpcSearchConfig;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Binding;
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
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.widget.Dropdown;
import com.k8bas.skyblockutility.ui.widget.KeyTarget;
import com.k8bas.skyblockutility.ui.widget.KeybindButton;
import com.k8bas.skyblockutility.ui.widget.Overlay;
import com.k8bas.skyblockutility.ui.widget.OverlayHost;
import com.k8bas.skyblockutility.ui.widget.Slider;
import com.k8bas.skyblockutility.ui.widget.SliderModel;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.Widget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UI-03 [A], REQ-UI-05 (T2.4a): the page's real widgets, clicked the way the screen routes clicks. */
class ConfigPageTest {
	private static final Gson GSON = new Gson();

	private final GeneralConfig general = new GeneralConfig();
	private final MobHighlighterConfig mobs = new MobHighlighterConfig();
	private final NpcSearchConfig npcs = new NpcSearchConfig();
	private final Map<String, String> keys = new HashMap<>();
	private final List<Overlay> opened = new ArrayList<>();
	private final OverlayHost host = new OverlayHost() {
		@Override
		public void open(Overlay overlay) {
			opened.add(overlay);
		}

		@Override
		public void close(Overlay overlay) {
			opened.remove(overlay);
		}
	};

	private List<Card> catalog() {
		// As the modules do: switching one stores it in its config.
		return OptionCatalog.build(general, mobs, npcs, enabled -> mobs.enabled = enabled, enabled -> npcs.enabled = enabled);
	}

	private Widget control(Option option) {
		return switch (option) {
			case Toggle toggle -> new ToggleSwitch(toggle.binding(), () -> { }, () -> 0L);
			case IntSlider slider -> new Slider(SliderModel.ofInt(slider.min(), slider.max(), slider.step()), () -> slider.binding().get(),
					value -> slider.binding().set((int) value), () -> { }, value -> slider.format((int) value), () -> { });
			case Choice<?> choice -> dropdown(choice);
			case ColorOption colour -> new com.k8bas.skyblockutility.ui.widget.ColorSwatch(colour.binding(), colour.storesAlpha(), colour.text().title(), host,
					() -> { }, () -> { });
			case Keybind keybind -> new KeybindButton(new StubKey(keybind.keyMappingName()), List::of, name -> name, () -> { });
			case TextOption text -> com.k8bas.skyblockutility.ui.widget.TextField.of(new com.k8bas.skyblockutility.ui.widget.TextEditModel(text.maxLength(), null),
					text.placeholder(), text.binding()::set);
			case ActionOption action -> new com.k8bas.skyblockutility.ui.widget.Button(action.buttonLabel(), com.k8bas.skyblockutility.ui.widget.Button.Style.DESTRUCTIVE,
					action.action(), () -> { });
			case InfoOption info -> new InfoText(info.value());
			case DatabaseOption database -> new com.k8bas.skyblockutility.ui.widget.Button(database.buttonLabel(), com.k8bas.skyblockutility.ui.widget.Button.Style.NORMAL, () -> { }, () -> { });
		};
	}

	private <E> Dropdown<E> dropdown(Choice<E> choice) {
		return new Dropdown<>(choice, host, () -> { });
	}

	/** The state every setting is in: the configs (module switches included) and the keys. */
	private String state() {
		return GSON.toJson(general) + GSON.toJson(mobs) + GSON.toJson(npcs) + keys;
	}

	/** As WidgetScreen routes a press: topmost first, only inside a widget's clip region. */
	private static Widget press(ConfigPage page, double x, double y) {
		List<Widget> widgets = page.widgets();
		for (int i = widgets.size() - 1; i >= 0; i--) {
			Widget widget = widgets.get(i);
			if (widget.inClip(x, y) && widget.press(x, y, 0)) {
				widget.release(x, y);
				return widget;
			}
		}
		return null;
	}

	/** AC-UI-03 [A]: centre and edge reach only the control and change only its setting; 1 px outside reaches only a toggle card. */
	@Test
	void clicksReachOnlyTheirControl() {
		List<Card> cards = catalog();
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		int checked = 0;
		for (Category category : ConfigLayout.categories(cards)) {
			ConfigPage page = new ConfigPage(category, cards, this::control);
			page.layout(frame, 0, text -> text.length() * 6);
			for (ConfigLayout.Row row : page.page().rows()) {
				if (!(row instanceof ConfigLayout.OptionRow optionRow)) {
					continue;
				}
				ConfigLayout.Rect card = ConfigLayout.card(frame, 0, optionRow);
				if (card.bottom() > frame.content().bottom()) {
					continue;
				}
				Option option = optionRow.option();
				Widget control = page.control(option);
				ConfigLayout.Rect rect = ConfigLayout.control(card, option);
				for (double[] point : new double[][] {{rect.x() + rect.w() / 2.0, rect.y() + rect.h() / 2.0}, {rect.x(), rect.y()},
						{rect.right() - 1, rect.bottom() - 1}}) {
					opened.clear();
					String before = state();
					Widget hit = press(page, point[0], point[1]);
					assertSame(control, hit, option.id() + " at " + point[0] + "," + point[1]);
					expectOnlyItsSetting(option, before, page);
				}
				opened.clear();
				String before = state();
				Widget outside = press(page, rect.x() - 1, rect.y() + rect.h() / 2.0);
				if (option instanceof Toggle) {
					assertSame(page.card(option), outside, option.id() + ": the whole card toggles");
					assertFalse(before.equals(state()), option.id() + ": and flips it");
					press(page, rect.x() - 1, rect.y() + rect.h() / 2.0);
				} else {
					assertNull(outside, option.id() + ": 1 px outside reaches nothing");
					assertEquals(before, state(), option.id() + ": and changes nothing");
				}
				checked++;
			}
		}
		assertTrue(checked >= 6, "every kind of control was clicked: " + checked);
	}

	private void expectOnlyItsSetting(Option option, String before, ConfigPage page) {
		switch (option) {
			case Toggle toggle -> {
				assertFalse(before.equals(state()), toggle.id() + " flips");
				// Flip it back, so each click starts from the defaults.
				((ToggleSwitch) page.control(option)).toggle();
				assertEquals(before, state(), toggle.id() + ": nothing else changed");
			}
			case Choice<?> choice -> {
				assertEquals(1, opened.size(), choice.id() + " opens its list");
				assertEquals(before, state(), choice.id() + ": nothing changes until a pick");
			}
			case Keybind keybind -> {
				assertTrue(((KeybindButton) page.control(option)).armed(), keybind.id() + " is armed");
				((KeybindButton) page.control(option)).captureKey(Keybind.UNBOUND, true);
				keys.clear();
			}
			case ColorOption colour -> {
				assertEquals(1, opened.size(), colour.id() + " opens the picker");
				assertEquals(before, state(), colour.id() + ": nothing changes until Save");
			}
			case TextOption text -> assertEquals(before, state(), text.id() + ": a click into the field changes nothing");
			case ActionOption action -> { }
			case InfoOption info -> { }
			case DatabaseOption d -> { }
			case IntSlider slider -> {
				// A press on the track sets that value; put back what was there (a setting absent until picked stays absent).
				GeneralConfig was = GSON.fromJson(before.substring(0, before.indexOf('}') + 1), GeneralConfig.class);
				general.mobScanRangeBlocks = was.mobScanRangeBlocks;
				general.noticeSeconds = was.noticeSeconds;
				assertEquals(before, state(), slider.id() + ": nothing else changed");
			}
		}
	}

	/** AC-UI-03 [A] on a test category with one control of every kind: each kind is clicked at its centre, edges and 1 px outside. */
	@Test
	void aTestCategoryWithEveryKindOfControl() {
		boolean[] feature = {true};
		String[] mode = {"Exact"};
		int[] amount = {5};
		Toggle toggle = Toggle.of("test.enabled", "test.enabled", new OptionText("Test feature", "A feature for the test.", "", List.of()), true,
				Binding.of(() -> feature[0], value -> feature[0] = value));
		IntSlider slider = IntSlider.of("test.amount", "test.amount", new OptionText("Amount", "How many.", "", List.of()), 5, 0, 10, 1, "", "",
				Binding.of(() -> amount[0], value -> amount[0] = value));
		Choice<String> choice = Choice.of("test.mode", "test.mode", new OptionText("Mode", "How names match.", "", List.of()), "Exact",
				List.of("Exact", "Contains", "Regex"), value -> value, Binding.of(() -> mode[0], value -> mode[0] = value));
		Keybind keybind = Keybind.of("test.key", "key.test", new OptionText("Test key", "A key.", "", List.of()));
		List<Card> cards = List.of(new Card("test", Category.GENERAL, "Test", toggle, List.of(slider, choice, keybind)));
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		ConfigPage page = new ConfigPage(Category.GENERAL, cards, this::control);
		page.layout(frame, 0, text -> text.length() * 6);
		java.util.Set<Class<?>> kinds = new java.util.HashSet<>();
		for (ConfigLayout.Row row : page.page().rows()) {
			if (!(row instanceof ConfigLayout.OptionRow optionRow)) {
				continue;
			}
			Option option = optionRow.option();
			ConfigLayout.Rect card = ConfigLayout.card(frame, 0, optionRow);
			ConfigLayout.Rect rect = ConfigLayout.control(card, option);
			for (double[] point : new double[][] {{rect.x() + rect.w() / 2.0, rect.y() + rect.h() / 2.0}, {rect.x(), rect.y()},
					{rect.right() - 1, rect.bottom() - 1}}) {
				opened.clear();
				String before = feature[0] + mode[0] + amount[0] + keys;
				assertSame(page.control(option), press(page, point[0], point[1]), option.id());
				switch (option) {
					case Toggle t -> assertFalse(before.equals(feature[0] + mode[0] + amount[0] + keys), "flips");
					case Choice<?> c -> assertEquals(1, opened.size(), "opens its list");
					case Keybind k -> assertTrue(((KeybindButton) page.control(option)).armed(), "armed");
					case ColorOption c -> assertEquals(1, opened.size(), "opens the picker");
					case TextOption t -> { }
					case ActionOption a -> { }
					case InfoOption f -> { }
					case DatabaseOption d -> { }
					case IntSlider s -> assertTrue(feature[0] && mode[0].equals("Exact") && keys.isEmpty(), "only the amount changes");
				}
				feature[0] = true;
				amount[0] = 5;
				if (page.control(option) instanceof KeybindButton button && button.armed()) {
					button.captureKey(Keybind.UNBOUND, true);
				}
				keys.clear();
			}
			Widget outside = press(page, rect.x() - 1, rect.y() + rect.h() / 2.0);
			if (option instanceof Toggle) {
				assertSame(page.card(option), outside, "the whole toggle card");
				feature[0] = true;
			} else {
				assertNull(outside, option.id() + ": 1 px outside reaches nothing");
			}
			kinds.add(option.getClass());
		}
		assertEquals(java.util.Set.of(Toggle.class, IntSlider.class, Choice.class, Keybind.class), kinds, "every kind of control was clicked");
	}

	/** REQ-UI-07: a description cut to 2 lines ends in "..." and its card's tooltip has it whole. */
	@Test
	void aCutDescriptionIsWholeInTheTooltip() {
		String description = "This description is much too long for two lines of a narrow card, so the card cuts it and the tooltip has it all.";
		Toggle toggle = Toggle.of("test.long", "test.long", new OptionText("Long", description, "Extra detail.", List.of()), true,
				Binding.of(() -> true, value -> { }));
		List<Card> cards = List.of(new Card("test", Category.GENERAL, "Test", toggle, List.of()));
		ConfigPage page = new ConfigPage(Category.GENERAL, cards, this::control);
		page.layout(ConfigLayout.frame(320, 240), 0, text -> text.length() * 6);
		String tooltip = page.card(toggle).tooltipAt(0, 0);
		assertTrue(tooltip.startsWith(description) && tooltip.endsWith("Extra detail."), tooltip);
		page.layout(ConfigLayout.frame(1920, 1080), 0, text -> text.length() * 3);
		assertEquals("Extra detail.", page.card(toggle).tooltipAt(0, 0), "uncut: only the tooltip");
	}

	/** A control its factory made disabled (a key that is not registered) stays disabled through every layout. */
	@Test
	void aControlDisabledByItsFactoryStaysDisabled() {
		List<Card> cards = catalog();
		ConfigPage page = new ConfigPage(Category.GENERAL, cards, option -> {
			Widget control = control(option);
			if (option instanceof Keybind) {
				control.setEnabled(false);
			}
			return control;
		});
		page.layout(ConfigLayout.frame(1280, 720), 0, text -> text.length() * 6);
		Option key = cards.stream().filter(card -> card.category() == Category.GENERAL).flatMap(card -> card.all().stream())
				.filter(option -> option instanceof Keybind).findFirst().orElseThrow();
		assertFalse(page.control(key).isEnabled());
	}

	/** REQ-UI-08: while searching only the matching options show; the others are neither laid out nor clickable. */
	@Test
	void aSearchShowsOnlyTheMatchingOptions() {
		List<Card> cards = catalog();
		Card mobHighlighter = cards.stream().filter(card -> card.category() == Category.HIGHLIGHTS).findFirst().orElseThrow();
		Option range = mobHighlighter.options().get(0);
		ConfigPage page = new ConfigPage(Category.HIGHLIGHTS, cards, this::control);
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		page.layout(frame, 0, text -> text.length() * 6);
		ConfigLayout.Rect toggleCard = ConfigLayout.card(frame, 0, (ConfigLayout.OptionRow) page.page().rows().get(2));
		page.layout(frame, 0, text -> text.length() * 6, option -> option == range);
		List<Option> rows = page.page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow)
				.map(row -> ((ConfigLayout.OptionRow) row).option()).toList();
		assertEquals(List.of(range), rows, "only the match");
		assertTrue(page.page().rows().stream().anyMatch(row -> row instanceof ConfigLayout.Section section && section.card() == mobHighlighter),
				"under its feature's section");
		String before = state();
		// The match moved up into the switch's old place: a click on its text reaches nothing, the hidden switch included.
		assertNull(press(page, toggleCard.x() + 5, toggleCard.y() + 5));
		assertEquals(before, state(), "nothing changed");
		assertFalse(page.control(mobHighlighter.toggle()).showing());
		page.layout(frame, 0, text -> text.length() * 6, option -> false);
		assertEquals(1, page.page().rows().size(), "nothing matches: only the heading");
	}

	/** REQ-UI-05: a feature switched off dims its sub-options, which stay there and still work. */
	@Test
	void aFeatureOffDimsItsSubOptions() {
		List<Card> cards = catalog();
		Card mobHighlighter = cards.stream().filter(card -> card.category() == Category.HIGHLIGHTS).findFirst().orElseThrow();
		ConfigPage page = new ConfigPage(Category.HIGHLIGHTS, cards, this::control);
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		mobHighlighter.toggle().binding().set(false);
		page.layout(frame, 0, text -> text.length() * 6);
		Option range = mobHighlighter.options().get(0);
		assertTrue(page.control(range).isDimmed() && page.card(range).isDimmed(), "dimmed while off");
		assertTrue(page.control(range).isEnabled(), "but still editable");
		assertFalse(page.control(mobHighlighter.toggle()).isDimmed(), "the feature's own switch is not dimmed");
		mobHighlighter.toggle().binding().set(true);
		page.layout(frame, 0, text -> text.length() * 6);
		assertFalse(page.control(range).isDimmed());
	}

	/** REQ-UI-02/03: a card scrolled under the header is neither drawn nor clickable there. */
	@Test
	void aCardScrolledOutOfTheViewportTakesNoClick() {
		List<Card> cards = catalog();
		ConfigPage page = new ConfigPage(Category.WAYPOINTS, cards, this::control);
		ConfigLayout.Frame frame = ConfigLayout.frame(1280, 720);
		page.layout(frame, 0, text -> text.length() * 6);
		ConfigLayout.OptionRow first = (ConfigLayout.OptionRow) page.page().rows().stream()
				.filter(row -> row instanceof ConfigLayout.OptionRow).findFirst().orElseThrow();
		int scroll = first.y() + 20;
		page.layout(frame, scroll, text -> text.length() * 6);
		ConfigLayout.Rect card = ConfigLayout.card(frame, scroll, first);
		assertTrue(card.y() < frame.content().y());
		String before = state();
		assertNull(press(page, card.x() + 20, frame.content().y() - 2), "above the viewport");
		assertEquals(before, state());
		assertNotNull(press(page, card.x() + 20, frame.content().y() + 2), "the visible part still works");
	}

	private final class StubKey implements KeyTarget {
		private final String name;

		StubKey(String name) {
			this.name = name;
		}

		@Override
		public String mapping() {
			return name;
		}

		@Override
		public String bound() {
			return keys.getOrDefault(name, Keybind.UNBOUND);
		}

		@Override
		public String defaultKey() {
			return Keybind.UNBOUND;
		}

		@Override
		public void bind(String keyName) {
			keys.put(name, keyName);
		}
	}
}
