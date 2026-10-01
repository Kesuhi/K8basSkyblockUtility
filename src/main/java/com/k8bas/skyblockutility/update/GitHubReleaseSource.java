package com.k8bas.skyblockutility.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads this mod's GitHub releases (REQ-UPD-03, REQ-UPD-09, REQ-UPD-20): one unauthenticated GET of
 * the release list, which covers both channels. It sends only the User-Agent (mod id and version),
 * Accept, the pinned API version and If-None-Match; no token, no player data.
 */
final class GitHubReleaseSource {
	static final URI RELEASES = URI.create("https://api.github.com/repos/Kesuhi/K8basSkyblockUtility/releases?per_page=30");
	static final String API_VERSION = "2022-11-28";
	static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
	static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
	/** Points the updater at a test server; honoured only in the development environment. */
	static final String SOURCE_PROPERTY = "k8bas.update.source";

	enum Kind {
		OK, NOT_MODIFIED, HTTP_ERROR, NO_RESPONSE, MALFORMED, BAD_REDIRECT
	}

	/**
	 * @param headers the lower-case rate-limit headers of an HTTP error (retry-after,
	 *                x-ratelimit-remaining, x-ratelimit-reset)
	 * @param problem what went wrong, for the one log line; null on success
	 */
	record Result(Kind kind, int status, List<Release> releases, String etag, Map<String, String> headers, String problem) {
		static Result failure(Kind kind, int status, Map<String, String> headers, String problem) {
			return new Result(kind, status, List.of(), null, headers, problem);
		}
	}

	private final URI uri;
	private final HttpClient client;
	private final String userAgent;
	private final Duration requestTimeout;

	GitHubReleaseSource(URI uri, HttpClient client, String modVersion, Duration requestTimeout) {
		this.uri = uri;
		this.client = client;
		this.userAgent = "k8bas_skyblock_utility/" + modVersion + " (+https://github.com/Kesuhi/K8basSkyblockUtility)";
		this.requestTimeout = requestTimeout;
	}

	/** The release list URL: the override only in the development environment, else GitHub. */
	static URI resolve(String override, boolean developmentEnvironment) {
		return developmentEnvironment && override != null && !override.isBlank() ? URI.create(override) : RELEASES;
	}

	/** Never throws: every failure is a Result, so the back-off always applies (AC-UPD-10). */
	Result fetch(String etag) {
		try {
			return fetchOrThrow(etag);
		} catch (RuntimeException e) {
			return Result.failure(Kind.NO_RESPONSE, 0, Map.of(), e.getClass().getSimpleName());
		}
	}

