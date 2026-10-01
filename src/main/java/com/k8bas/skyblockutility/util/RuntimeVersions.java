package com.k8bas.skyblockutility.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;
import java.util.function.Function;

/** Versions as the running game reports them, never hard-coded (REQ-PORT-10). */
public final class RuntimeVersions {
	public static final String UNKNOWN = "unknown";

	private RuntimeVersions() {
	}

	/** The running Minecraft version, from the Loader. */
	public static String minecraft() {
		return versionOf("minecraft", FabricLoader.getInstance()::getModContainer);
	}

	/** This mod's own version, from the Loader. */
	public static String mod(String modId) {
		return versionOf(modId, FabricLoader.getInstance()::getModContainer);
	}

	static String versionOf(String id, Function<String, Optional<ModContainer>> loader) {
		return loader.apply(id).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse(UNKNOWN);
	}
}
