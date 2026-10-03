package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.settings.OptionCatalog;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Keybind;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.Clip;
import com.k8bas.skyblockutility.ui.render.Ellipsis;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.TooltipLayout;
import com.k8bas.skyblockutility.ui.render.UiSound;
import com.k8bas.skyblockutility.ui.render.UiText;
import com.k8bas.skyblockutility.ui.widget.Button;
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
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The settings screen (T2.4a, REQ-UI-01): one centred panel over the dimmed game, with a header (name,
 * version, search box, accent line), a sidebar of categories with counts, and the selected category's
 * cards under section headers. The sidebar and the content scroll separately (EC-UI-12). Everything is
 * laid out each frame by {@link ConfigLayout}; the selected category, each category's scroll and the
 * widgets' state survive a resize or GUI-scale change (EC-UI-01). Changes apply at once; the file is
 * written when the screen closes. Opened from a dev command until it replaces the Cloth screen (T2.5b).
 */
public final class ConfigScreen extends WidgetScreen {
	public static final String NAME = "K8bas Skyblock Utility";

	private final Screen parent;
	private final List<Card> cards;
	private final List<Category> categories;
	private final Map<Category, Integer> counts = new EnumMap<>(Category.class);
	private final Map<Category, ConfigPage> pages = new EnumMap<>(Category.class);
	private final Map<Category, Integer> scrolls = new EnumMap<>(Category.class);
	private final String version;
	private final ScrollArea scrollArea = new ScrollArea();
	private final VirtualList tabs;
	private final TextField search;
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
		for (Card card : cards) {
			counts.merge(card.category(), card.all().size(), Integer::sum);
		}
		this.version = RuntimeVersions.mod(K8basSkyblockUtilityClient.MOD_ID);
		add(scrollArea);
		tabs = add(new VirtualList(ConfigLayout.TAB + ConfigLayout.TAB_GAP, categories::size, this::drawTab, index -> {
			UiSound.click();
			select(categories.get(index));
		}));
		// The search box's place; T2.4b wires it to the index.
		search = add(TextField.of(new TextEditModel(35, GameClipboard.INSTANCE), "Search settings", query -> { }));
		select(categories.get(0));
	}

	/** Shows a category, keeping the scroll each one had. */
	public void select(Category category) {
		if (category == selected || !categories.contains(category)) {
			return;
		}
		focus(null);
		if (page != null) {
			// A category shown and left before a frame was drawn keeps the scroll it came with.
			scrolls.put(selected, pendingScroll >= 0 ? pendingScroll : scrollArea.scroll());
			widgets().removeAll(page.widgets());
		}
		selected = category;
		page = pages.computeIfAbsent(category, shown -> new ConfigPage(shown, cards, this::control));
		// Over the scroll area, under the sidebar and the search box.
		widgets().addAll(1, page.widgets());
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
		frame = ConfigLayout.frame(width, height);
		ConfigLayout.Rect sidebar = frame.sidebar();
		tabs.setBounds(sidebar.x() + 6, sidebar.y() + 8, Math.max(0, sidebar.w() - 12), Math.max(0, sidebar.h() - 12));
		ConfigLayout.Rect box = frame.search();
		search.setBounds(box.x(), box.y(), box.w(), box.h());
		ConfigLayout.Rect content = frame.content();
		scrollArea.setBounds(content.x(), content.y(), content.w(), content.h());
		int scroll = scrollArea.scroll();
		ConfigLayout.Page laidOut = page.layout(frame, scroll, font::width);
		scrollArea.setContentHeight(laidOut.height());
		if (pendingScroll >= 0) {
			scrollArea.setScroll(pendingScroll);
			pendingScroll = -1;
		}
		if (scrollArea.scroll() != scroll) {
			page.layout(frame, scrollArea.scroll(), font::width);
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

	private void drawTab(GuiGraphicsExtractor graphics, Font font, int index, int x, int y, int w, int h, boolean hovered) {
		Category category = categories.get(index);
		boolean current = category == selected;
		Theme theme = Theme.current();
		int tabHeight = ConfigLayout.TAB;
		if (current) {
			Shapes.roundedRect(graphics, x, y, w, tabHeight, Shapes.RADIUS_TAB, theme.accentBackground());
			graphics.fill(x, y + 4, x + 2, y + tabHeight - 4, theme.accent());
		} else if (hovered) {
			Shapes.roundedRect(graphics, x, y, w, tabHeight, Shapes.RADIUS_TAB, Theme.CARD_HOVER);
		}
		String count = Integer.toString(counts.getOrDefault(category, 0));
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
				yield new Slider(model, () -> slider.binding().get(), value -> slider.binding().set((int) value), () -> { },
						value -> slider.format((int) value), UiSound::click);
			}
			case Choice<?> choice -> dropdown(choice);
			case Keybind keybind -> keybindButton(keybind);
		};
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

	@Override
	public void onClose() {
		ConfigManager.save();
		minecraft.gui.setScreen(parent);
	}
}
