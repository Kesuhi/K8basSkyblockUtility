package com.k8bas.skyblockutility.ui.option;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One rule of a feature's list, shown as a collapsible card (REQ-UI-11): its header has the label and a
 * colour dot, and expanded it shows its fields. A rule that cannot be used has a {@code problem}: a
 * warning sign in the header and the reason under it (REQ-GLOW-10, REQ-UI-12). Its label and name
 * pattern are searchable (REQ-UI-09).
 *
 * @param id           unique across every list, e.g. "mob_highlighter.rule.mob-1"; also its search entry's id
 * @param problem      why the rule does nothing, or null when it works
 * @param searchFields the texts the search matches (label and pattern)
 */
public record RuleGroup(String id, Supplier<String> label, IntSupplier colour, Supplier<String> problem, Supplier<List<String>> searchFields,
		List<Option> fields) {
	public RuleGroup {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(label, "label");
		Objects.requireNonNull(colour, "colour");
		Objects.requireNonNull(problem, "problem");
		Objects.requireNonNull(searchFields, "searchFields");
		fields = List.copyOf(fields);
	}
}
