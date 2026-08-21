package com.k8bas.skyblockutility.net;

import java.net.http.HttpClient;

/**
 * One HttpClient for the whole mod, instead of one each in MobDatabase, NpcDatabase, and
 * UpdateChecker. Each HttpClient owns its own selector thread and connection pool, so three
 * separate instances meant three sets of those — paid at class-init time even for a session that
 * never actually fetches anything (e.g. auto-update checking is off by default). The holder-class
 * idiom defers actually constructing it until the first real request, not just until this class
 * happens to load.
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
