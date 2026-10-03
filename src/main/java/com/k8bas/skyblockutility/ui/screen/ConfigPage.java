package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionStatus;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.Widget;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * One category's widgets: a card per option and its control over it, made once and kept while the
 * screen is open, so a switch's animation or an armed key survives a resize (EC-UI-01). Each frame
 * {@link #layout} places them from {@link ConfigLayout}, clipped to the content viewport, and dims a
 * feature's sub-options while it is off (REQ-UI-05).
 */
final class ConfigPage {
	/** Makes the control for an option; the screen's makes real ones, tests make them without a game. */
	interface Controls {
		Widget control(Option option);
	}

	private final Category category;
	private final List<Card> cards;
	private final Map<Option, OptionCard> cardWidgets = new IdentityHashMap<>();
	private final Map<Option, Widget> controls = new IdentityHashMap<>();
	/** Controls the factory made disabled (e.g. a key that is not registered) stay so, whatever the status. */
	private final Map<Widget, Boolean> ownEnabled = new IdentityHashMap<>();
	/** Cards first, then controls, so every control lies over its card. */
	private final List<Widget> widgets = new ArrayList<>();
	private ConfigLayout.Page page;

	ConfigPage(Category category, List<Card> cards, Controls factory) {
		this.category = category;
		this.cards = cards;
		List<Widget> controlWidgets = new ArrayList<>();
		for (Card card : cards) {
			if (card.category() != category) {
				continue;
			}
			for (Option option : card.all()) {
				Widget control = factory.control(option);
				ToggleSwitch toggle = option instanceof Toggle && control instanceof ToggleSwitch toggleSwitch ? toggleSwitch : null;
				OptionCard cardWidget = new OptionCard(option, toggle);
				cardWidgets.put(option, cardWidget);
				controls.put(option, control);
				ownEnabled.put(control, control.isEnabled());
				widgets.add(cardWidget);
				controlWidgets.add(control);
			}
		}
		widgets.addAll(controlWidgets);
	}

	Category category() {
		return category;
	}

	List<Widget> widgets() {
		return widgets;
	}

	ConfigLayout.Page page() {
		return page;
	}

	Widget control(Option option) {
		return controls.get(option);
	}

	OptionCard card(Option option) {
		return cardWidgets.get(option);
	}

	/** Places every card and control for this frame and returns the page (for its height and rows). */
	ConfigLayout.Page layout(ConfigLayout.Frame frame, int scroll, ToIntFunction<String> width) {
		page = ConfigLayout.page(cards, category, frame.content().w(), width);
		ConfigLayout.Rect content = frame.content();
		ClipRect clip = ClipRect.of(content.x(), content.y(), content.w(), content.h());
		for (ConfigLayout.Row row : page.rows()) {
			if (!(row instanceof ConfigLayout.OptionRow optionRow)) {
				continue;
			}
			Option option = optionRow.option();
			ConfigLayout.Rect rect = ConfigLayout.card(frame, scroll, optionRow);
			ConfigLayout.Rect controlRect = ConfigLayout.control(rect, option);
			OptionCard cardWidget = cardWidgets.get(option);
			Widget control = controls.get(option);
			cardWidget.setBounds(rect.x(), rect.y(), rect.w(), rect.h());
			control.setBounds(controlRect.x(), controlRect.y(), controlRect.w(), controlRect.h());
			cardWidget.setClip(clip);
			cardWidget.fitText(width);
			control.setClip(clip);
			Toggle feature = optionRow.card().toggle();
			boolean off = feature != null && !optionRow.featureToggle() && !feature.binding().get();
			cardWidget.setDimmed(off);
			control.setDimmed(off);
			boolean available = option.status().state() != OptionStatus.State.UNAVAILABLE;
			cardWidget.setEnabled(available);
			control.setEnabled(available && ownEnabled.get(control));
		}
		return page;
	}
}
