package com.k8bas.skyblockutility.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * One HttpClient for the whole mod, instead of one each in MobDatabase, NpcDatabase, and
 * UpdateChecker. Each HttpClient owns its own selector thread and connection pool, so three
 * separate instances meant three sets of those, all now paid once instead of three times. The
 * holder-class idiom defers actually constructing it until the first real request rather than
 * just until this class happens to load, though in practice both databases fetch at startup and
 * the update checker is on by default, so a typical launch triggers this immediately regardless.
 */
public final class SharedHttpClient {
	/** No request may hang: at most 10 s to connect and 10 s for the response (REQ-NPCDB-05). */
	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
	private static final String USER_AGENT = "Kesuhi/k8bas-skyblock-utility (https://github.com/Kesuhi/K8basSkyblockUtility)";

	private SharedHttpClient() {
	}

	private static final class Holder {
		private static final HttpClient INSTANCE = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
	}

	public static HttpClient get() {
		return Holder.INSTANCE;
	}

	/** The body of a GET, or null after exactly one warning (an HTTP error, a timeout or a network
	 *  failure). Blocks, so it runs off the client thread. */
	public static String fetchText(URI uri, String what, Consumer<String> warn) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(REQUEST_TIMEOUT)
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();
		try {
			HttpResponse<String> response = get().send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				warn.accept(what + " got HTTP " + response.statusCode());
				return null;
			}
			return response.body();
		} catch (HttpTimeoutException e) {
			warn.accept(what + " timed out (" + e.getMessage() + ")");
		} catch (IOException e) {
			warn.accept(what + " failed: " + e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			warn.accept(what + " was interrupted");
		}
		return null;
	}
}
