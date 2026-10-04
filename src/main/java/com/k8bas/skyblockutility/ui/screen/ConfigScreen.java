package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.module.Module;
import com.k8bas.skyblockutility.module.ModuleManager;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.notice.Notice;
import com.k8bas.skyblockutility.ui.notice.Notices;
import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.ColorOption;
import com.k8bas.skyblockutility.ui.option.InfoOption;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.option.SearchIndex;
import com.k8bas.skyblockutility.ui.option.TextOption;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.Clip;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.TooltipLayout;
import com.k8bas.skyblockutility.ui.render.UiSound;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.Button;
import com.k8bas.skyblockutility.ui.widget.ColorSwatch;
import com.k8bas.skyblockutility.ui.widget.Dropdown;
import com.k8bas.skyblockutility.ui.widget.GameClipboard;
import com.k8bas.skyblockutility.ui.widget.KeyMappingTarget;
import com.k8bas.skyblockutility.ui.widget.KeybindButton;
import com.k8bas.skyblockutility.ui.widget.ScrollArea;
import com.k8bas.skyblockutility.ui.widget.Slider;
import com.k8bas.skyblockutility.ui.widget.SliderModel;
import com.k8bas.skyblockutility.ui.widget.TextEditModel;
import com.k8bas.skyblockutility.ui.widget.TextField;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.VirtualList;
import com.k8bas.skyblockutility.ui.widget.Widget;
import com.k8bas.skyblockutility.ui.widget.WidgetScreen;
import com.k8bas.skyblockutility.util.RuntimeVersions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The settings screen (T2.4a, REQ-UI-01): one centred panel over the dimmed game, with a header (name,
 * version, search box, accent line), a sidebar of categories with counts, and the selected category's
 * cards under section headers. The sidebar and the content scroll separately (EC-UI-12). Everything is
 * laid out each frame by {@link ConfigLayout}; the selected category, each category's scroll and the
 * widgets' state survive a resize or GUI-scale change (EC-UI-01). Changes apply at once; the file is
 * written on discrete commits and once when the screen closes, by any route (REQ-UI-15). Opened from a
 * dev command until it replaces the Cloth screen (T2.5b).
 */
public final class ConfigScreen extends WidgetScreen {
	public static final String NAME = "K8bas Skyblock Utility";

	private final Screen parent;
	private final List<Card> cards;
	private final List<Category> categories;
	/** Built anew for each query, so rules added, renamed or deleted are found as they are (REQ-UI-09). */
	private SearchIndex index;
	/** Rules opened by a click, by id; kept while the screen is open. */
	private final Set<String> openRules = new HashSet<>();
	/** Rules the search opened and a click closed again, until the query changes. */
	private final Set<String> closedInSearch = new HashSet<>();
	/** The page's widgets as last put into the screen's list. */
	private List<Widget> pageWidgets = List.of();
	/** A rule was renamed, added or deleted: the badges and hits are recounted at the next layout. */
	private boolean viewStale;
	private final Map<Category, ConfigPage> pages = new EnumMap<>(Category.class);
	private final Map<Category, Integer> scrolls = new EnumMap<>(Category.class);
	private final String version;
	private final ScrollArea scrollArea = new ScrollArea();
	/** Writes on discrete commits and once on close; modules rebuild once on close (REQ-UI-15). */
	private final SaveSession session = new SaveSession(() -> ModuleManager.modules().forEach(Module::onSettingsClosed), ConfigManager::save);
	private final VirtualList tabs;
	private final TextField search;
	private final ClearButton clear;
	/** What the current query shows: the categories, badges and options (REQ-UI-08). */
	private SearchView view;
	private Category selected;
	private ConfigPage page;
	private ConfigLayout.Frame frame;
	/** A category's remembered scroll, applied once its page height is known. */
	private int pendingScroll = -1;

	public ConfigScreen(Screen parent) {
		this(parent, OptionCatalog.live());
	}

