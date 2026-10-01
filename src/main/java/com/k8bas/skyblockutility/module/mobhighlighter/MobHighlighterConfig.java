package com.k8bas.skyblockutility.module.mobhighlighter;

import com.k8bas.skyblockutility.config.Normalizable;
import com.k8bas.skyblockutility.highlight.HighlightRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MobHighlighterConfig implements Normalizable {
	public boolean enabled = true;
	public List<HighlightRule> rules = new ArrayList<>();

	@Override
	public boolean normalize() {
		boolean changed = false;
		if (rules == null) {
			rules = new ArrayList<>();
			changed = true;
		}
		changed |= rules.removeIf(Objects::isNull);
		for (HighlightRule rule : rules) {
			changed |= rule.normalize();
		}
		return changed;
	}
}
