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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-PORT-03: the shipped dependency ranges, evaluated with Fabric Loader's own version parser. */
class FabricModJsonTest {
	private static JsonObject depends() throws IOException {
		try (InputStream in = FabricModJsonTest.class.getResourceAsStream("/fabric.mod.json")) {
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("depends");
		}
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
		assertTrue(accepts("cloth-config", "26.2.155"));
	}
}