	ConfigScreen(Screen parent, List<Card> cards) {
		super(Component.literal(NAME));
		this.parent = parent;
		this.cards = cards;
		this.categories = ConfigLayout.categories(cards);
		this.index = SearchIndex.of(cards);
		this.view = SearchView.of(index, categories, "", categories.get(0));
		this.version = RuntimeVersions.mod(K8basSkyblockUtilityClient.MOD_ID);
		add(scrollArea);
		tabs = add(new VirtualList(ConfigLayout.TAB + ConfigLayout.TAB_GAP, () -> view.categories().size(), this::drawTab, row -> {
			UiSound.click();
			// A keystroke may have shortened the list since the last frame.
			if (row < view.categories().size()) {
				select(view.categories().get(row));
			}
		}));
		search = add(TextField.of(new TextEditModel(SearchIndex.MAX_QUERY_LENGTH, GameClipboard.INSTANCE), "Search settings", this::applyQuery));
		search.reserveRight(ClearButton.SIZE);
		clear = add(new ClearButton(() -> !search.model().text().isEmpty(), () -> {
			UiSound.click();
			search.model().setText("");
			applyQuery("");
			focus(search);
		}));
		show(view.selected(), false);
	}

	/** Shows a category, keeping the scroll each one had; the query stays (REQ-UI-08). */
	public void select(Category category) {
		show(category, false);
	}

	/** Filters by what is typed in the search box; the shown category moves to the first with a hit when it has none. */
	private void applyQuery(String typed) {
		index = SearchIndex.of(cards);
		view = SearchView.of(index, categories, typed, selected);
		closedInSearch.clear();
		viewStale = false;
		if (view.selected() != selected) {
			// Switched by the search itself: the box keeps the keyboard.
			show(view.selected(), true);
		}
		scrollTo(0);
	}

	/**
	 * Recounts the badges and hits for the same query after a rule changed (renamed, added, deleted),
	 * keeping the scroll and the rules closed by hand; with no hit left in the shown category it moves on.
	 */
	private void refreshView() {
		viewStale = false;
		index = SearchIndex.of(cards);
		view = SearchView.of(index, categories, search.model().text(), selected);
		if (view.selected() != selected) {
			show(view.selected(), true);
		}
	}

	/** The query typed, as the search sees it (trimmed, lower case). */
	String query() {
		return view.result().query();
	}

	SearchView view() {
		return view;
	}

	private void show(Category category, boolean keepFocus) {
		if (category == selected || !categories.contains(category)) {
			return;
		}
		if (!keepFocus) {
			focus(null);
		}
		if (page != null) {
			// A category shown and left before a frame was drawn keeps the scroll it came with.
			scrolls.put(selected, pendingScroll >= 0 ? pendingScroll : scrollArea.scroll());
			widgets().removeAll(pageWidgets);
		}
		selected = category;
		page = pages.computeIfAbsent(category, shown -> new ConfigPage(shown, cards, this::control, this::toggleRule));
		// Over the scroll area, under the sidebar and the search box.
		pageWidgets = List.copyOf(page.widgets());
		widgets().addAll(1, pageWidgets);
		pendingScroll = scrolls.getOrDefault(category, 0);
	}

	/**
	 * Scrolls the shown category to {@code y} (page pixels), held to its height at the next layout. Use
	 * this rather than the scroll area right after {@link #select}: until the new page is laid out, the
	 * scroll area still has the old page's height.
	 */
	public void scrollTo(int y) {
		pendingScroll = Math.max(0, y);
	}

	/** Opens or closes a rule card; while searching, a matching rule starts open (REQ-UI-09). */
	void toggleRule(RuleGroup rule) {
		UiSound.click();
		Set<String> set = view.filtering() ? closedInSearch : openRules;
		if (!set.remove(rule.id())) {
			set.add(rule.id());
		}
	}

	private final ConfigLayout.Filter filter = new ConfigLayout.Filter() {
		@Override
		public boolean shows(Option option) {
			return !view.nothingFound() && view.shows(option);
		}

		@Override
		public boolean shows(RuleGroup rule) {
			return !view.nothingFound() && view.shows(rule);
		}

		@Override
		public boolean expanded(RuleGroup rule) {
			return view.filtering() ? !closedInSearch.contains(rule.id()) : openRules.contains(rule.id());
		}
	};

