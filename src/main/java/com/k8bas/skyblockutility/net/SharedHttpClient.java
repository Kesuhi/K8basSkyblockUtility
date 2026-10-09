package com.k8bas.skyblockutility.net;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The mod's one HttpClient. Since the NPC and mob lists are bundled (T3.8), only the update check uses
 * it. The holder-class idiom defers constructing it, with its selector thread and connection pool, until
 * the first request, so a launch with the update check off never builds it.
 */
public final class SharedHttpClient {
	/** No connection attempt may hang: at most 10 s (REQ-UPD-09); the update check sets its own request timeout. */
	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private SharedHttpClient() {
	}

	private static final class Holder {
		private static final HttpClient INSTANCE = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
	}

	public static HttpClient get() {
		return Holder.INSTANCE;
	}
}
