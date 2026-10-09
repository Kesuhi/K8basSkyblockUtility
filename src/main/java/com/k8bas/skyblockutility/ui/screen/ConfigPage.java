package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.OptionStatus;
import com.k8bas.skyblockutility.ui.option.RuleGroup;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.ClipRect;
import com.k8bas.skyblockutility.ui.widget.ToggleSwitch;
import com.k8bas.skyblockutility.ui.widget.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * One category's widgets: a card per option and its control over it, and a header per rule (REQ-UI-11),
 * each made the first time it is shown and kept while the screen is open, so a switch's animation or an
 * armed key survives a resize (EC-UI-01). Each frame {@link #layout} places what the page shows,
 * clipped to the content viewport, dims a feature's sub-options and rules while it is off (REQ-UI-05),
 * and lists only those widgets; the rest are hidden, so they are neither drawn nor clicked.
 */
final class ConfigPage {
	/** Makes the control for an option; the screen's makes real ones, tests make them without a game. */
	interface Controls {
		Widget control(Option option);
	}

	private final Category category;
	private final List<Card> cards;
	private final Controls factory;
	/** Opens or closes a rule when its header is clicked. */
	private final Consumer<RuleGroup> toggleRule;
	private final Map<Option, OptionCard> cardWidgets = new IdentityHashMap<>();
	private final Map<Option, Widget> controls = new IdentityHashMap<>();
	private final Map<RuleGroup, RuleHeaderWidget> headers = new IdentityHashMap<>();
	/** Controls the factory made disabled (e.g. a key that is not registered) stay so, whatever the status. */
	private final Map<Widget, Boolean> ownEnabled = new IdentityHashMap<>();
	/** What the last layout placed: cards and rule headers first, then controls, so every control lies over its card. */
	private List<Widget> widgets = new ArrayList<>();
	private ConfigLayout.Page page;

	ConfigPage(Category category, List<Card> cards, Controls factory) {
		this(category, cards, factory, rule -> { });
	}

	ConfigPage(Category category, List<Card> cards, Controls factory, Consumer<RuleGroup> toggleRule) {
		this.category = category;
		this.cards = cards;
		this.factory = factory;
		this.toggleRule = toggleRule;
		List<Widget> controlWidgets = new ArrayList<>();
		for (Card card : cards) {
			if (card.category() != category) {
				continue;
			}
			for (Option option : card.all()) {
				widgets.add(cardFor(option));
				controlWidgets.add(controls.get(option));
			}
		}
		widgets.addAll(controlWidgets);
	}

	/** A control without a card (a rule header's Remove), made the first time. */
	private Widget controlFor(Option option) {
		Widget existing = controls.get(option);
		if (existing != null) {
			return existing;
		}
		Widget control = factory.control(option);
		controls.put(option, control);
		ownEnabled.put(control, control.isEnabled());
		return control;
	}

	/** The option's card, made (with its control) the first time. */
	private OptionCard cardFor(Option option) {
		OptionCard existing = cardWidgets.get(option);
		if (existing != null) {
			return existing;
		}
		Widget control = factory.control(option);
		ToggleSwitch toggle = option instanceof Toggle && control instanceof ToggleSwitch toggleSwitch ? toggleSwitch : null;
		OptionCard cardWidget = new OptionCard(option, toggle);
		cardWidgets.put(option, cardWidget);
		controls.put(option, control);
		ownEnabled.put(control, control.isEnabled());
		return cardWidget;
	}

	Category category() {
		return category;
	}

	/** The widgets placed by the last layout (before the first, every option's). */
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

	RuleHeaderWidget header(RuleGroup rule) {
		return headers.get(rule);
	}

	/**
	 * The page's keyboard stops in reading order, as the last layout placed them (REQ-UI-18, T2.9b): each option's control,
	 * each rule's header and Remove, and an open rule's fields. The caller keeps those that are focusable and enabled.
	 */
	void focusOrder(List<Widget> out) {
		if (page == null) {
			return;
		}
		for (ConfigLayout.Row row : page.rows()) {
			if (row instanceof ConfigLayout.RuleHeader ruleRow) {
				RuleHeaderWidget header = headers.get(ruleRow.rule());
				if (header != null) {
					out.add(header);
				}
				ActionOption remove = ruleRow.rule().remove();
				Widget control = remove == null ? null : controls.get(remove);
				if (control != null) {
					out.add(control);
				}
			} else if (row instanceof ConfigLayout.OptionRow optionRow) {
				Widget control = controls.get(optionRow.option());
				if (control != null) {
					out.add(control);
				}
			}
		}
	}

	/** The row a page widget stands in (a control, a rule header or its Remove), or null; to scroll it into view. */
	ConfigLayout.Row rowOf(Widget widget) {
		if (page == null || widget == null) {
			return null;
		}
		for (ConfigLayout.Row row : page.rows()) {
			if (row instanceof ConfigLayout.RuleHeader ruleRow) {
				ActionOption remove = ruleRow.rule().remove();
				if (headers.get(ruleRow.rule()) == widget || remove != null && controls.get(remove) == widget) {
					return row;
				}
			} else if (row instanceof ConfigLayout.OptionRow optionRow && controls.get(optionRow.option()) == widget) {
				return row;
			}
		}
		return null;
	}

	/** Places every card and control for this frame and returns the page (for its height and rows). */
	ConfigLayout.Page layout(ConfigLayout.Frame frame, int scroll, ToIntFunction<String> width) {
		return layout(frame, scroll, width, ConfigLayout.Filter.of(option -> true));
	}

	/** Places what {@code filter} shows (a search, the open rules); the rest is hidden. */
	ConfigLayout.Page layout(ConfigLayout.Frame frame, int scroll, ToIntFunction<String> width, ConfigLayout.Filter filter) {
		page = ConfigLayout.page(cards, category, frame.content().w(), width, filter);
		ConfigLayout.Rect content = frame.content();
		ClipRect clip = ClipRect.of(content.x(), content.y(), content.w(), content.h());
		List<Widget> placedCards = new ArrayList<>();
		List<Widget> placedControls = new ArrayList<>();
		for (ConfigLayout.Row row : page.rows()) {
			if (row instanceof ConfigLayout.RuleHeader ruleRow) {
				RuleHeaderWidget header = headers.computeIfAbsent(ruleRow.rule(), rule -> new RuleHeaderWidget(rule, () -> toggleRule.accept(rule)));
				ConfigLayout.Rect rect = ConfigLayout.row(frame, scroll, ruleRow);
				header.setBounds(rect.x(), rect.y(), rect.w(), rect.h());
				header.setClip(clip);
				header.setExpanded(ruleRow.expanded());
				header.setDimmed(featureOff(ruleRow.card()));
				placedCards.add(header);
				// Remove, over the header's right end (R31); placed with the controls, so it lies over the header and gets the click.
				ActionOption remove = ruleRow.rule().remove();
				if (remove == null) {
					header.setActionRoom(0);
				} else {
					Widget control = controlFor(remove);
					ConfigLayout.Rect actionRect = ConfigLayout.headerAction(rect);
					control.setBounds(actionRect.x(), actionRect.y(), actionRect.w(), actionRect.h());
					control.setClip(clip);
					control.setDimmed(featureOff(ruleRow.card()));
					control.setEnabled(ownEnabled.get(control));
					header.setActionRoom(rect.right() - actionRect.x());
					placedControls.add(control);
				}
				continue;
			}
			if (!(row instanceof ConfigLayout.OptionRow optionRow)) {
				continue;
			}
			Option option = optionRow.option();
			ConfigLayout.Rect rect = ConfigLayout.card(frame, scroll, optionRow);
			ConfigLayout.Rect controlRect = ConfigLayout.control(rect, option);
			OptionCard cardWidget = cardFor(option);
			Widget control = controls.get(option);
			cardWidget.setBounds(rect.x(), rect.y(), rect.w(), rect.h());
			control.setBounds(controlRect.x(), controlRect.y(), controlRect.w(), controlRect.h());
			cardWidget.setClip(clip);
			cardWidget.fitText(width);
			control.setClip(clip);
			boolean off = !optionRow.featureToggle() && featureOff(optionRow.card());
			cardWidget.setDimmed(off);
			control.setDimmed(off);
			boolean available = option.status().state() != OptionStatus.State.UNAVAILABLE;
			cardWidget.setEnabled(available);
			control.setEnabled(available && ownEnabled.get(control));
			placedCards.add(cardWidget);
			placedControls.add(control);
		}
		List<Widget> placed = new ArrayList<>(placedCards);
		placed.addAll(placedControls);
		Set<Widget> shown = Collections.newSetFromMap(new IdentityHashMap<>());
		shown.addAll(placed);
		// Anything not placed this frame (filtered out, a closed or deleted rule) can neither draw nor be clicked.
		for (Widget widget : widgets) {
			if (!shown.contains(widget)) {
				hide(widget);
			}
		}
		widgets = placed;
		forgetDeletedRules();
		return page;
	}

	/** Drops the widgets of rules that are gone (deleted), so they do not pile up while the screen is open. */
	private void forgetDeletedRules() {
		Set<RuleGroup> live = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Card card : cards) {
			if (card.category() == category) {
				live.addAll(card.rules());
			}
		}
		headers.keySet().removeIf(rule -> {
			if (live.contains(rule)) {
				return false;
			}
			if (rule.remove() != null) {
				ownEnabled.remove(controls.remove(rule.remove()));
			}
			for (Option field : rule.fields()) {
				cardWidgets.remove(field);
				ownEnabled.remove(controls.remove(field));
			}
			return true;
		});
	}

	/** Whether the card's feature is switched off (its sub-options and rules are dimmed, still editable). */
	private static boolean featureOff(Card card) {
		Toggle feature = card.toggle();
		return feature != null && !feature.binding().get();
	}

	private static void hide(Widget widget) {
		widget.setBounds(0, 0, 0, 0);
		widget.setClip(ClipRect.of(0, 0, 0, 0));
	}
}
