package com.k8bas.skyblockutility.net;

import java.net.http.HttpClient;

/**
 * One HttpClient for the whole mod, instead of one each in MobDatabase, NpcDatabase, and
 * UpdateChecker. Each HttpClient owns its own selector thread and connection pool, so three
 * separate instances meant three sets of those. The holder-class idiom defers actually
 * constructing it until the first real request rather than just until this class happens to
 * load — the mob/NPC databases are only fetched lazily when their picker screen is opened, so a
 * session that never opens either one still avoids paying for this even though the update
 * checker (on by default) will normally trigger it once per launch regardless.
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
