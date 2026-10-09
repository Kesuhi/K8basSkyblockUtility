package com.k8bas.skyblockutility.hook;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.module.bobberfix.BobberFixModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shipped mixin config and the hooks its plugin declares agree (T3.0f): every mixin listed has a hook, so its
 * errors are this mod's to report with one warning, and every hook names a listed mixin. Ids and handlers are
 * unique.
 */
class MainHookPluginTest {
	private static final String PACKAGE = "com.k8bas.skyblockutility.mixin.";

	@Test
	void everyListedMixinHasAHookAndEveryHookAListedMixin() throws IOException {
		JsonObject config;
		try (InputStream in = MainHookPluginTest.class.getResourceAsStream("/k8bas_skyblock_utility.mixins.json")) {
			config = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		Set<String> listed = config.getAsJsonArray("client").asList().stream().map(name -> PACKAGE + name.getAsString()).collect(Collectors.toSet());
		List<Hook> hooks = new MainHookPlugin().hooks();
		assertEquals(listed, hooks.stream().map(Hook::mixin).collect(Collectors.toSet()));
		assertEquals(hooks.size(), hooks.stream().map(Hook::id).distinct().count(), "unique ids");
		assertEquals(hooks.size(), hooks.stream().map(Hook::handler).distinct().count(), "unique handlers");
	}

	@Test
	void theFeatureHooksAreTheirCardsOwn() {
		// The plugin spells the ids out (it runs before the mods are initialised); they must be the ones the card asks about.
		assertEquals(List.of(BobberFixModule.LINK_HOOK, BobberFixModule.LAVA_HOOK, com.k8bas.skyblockutility.module.hotspot.HotspotModule.PARTICLE_HOOK,
				com.k8bas.skyblockutility.module.drillfix.DrillFixModule.HOOK),
				new MainHookPlugin().hooks().stream().map(Hook::id).toList());
	}
}
