package com.k8bas.skyblockutility.settings;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * How the rule editors mark a rule that cannot be used (REQ-GLOW-10): its title gets a warning sign
 * (Cloth Config draws folder titles in its own colour), and a red line inside says why. The rule
 * stays in the list, does nothing, and works again as soon as it is fixed.
 */
public final class RuleWarning {
	private RuleWarning() {
	}

	/** The rule's folder title; marked when {@code inertReason} is not null. */
	public static Component title(String label, String inertReason) {
		return Component.literal(inertReason == null ? label : "⚠ " + label);
	}

	/** The explanation shown first inside the folder of a rule that cannot be used. */
	public static AbstractConfigListEntry<?> explanation(ConfigEntryBuilder entryBuilder, String inertReason) {
		return entryBuilder.startTextDescription(Component.literal("⚠ This rule can't be used: " + inertReason
				+ ". It is kept, but does nothing until you change it.").withStyle(ChatFormatting.RED)).build();
	}
}
