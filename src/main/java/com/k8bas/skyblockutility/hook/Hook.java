package com.k8bas.skyblockutility.hook;

/**
 * One optional cosmetic game hook (T3.0f, AD-9): the handler {@code handler} of {@code mixin}, injected into
 * {@code target}.
 *
 * @param id          the name feature cards ask about, e.g. {@code bobber}
 * @param mixin       the mixin class, fully qualified
 * @param target      the target class, dotted ({@code net.minecraft.client...})
 * @param handler     the handler method's name in the mixin, {@code k8bas$...}, unique among this mod's mixins
 * @param unavailable the card's text when the hook is skipped; blank for the default
 */
public record Hook(String id, String mixin, String target, String handler, String unavailable) {
	static final String DEFAULT_UNAVAILABLE = "Unavailable: its game hook could not be applied";

	/** What the feature's card shows when this hook is skipped. */
	public String unavailableText() {
		return unavailable.isBlank() ? DEFAULT_UNAVAILABLE : unavailable;
	}
}
