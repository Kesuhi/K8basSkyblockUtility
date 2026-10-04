package com.k8bas.skyblockutility.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.config.GeneralConfig;
import com.k8bas.skyblockutility.config.HudConfig;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The elements' positions in `general.hud.positions` (REQ-HUD-03). Reading never writes: an element with
 * no entry uses its default, and a malformed entry its default place with its scale held to the range,
 * logged once and left in the file until the player saves a new position (EC-HUD-05). Entries for ids no
 * element has are kept as they are (EC-HUD-04). A parsed entry is reused until it changes, so the draw path
 * only looks it up. Nothing here writes the file; the editor saves.
 */
public final class HudPositions {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/hud");
	/**
	 * The live positions, in the config file's general section. A malformed entry is logged with the next frame's
	 * tasks, not from the draw path that read it (REQ-HUD-11).
	 */
	public static final HudPositions LIVE = new HudPositions(() -> ConfigManager.general().hud, () -> {
		GeneralConfig general = ConfigManager.general();
		if (general.hud == null) {
			general.hud = new HudConfig();
		}
		return general.hud;
	}, message -> Minecraft.getInstance().schedule(() -> LOGGER.warn("{}", message)));

	private record Parsed(JsonElement raw, HudPosition fallback, HudPosition position) {
	}

	private final Supplier<HudConfig> read;
	private final Supplier<HudConfig> write;
	private final Consumer<String> log;
	private final Map<String, Parsed> parsed = new HashMap<>();
	private final Set<String> logged = new HashSet<>();

	/**
	 * @param read  the section as it is (null while absent)
	 * @param write the section, created when absent
	 */
	public HudPositions(Supplier<HudConfig> read, Supplier<HudConfig> write, Consumer<String> log) {
		this.read = read;
		this.write = write;
		this.log = log;
	}

	/** Where element {@code id} is; {@code fallback} (its default) when it has no usable entry. */
	public HudPosition get(String id, HudPosition fallback) {
		HudConfig config = read.get();
		JsonElement raw = config == null || config.positions == null ? null : config.positions.get(id);
		if (raw == null) {
			return fallback;
		}
		Parsed last = parsed.get(id);
		if (last != null && last.raw == raw && last.fallback.equals(fallback)) {
			return last.position;
		}
		HudPosition position = parse(id, raw, fallback);
		parsed.put(id, new Parsed(raw, fallback, position));
		return position;
	}

	/** Element {@code id}'s entry as written, or null. */
	public JsonElement raw(String id) {
		HudConfig config = read.get();
		return config == null || config.positions == null ? null : config.positions.get(id);
	}

	/** Puts back an entry as it was written ({@link #raw}), or removes it when it was null. */
	public void restore(String id, JsonElement raw) {
		if (raw != null) {
			HudConfig config = write.get();
			if (config.positions == null) {
				config.positions = new LinkedHashMap<>();
			}
			config.positions.put(id, raw);
		} else if (read.get() != null && read.get().positions != null) {
			read.get().positions.remove(id);
		}
	}

	/** Whether element {@code id} has an entry in the config, usable or not. */
	public boolean has(String id) {
		HudConfig config = read.get();
		return config != null && config.positions != null && config.positions.containsKey(id);
	}

	/** Stores element {@code id}'s position in the config; the caller saves. */
	public void set(String id, HudPosition position) {
		JsonObject entry = new JsonObject();
		entry.addProperty("anchor", position.anchor().name());
		entry.addProperty("x", position.x());
		entry.addProperty("y", position.y());
		entry.addProperty("scale", position.scale());
		HudConfig config = write.get();
		if (config.positions == null) {
			config.positions = new LinkedHashMap<>();
		}
		config.positions.put(id, entry);
	}

	private HudPosition parse(String id, JsonElement raw, HudPosition fallback) {
		if (raw.isJsonObject()) {
			JsonObject entry = raw.getAsJsonObject();
			HudAnchor anchor = entry.get("anchor") instanceof JsonPrimitive name && name.isString() ? HudAnchor.named(name.getAsString()) : null;
			Integer x = integer(entry.get("x"));
			Integer y = integer(entry.get("y"));
			double scale = number(entry.get("scale"));
			if (anchor != null && x != null && y != null && HudScale.inRange(scale)) {
				return new HudPosition(anchor, x, y, scale);
			}
			malformed(id, raw);
			return fallback.withScale(Double.isFinite(scale) ? scale : fallback.scale());
		}
		malformed(id, raw);
		return fallback;
	}

	private void malformed(String id, JsonElement raw) {
		if (logged.add(id + "|" + raw)) {
			log.accept("HUD position of '" + id + "' is malformed (" + raw + "); using its default place until a new one is saved");
		}
	}

	private static Integer integer(JsonElement value) {
		double number = number(value);
		return Double.isFinite(number) && number == Math.rint(number) && Math.abs(number) < 100_000 ? (int) number : null;
	}

	/** A JSON number (or numeric string), or NaN. */
	private static double number(JsonElement value) {
		if (!(value instanceof JsonPrimitive primitive) || primitive.isBoolean()) {
			return Double.NaN;
		}
		try {
			return primitive.getAsDouble();
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}
}