	public Category selected() {
		return selected;
	}

	List<Category> categories() {
		return categories;
	}

	ConfigLayout.Frame frame() {
		return frame;
	}

	ConfigPage page() {
		return page;
	}

	ScrollArea scrollArea() {
		return scrollArea;
	}

	VirtualList tabs() {
		return tabs;
	}

	TextField search() {
		return search;
	}

	/** The tooltip drawn in the last frame, or null, for tests. */
	TooltipLayout.Box tooltipShown() {
		return lastTooltip();
	}

	/** Every widget on screen now, for tests. */
	List<Widget> allWidgets() {
		return List.copyOf(widgets());
	}

	@Override
	protected void layout() {
		if (viewStale) {
			refreshView();
		}
		frame = ConfigLayout.frame(width, height);
		ConfigLayout.Rect sidebar = frame.sidebar();
		tabs.setBounds(sidebar.x() + 6, sidebar.y() + 8, Math.max(0, sidebar.w() - 12), Math.max(0, sidebar.h() - 12));
		ConfigLayout.Rect box = frame.search();
		search.setBounds(box.x(), box.y(), box.w(), box.h());
		clear.setBounds(box.right() - ClearButton.SIZE - 2, box.y() + (box.h() - ClearButton.SIZE) / 2, ClearButton.SIZE, ClearButton.SIZE);
		ConfigLayout.Rect content = frame.content();
		scrollArea.setBounds(content.x(), content.y(), content.w(), content.h());
		int scroll = scrollArea.scroll();
		ConfigLayout.Page laidOut = page.layout(frame, scroll, font::width, filter);
		scrollArea.setContentHeight(laidOut.height());
		if (pendingScroll >= 0) {
			scrollArea.setScroll(pendingScroll);
			pendingScroll = -1;
		}
		if (scrollArea.scroll() != scroll) {
			page.layout(frame, scrollArea.scroll(), font::width, filter);
		}
		syncPageWidgets();
	}