	private Result fetchOrThrow(String etag) {
		URI target = uri;
		// A 301 (repo renamed) is followed once, and only to the same host (EC-UPD-12).
		for (int hop = 0; hop < 2; hop++) {
			HttpResponse<InputStream> response;
			try {
				response = client.send(request(target, etag), HttpResponse.BodyHandlers.ofInputStream());
			} catch (HttpTimeoutException e) {
				return Result.failure(Kind.NO_RESPONSE, 0, Map.of(), "timed out");
			} catch (IOException e) {
				return Result.failure(Kind.NO_RESPONSE, 0, Map.of(), e.getClass().getSimpleName()
						+ (e.getMessage() == null ? "" : ": " + e.getMessage()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Result.failure(Kind.NO_RESPONSE, 0, Map.of(), "interrupted");
			}
			int status = response.statusCode();
			if (status == 301) {
				close(response);
				Optional<URI> next = resolve(target, response.headers().firstValue("location"));
				if (next.isEmpty() || !sameOrigin(next.get())) {
					return Result.failure(Kind.BAD_REDIRECT, status, Map.of(), "redirect to " + next.map(URI::getHost).orElse("nowhere"));
				}
				target = next.get();
				continue;
			}
			if (status == 304) {
				close(response);
				return new Result(Kind.NOT_MODIFIED, status, List.of(), etag, Map.of(), null);
			}
			if (status != 200) {
				close(response);
				return Result.failure(Kind.HTTP_ERROR, status, rateLimitHeaders(response.headers()), "HTTP " + status);
			}
			return readBody(response);
		}
		return Result.failure(Kind.BAD_REDIRECT, 301, Map.of(), "too many redirects");
	}

	private static Optional<URI> resolve(URI base, Optional<String> location) {
		try {
			return location.map(base::resolve);
		} catch (IllegalArgumentException malformed) {
			return Optional.empty();
		}
	}

	private HttpRequest request(URI target, String etag) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(target)
				.timeout(requestTimeout)
				.header("User-Agent", userAgent)
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", API_VERSION)
				.GET();
		if (etag != null) {
			builder.header("If-None-Match", etag);
		}
		return builder.build();
	}

	private boolean sameOrigin(URI next) {
		return uri.getScheme().equalsIgnoreCase(next.getScheme()) && uri.getHost().equalsIgnoreCase(next.getHost())
				&& uri.getPort() == next.getPort();
	}

	private Result readBody(HttpResponse<InputStream> response) {
		byte[] body;
		InputStream in = response.body();
		// The request timeout ends when the headers arrive; a body that stalls is cut off by
		// closing the stream after the same time again.
		Thread watchdog = Thread.ofVirtual().name("k8bas-update-body-timeout").start(() -> {
			try {
				Thread.sleep(requestTimeout);
				in.close();
			} catch (InterruptedException | IOException finished) {
				// read in time, or already closed
			}
		});
		try (in) {
			body = in.readNBytes(MAX_BODY_BYTES + 1);
		} catch (IOException e) {
			return Result.failure(Kind.NO_RESPONSE, 200, Map.of(), watchdog.isAlive() ? "body: " + e.getClass().getSimpleName() : "timed out");
		} finally {
			watchdog.interrupt();
		}
		if (body.length > MAX_BODY_BYTES) {
			return Result.failure(Kind.MALFORMED, 200, Map.of(), "the release list is larger than 2 MiB");
		}
		try {
			JsonElement root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (!root.isJsonArray()) {
				return Result.failure(Kind.MALFORMED, 200, Map.of(), "the release list is not a JSON array");
			}
			return new Result(Kind.OK, 200, parseReleases(root.getAsJsonArray()),
					response.headers().firstValue("etag").orElse(null), Map.of(), null);
		} catch (JsonParseException e) {
			return Result.failure(Kind.MALFORMED, 200, Map.of(), "the release list is not valid JSON");
		}
	}

	/** Malformed releases and assets are skipped; the rest are kept (REQ-UPD-09). */
	static List<Release> parseReleases(JsonArray array) {
		List<Release> releases = new ArrayList<>();
		for (JsonElement element : array) {
			try {
				JsonObject object = element.getAsJsonObject();
				String tag = string(object, "tag_name");
				String htmlUrl = string(object, "html_url");
				if (tag == null || htmlUrl == null) {
					continue;
				}
				List<Release.Asset> assets = new ArrayList<>();
				if (object.get("assets") instanceof JsonArray assetArray) {
					for (JsonElement assetElement : assetArray) {
						Release.Asset asset = parseAsset(assetElement);
						if (asset != null) {
							assets.add(asset);
						}
					}
				}
				releases.add(new Release(tag, htmlUrl, bool(object, "draft"), bool(object, "prerelease"), assets));
			} catch (RuntimeException malformed) {
				// skipped
			}
		}
		return List.copyOf(releases);
	}

	private static Release.Asset parseAsset(JsonElement element) {
		try {
			JsonObject object = element.getAsJsonObject();
			String name = string(object, "name");
			if (name == null) {
				return null;
			}
			long size = object.get("size") instanceof JsonElement s && s.isJsonPrimitive() ? s.getAsLong() : 0;
			return new Release.Asset(name, string(object, "state"), string(object, "browser_download_url"), string(object, "digest"), size);
		} catch (RuntimeException malformed) {
			return null;
		}
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
	}

	private static boolean bool(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
	}

	private static Map<String, String> rateLimitHeaders(HttpHeaders headers) {
		Map<String, String> result = new HashMap<>();
		for (String name : List.of("retry-after", "x-ratelimit-remaining", "x-ratelimit-reset")) {
			headers.firstValue(name).ifPresent(value -> result.put(name.toLowerCase(Locale.ROOT), value.trim()));
		}
		return Map.copyOf(result);
	}

	private static void close(HttpResponse<InputStream> response) {
		try {
			response.body().close();
		} catch (IOException ignored) {
			// nothing to release
		}
	}
}
