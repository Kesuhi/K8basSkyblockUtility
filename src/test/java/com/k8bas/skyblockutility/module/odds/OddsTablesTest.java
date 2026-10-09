package com.k8bas.skyblockutility.module.odds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped odds tables (T3.9g, REQ-ODDS-09/10) agree with SPEC's fixtures: the Vanguard weights total 3,515 without
 * SkyOcean's assumed dye (AC-ODDS-07's Caged Wisp 10 and Shattered Locket 5), Warden Heart needs 3,674,250 slayer XP,
 * and the table for pages you supply is empty until you supply them. The schema and row rules are the validator's.
 */
class OddsTablesTest {
	private static final Path DATA = Path.of(System.getProperty("k8bas.projectDir", ".")).resolve("src/main/resources/assets/k8bas_skyblock_utility/data");

	private static JsonObject table(String path) throws IOException {
		return JsonParser.parseString(Files.readString(DATA.resolve(path))).getAsJsonObject();
	}

	private static JsonObject row(JsonObject table, String id) {
		return StreamSupport.stream(table.getAsJsonArray("rows").spliterator(), false).map(JsonElement::getAsJsonObject)
				.filter(row -> row.get("id").getAsString().equals(id)).findFirst().orElseThrow(() -> new AssertionError("no row " + id));
	}

	@Test
	void theVanguardWeightsAreSkyOceansWithoutTheAssumedDye() throws IOException {
		JsonObject vanguard = table("thirdparty/skyocean/odds/vanguard.json");
		JsonArray rows = vanguard.getAsJsonArray("rows");
		int sum = StreamSupport.stream(rows.spliterator(), false).mapToInt(row -> row.getAsJsonObject().get("weight").getAsInt()).sum();
		assertEquals(3515, sum);
		assertEquals(3515, vanguard.get("totalWeight").getAsInt());
		assertEquals(10, row(vanguard, "CAGED_WISP").get("weight").getAsInt());
		assertEquals(5, row(vanguard, "SHATTERED_PENDANT").get("weight").getAsInt());
		assertEquals("Shattered Locket", row(vanguard, "SHATTERED_PENDANT").get("displayName").getAsString());
		assertFalse(rows.toString().contains("DYE"), "the dye's assumed weight is never used (R6)");
		assertFalse(vanguard.has("rolls"), "no roll counts until a page gives them (D-16 (a))");
	}

	@Test
	void theRngScoresAreNeusWithTheDyesFlagged() throws IOException {
		JsonObject scores = table("thirdparty/neu-repo/odds/rngscore.json");
		assertEquals(3_674_250, row(scores, "slayer:Revenant Horror:WARDEN_HEART").get("score").getAsInt());
		assertEquals(416_820, row(scores, "catacombs:M7:DARK_CLAYMORE").get("score").getAsInt());
		for (JsonElement element : scores.getAsJsonArray("rows")) {
			JsonObject row = element.getAsJsonObject();
			boolean dye = row.get("item").getAsString().startsWith("DYE_");
			assertEquals(dye, row.has("placeholder") && row.get("placeholder").getAsBoolean(), row.get("id").getAsString());
		}
	}

	@Test
	void theSuppliedTableIsEmptyUntilPagesAreSupplied() throws IOException {
		JsonObject supplied = table("odds/supplied.json");
		assertEquals("CC0-1.0", supplied.get("license").getAsString());
		assertTrue(supplied.getAsJsonArray("rows").isEmpty(), "rows come only with the pages you supply (REQ-ODDS-10, REQ-ODDS-11)");
	}
}
