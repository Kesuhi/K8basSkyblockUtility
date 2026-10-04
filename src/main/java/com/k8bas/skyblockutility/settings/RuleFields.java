package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.highlight.NameMatchMode;
import com.k8bas.skyblockutility.location.Islands;
import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.option.Choice;
import com.k8bas.skyblockutility.ui.option.OptionText;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fields both rule lists share (REQ-UI-11): the island picked from the location list (REQ-LOC-09)
 * and the name match mode. Pure, so the rule cards can be tested without a game.
 */
public final class RuleFields {
	/** The island choice for "every island": stored as no island (null). */
	public static final String ANY_ISLAND = "Any island";

	private RuleFields() {
	}

	/**
	 * The island list, with "Any island" first. A stored name that is not in the list (a hand edit, an
	 * island added later) is offered too, so it is never lost (REQ-UI-16).
	 *
	 * @param get the stored island, null for every island
	 * @param set stores an island, null for every island
	 */
	public static Choice<String> island(String id, String storageKey, Supplier<String> get, Consumer<String> set) {
		List<String> values = new ArrayList<>();
		values.add(ANY_ISLAND);
		Islands.all().forEach(island -> values.add(island.name()));
		String stored = get.get();
		if (stored != null && !stored.isBlank() && !values.contains(stored)) {
			values.add(stored);
		}
		return Choice.of(id, storageKey,
				new OptionText("Island", "Only on this island; \"Any island\" for all of them.", "", List.of("island", "location", "area")),
				ANY_ISLAND, values, value -> value, Binding.of(() -> {
					String island = get.get();
					return island == null || island.isBlank() ? ANY_ISLAND : island;
				}, value -> set.accept(ANY_ISLAND.equals(value) ? null : value)));
	}

	public static Choice<NameMatchMode> matchMode(String id, String storageKey, Supplier<NameMatchMode> get, Consumer<NameMatchMode> set) {
		return matchMode(id, storageKey, get, set, true);
	}

	/**
	 * @param anyName whether "Any name" is offered: not for NPC rules, which have no entity type to fall
	 *                back on (it could never work); a rule that already has it keeps it (REQ-UI-16)
	 */
	public static Choice<NameMatchMode> matchMode(String id, String storageKey, Supplier<NameMatchMode> get, Consumer<NameMatchMode> set,
			boolean anyName) {
		List<NameMatchMode> modes = new ArrayList<>(List.of(NameMatchMode.CONTAINS, NameMatchMode.EXACT, NameMatchMode.REGEX));
		if (anyName || get.get() == NameMatchMode.NONE) {
			modes.add(NameMatchMode.NONE);
		}
		return Choice.of(id, storageKey,
				new OptionText("Name match", "How the name pattern is compared with an entity's name.",
						"Contains: the name includes the pattern. Exact: the whole name equals it. Regular expression: the pattern is a "
								+ "Java regex. Any name: only the entity type counts.",
						List.of("match", "regex", "pattern", "name")),
				NameMatchMode.CONTAINS, modes,
				RuleFields::label, Binding.of(() -> get.get() == null ? NameMatchMode.CONTAINS : get.get(), set));
	}

	public static String label(NameMatchMode mode) {
		return switch (mode) {
			case CONTAINS -> "Contains";
			case EXACT -> "Exact";
			case REGEX -> "Regular expression";
			case NONE -> "Any name";
		};
	}

	/** Whether a rule's problem is about its name pattern (REQ-UI-12: the pattern field is marked). */
	public static boolean aboutPattern(String problem) {
		return problem != null && (problem.contains("pattern") || problem.contains("regular expression"));
	}

	/** Whether a rule's problem is about its entity type (the type field is marked). */
	public static boolean aboutType(String problem) {
		return problem != null && problem.contains("entity type");
	}
}
