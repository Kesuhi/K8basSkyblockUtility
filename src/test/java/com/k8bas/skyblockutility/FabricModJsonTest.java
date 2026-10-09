package com.k8bas.skyblockutility;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-PORT-03: the shipped dependency ranges, evaluated with Fabric Loader's own version parser. */
class FabricModJsonTest {
	private static JsonObject modJson() throws IOException {
		try (InputStream in = FabricModJsonTest.class.getResourceAsStream("/fabric.mod.json")) {
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static JsonObject depends() throws IOException {
		return modJson().getAsJsonObject("depends");
	}

	private static boolean accepts(String dependency, String version) throws IOException, VersionParsingException {
		return VersionPredicate.parse(depends().get(dependency).getAsString()).test(SemanticVersion.parse(version));
	}

	@Test
	void minecraftAcceptsOnly26Point2() throws Exception {
		assertTrue(accepts("minecraft", "26.2"));
		assertTrue(accepts("minecraft", "26.2.1"));
		assertFalse(accepts("minecraft", "26.1.2"));
		assertFalse(accepts("minecraft", "26.3"));
	}

	@Test
	void hypixelModApiNeedsAtLeast1Point0Point2() throws Exception {
		assertTrue(accepts("hypixel-mod-api", "1.0.2"));
		assertFalse(accepts("hypixel-mod-api", "1.0.1"));
	}

	@Test
	void loaderAndLibrariesMatchTheBuild() throws Exception {
		assertTrue(accepts("fabricloader", "0.19.5"));
		assertFalse(accepts("fabricloader", "0.19.4"));
		assertTrue(accepts("fabric-api", "0.161.0"));
		assertTrue(accepts("render-chest", "1.0.3+26.2"));
		// R29: the settings UI is Kotlin; Fabric Language Kotlin brings its runtime.
		assertTrue(accepts("fabric-language-kotlin", "1.14.1+kotlin.2.4.20"));
		assertFalse(accepts("fabric-language-kotlin", "1.14.0+kotlin.2.4.10"));
		assertFalse(accepts("render-chest", "1.0.2+26.2"));
	}

	/** AC-UI-20 [R] (T2.6): the settings screen is the mod's own; Cloth Config is neither required nor suggested. */
	@Test
	void clothConfigIsNoDependency() throws Exception {
		for (String kind : List.of("depends", "recommends", "suggests")) {
			JsonObject entries = modJson().getAsJsonObject(kind);
			assertTrue(entries == null || !entries.has("cloth-config"), kind);
		}
	}

	/** REQ-UPD-20 (T1.4): Mod Menu must not look this mod up on Modrinth; GitHub is the only source. */
	@Test
	void modMenuDoesNotCheckModrinthForThisMod() throws Exception {
		assertFalse(modJson().getAsJsonObject("custom").getAsJsonObject("modmenu").get("update_checker").getAsBoolean());
	}

	/**
	 * AD-9 (T3.0f): the one mixin config is the optional cosmetic one: not required, injectors not required, its
	 * own package and the hook-status plugin, so a hook that cannot go in never stops the game (REQ-BOB-08). No
	 * mixin forces glowing or visibility (AC-GLOW-05, T1.10; MixinRulesTest and check-forbidden.sh).
	 */
	@Test
	void theOnlyMixinConfigIsTheOptionalCosmeticOne() throws Exception {
		assertEquals(List.of("k8bas_skyblock_utility.mixins.json"),
				modJson().getAsJsonArray("mixins").asList().stream().map(element -> element.getAsString()).toList());
		JsonObject config;
		try (InputStream in = FabricModJsonTest.class.getResourceAsStream("/k8bas_skyblock_utility.mixins.json")) {
			config = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
		assertFalse(config.get("required").getAsBoolean());
		assertEquals(0, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
		assertEquals("com.k8bas.skyblockutility.mixin", config.get("package").getAsString());
		assertEquals("com.k8bas.skyblockutility.hook.MainHookPlugin", config.get("plugin").getAsString());
		assertFalse(config.has("mixins") || config.has("server"), "client-side cosmetic hooks only");
	}
}
