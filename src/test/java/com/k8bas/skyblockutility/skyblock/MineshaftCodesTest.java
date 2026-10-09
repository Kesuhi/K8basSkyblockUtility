package com.k8bas.skyblockutility.skyblock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k8bas.skyblockutility.data.DataRegistry;
import com.k8bas.skyblockutility.data.TableHandle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-GS-17 [A], first bullet: every one of the 34 known codes in a sidebar line shaped like the G1 capture's
 * (`<date> <server> <code>`) gives its display name, read from the bundled `mining_shafts` table through the
 * registry exactly as the game reads it. AC-GS-18 [R]: the widget hint exists once in the translation file.
 */
class MineshaftCodesTest {
	private static byte[] resource(String path) {
		try (InputStream in = MineshaftCodesTest.class.getResourceAsStream("/assets/k8bas_skyblock_utility/" + path)) {
			return in == null ? null : in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Map<String, String> table() {
		DataRegistry registry = new DataRegistry(path -> resource("data/" + path), Runnable::run, Runnable::run, message -> {
			throw new AssertionError(message);
		});
		TableHandle<Map<String, String>> handle = registry.register(MineshaftTable.SPEC);
		registry.start();
		return handle.value();
	}

	@Test
	void theTableHoldsTheThirtyFourCodes() {
		Map<String, String> names = table();
		assertEquals(34, names.size());
		assertEquals("Opal 1", names.get("OPAL_1"));
		assertEquals("Ruby Crystal", names.get("RUBY_C"));
		assertEquals("Vanguard", names.get("FAIR_1"));
		assertEquals("Littlefoot's Den", names.get("LITT_L"));
		for (String code : names.keySet()) {
			assertTrue(code.matches("[A-Z]{4}_[12CL]"), code);
		}
	}

	@Test
	void everyKnownCodeInTheSidebarGivesItsName() {
		Map<String, String> names = table();
		for (Map.Entry<String, String> shaft : names.entrySet()) {
			MineshaftTracker tracker = new MineshaftTracker(names::get, line -> { });
			tracker.enter(0);
			tracker.sidebar(List.of("10/01/26 m000XX " + shaft.getKey(), "Autumn 10th"), 500);
			assertEquals(shaft.getValue(), tracker.snapshot().getName(), shaft.getKey());
		}
	}

	@Test
	void theCapturedShaftIsKnown() {
		// sidebar-mineshaft.txt (G1): `10/01/26 m000XX UMBE_1`.
		MineshaftTracker tracker = new MineshaftTracker(table()::get, line -> { });
		tracker.enter(0);
		tracker.sidebar(SidebarParser.parse(CaptureFixtures.sidebar("sidebar-mineshaft.txt")).getLines(), 500);
		assertEquals("Umber 1", tracker.snapshot().getName());
	}

	@Test
	void theWidgetHintIsOneTranslationKey() {
		JsonObject lang = JsonParser.parseString(new String(resource("lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		assertTrue(lang.has(WidgetHint.KEY), WidgetHint.KEY);
		assertTrue(lang.get(WidgetHint.KEY).getAsString().contains("%s"), "names the widget");
	}
}
