package com.k8bas.skyblockutility.net;

import java.net.http.HttpClient;

/**
 * One HttpClient for the whole mod, instead of one each in MobDatabase, NpcDatabase, and
 * UpdateChecker. Each HttpClient owns its own selector thread and connection pool, so three
 * separate instances meant three sets of those, all now paid once instead of three times. The
 * holder-class idiom defers actually constructing it until the first real request rather than
 * just until this class happens to load, though in practice both databases fetch at startup and
 * the update checker is on by default, so a typical launch triggers this immediately regardless.
 */
public final class SharedHttpClient {
	private SharedHttpClient() {
	}

	private static final class Holder {
		private static final HttpClient INSTANCE = HttpClient.newHttpClient();
	}

	public static HttpClient get() {
		return Holder.INSTANCE;
	}
}