	/**
	 * Puts the page's widgets, which change as rules open, close, come and go, into the screen's list
	 * (over the scroll area, under the sidebar and the search box). A field that left the page gives up
	 * the keyboard.
	 */
	private void syncPageWidgets() {
		if (pageWidgets.equals(page.widgets())) {
			return;
		}
		widgets().removeAll(pageWidgets);
		pageWidgets = List.copyOf(page.widgets());
		widgets().addAll(1, pageWidgets);
		if (keyboardFocus() != null && !widgets().contains(keyboardFocus())) {
			focus(null);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// The game stays visible around the panel, only dimmed; with no world the title panorama shows.
		if (minecraft.level == null) {
			extractPanorama(graphics, partialTick);
		}
		graphics.fill(0, 0, width, height, Theme.BACKDROP);
		minecraft.gui.hud.extractDeferredSubtitles();
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Theme theme = Theme.current();
		ConfigLayout.Rect panel = frame.panel();
		ConfigLayout.Rect header = frame.header();
		ConfigLayout.Rect sidebar = frame.sidebar();
		graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), Theme.PANEL);
		graphics.fill(header.x(), header.y(), header.right(), header.bottom(), Theme.HEADER);
		graphics.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), theme.accent());
		graphics.fill(sidebar.x(), sidebar.y(), sidebar.right(), sidebar.bottom(), Theme.SIDEBAR);
		drawHeaderText(graphics, header);
		drawPageText(graphics, theme, mouseX, mouseY);
	}

	private void drawHeaderText(GuiGraphicsExtractor graphics, ConfigLayout.Rect header) {
		int textY = header.y() + (header.h() - font.lineHeight) / 2 + 1;
		int x = header.x() + ConfigLayout.PAD;
		int room = frame.search().x() - 8 - x;
		String name = Ellipsis.fit(NAME, room, font::width);
		UiText.draw(graphics, font, name, x, textY, Theme.TEXT_PRIMARY);
		int versionX = x + font.width(name) + 6;
		String shownVersion = Ellipsis.fit("v" + version, frame.search().x() - 8 - versionX, font::width);
		if (name.equals(NAME) && !shownVersion.isEmpty()) {
			UiText.draw(graphics, font, shownVersion, versionX, textY, Theme.TEXT_SECONDARY);
		}
	}

	/**
	 * The category heading and the section headers, scrolled with the cards and clipped to the content.
	 * A heading whose description was cut shows it whole on hover.
	 */
	private void drawPageText(GuiGraphicsExtractor graphics, Theme theme, int mouseX, int mouseY) {
		ConfigLayout.Rect content = frame.content();
		int scroll = scrollArea.scroll();
		try (Clip clip = Clip.push(graphics, content.x(), content.y(), content.w(), content.h())) {
			if (!clip.visible()) {
				return;
			}
			if (view.nothingFound()) {
				String message = Ellipsis.fit(view.message(), content.w() - 2 * ConfigLayout.PAD, font::width);
				UiText.centred(graphics, font, message, content.x() + content.w() / 2, content.y() + 40, Theme.TEXT_SECONDARY);
				return;
			}
			for (ConfigLayout.Row row : page.page().rows()) {
				ConfigLayout.Rect rect = ConfigLayout.row(frame, scroll, row);
				if (rect.bottom() < content.y() || rect.y() > content.bottom()) {
					continue;
				}
				if (row instanceof ConfigLayout.Heading heading) {
					UiText.draw(graphics, font, Ellipsis.fit(heading.category().displayName(), rect.w(), font::width), rect.x(), rect.y(),
							Theme.TEXT_PRIMARY);
					int lineY = rect.y() + 14;
					for (String line : heading.description()) {
						UiText.draw(graphics, font, line, rect.x(), lineY, Theme.TEXT_SECONDARY);
						lineY += 10;
					}
					if (heading.cut() && rect.contains(mouseX, mouseY) && content.contains(mouseX, mouseY)) {
						showTooltip(heading.category().description());
					}
				} else if (row instanceof ConfigLayout.RuleProblem problem) {
					// Why the rule does nothing, in the fixed error colour (REQ-GLOW-10, REQ-UI-12).
					int lineY = rect.y() + 1;
					for (String line : problem.lines()) {
						UiText.draw(graphics, font, line, rect.x(), lineY, Theme.ERROR);
						lineY += 10;
					}
				} else if (row instanceof ConfigLayout.Section section) {
					String title = Ellipsis.fit(section.card().title(), rect.w(), font::width);
					int textY = rect.y() + 8;
					UiText.draw(graphics, font, title, rect.x(), textY, theme.accent());
					int lineX = rect.x() + font.width(title) + 6;
					if (lineX < rect.right()) {
						graphics.fill(lineX, textY + 4, rect.right(), textY + 5, Theme.SEPARATOR);
					}
				}
			}
		}
	}

	private void drawTab(GuiGraphicsExtractor graphics, Font font, int row, int x, int y, int w, int h, boolean hovered) {
		Category category = view.categories().get(row);
		boolean current = category == selected;
		Theme theme = Theme.current();
		int tabHeight = ConfigLayout.TAB;
		if (current) {
			Shapes.roundedRect(graphics, x, y, w, tabHeight, Shapes.RADIUS_TAB, theme.accentBackground());
			graphics.fill(x, y + 4, x + 2, y + tabHeight - 4, theme.accent());
		} else if (hovered) {
			Shapes.roundedRect(graphics, x, y, w, tabHeight, Shapes.RADIUS_TAB, Theme.CARD_HOVER);
		}
		// The number of options, or of hits while searching (REQ-UI-08).
		String count = Integer.toString(view.badge(category));
		int badgeWidth = font.width(count) + 8;
		int badgeX = x + w - 6 - badgeWidth;
		Shapes.roundedRect(graphics, badgeX, y + 5, badgeWidth, tabHeight - 10, Shapes.RADIUS_CONTROL, current ? theme.accent() : Theme.CARD);
		UiText.centred(graphics, font, count, badgeX + badgeWidth / 2, y + (tabHeight - font.lineHeight) / 2 + 1,
				current ? theme.textOnAccent() : Theme.TEXT_SECONDARY);
		String label = Ellipsis.fit(category.displayName(), badgeX - 6 - (x + 10), font::width);
		UiText.draw(graphics, font, label, x + 10, y + (tabHeight - font.lineHeight) / 2 + 1, current ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY);
	}

	/** The control for an option, by its kind. */
	private Widget control(Option option) {
		return switch (option) {
			case Toggle toggle -> ToggleSwitch.of(toggle.binding());
			case IntSlider slider -> {
				SliderModel model = SliderModel.ofInt(slider.min(), slider.max(), slider.step());
				yield new Slider(model, () -> slider.binding().get(), value -> slider.binding().set((int) value), session::commit,
						value -> slider.format((int) value), UiSound::click);
			}
			case Choice<?> choice -> dropdown(choice);
			case Keybind keybind -> keybindButton(keybind);
			// Save in the picker is a discrete commit (REQ-UI-15).
			case ColorOption colour -> new ColorSwatch(colour.binding(), colour.storesAlpha(), colour.text().title(), this, session::commit, UiSound::click);
			case TextOption text -> textField(text);
			// An action (a rule's Delete) is a discrete commit too.
			case ActionOption action -> new Button(action.buttonLabel(), action.destructive() ? Button.Style.DESTRUCTIVE : Button.Style.NORMAL,
					() -> {
						action.action().run();
						viewStale = true;
						session.commit();
					}, UiSound::click);
			case InfoOption info -> new InfoText(info.value());
		};
	}

	/**
	 * Typing applies at once; the field shows the error colour while the option says so (REQ-UI-12). A
	 * stored text longer than the field's limit (a hand edit) is shown whole and kept: the limit grows to it.
	 */
	private TextField textField(TextOption option) {
		String stored = option.binding().get();
		TextEditModel model = new TextEditModel(Math.max(option.maxLength(), stored.length()), GameClipboard.INSTANCE);
		model.setText(stored);
		TextField field = TextField.of(model, option.placeholder(), value -> {
			option.binding().set(value);
			// A rule's label or pattern is searchable: recount the badges and hits.
			viewStale = true;
		});
		field.markInvalidWhen(option.invalid());
		return field;
	}

	private <E> Dropdown<E> dropdown(Choice<E> choice) {
		return new Dropdown<>(choice, this, UiSound::click);
	}

	private Widget keybindButton(Keybind keybind) {
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			if (mapping.getName().equals(keybind.keyMappingName())) {
				return new KeybindButton(new KeyMappingTarget(mapping), KeyMappingTarget::allKeys, KeyMappingTarget::displayName, UiSound::click);
			}
		}
		// Not registered in this game: shown, but nothing to bind.
		Button missing = new Button("Unavailable", Button.Style.NORMAL, () -> { }, () -> { });
		missing.setEnabled(false);
		return missing;
	}

	/**
	 * Ctrl+F (Cmd+F on macOS) focuses the search box while nothing else has the keyboard (REQ-UI-08); not
	 * AltGr+F, which types a character on some layouts, and not while a slider is held, so a search cannot
	 * hide the control being dragged.
	 */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_F && shortcutCtrl(event) && keyboardFocus() == null && overlay() == null && !pressHeld()) {
			focus(search);
			search.model().selectAll();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		// removed() writes the changes; the same happens when another screen replaces this one.
		minecraft.gui.setScreen(parent);
	}

	/** Shown, also again after a screen opened from it returns: the next way it goes saves once. */
	@Override
	public void added() {
		super.added();
		session.open();
		// The settings file could not be read and was copied aside: say where, once (EC-UI-16).
		String backup = ConfigManager.takeBackupForScreen();
		if (backup != null) {
			Notices.post(new Notice("Settings file backed up", List.of("A copy is in the config folder:", backup)));
		}
	}

	/** Every way the screen goes (Esc, replaced by another screen, a disconnect) saves once (REQ-UI-15, EC-UI-02). */
	@Override
	public void removed() {
		super.removed();
		session.close();
	}

	/**
	 * Saves now, for a game that is shutting down with the screen open: the game flushes the config
	 * before it removes the screen (REQ-CFG-10). The removal that follows then writes nothing more.
	 */
	public void saveBeforeShutdown() {
		session.close();
	}
}
