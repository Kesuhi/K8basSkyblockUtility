package com.k8bas.skyblockutility.update

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads this mod's GitHub releases (REQ-UPD-03, REQ-UPD-09, REQ-UPD-20): one unauthenticated GET of
 * the release list, which covers both channels. It sends only the User-Agent (mod id and version),
 * Accept, the pinned API version and If-None-Match; no token, no player data.
 *
 * The constructor takes nulls as the Java one did: a missing part fails inside [fetch], which turns
 * every failure into a [Result] (AC-UPD-10).
 */
class GitHubReleaseSource(
	private val uri: URI?,
	private val client: HttpClient?,
	modVersion: String?,
	private val requestTimeout: Duration?,
) {
	private val userAgent: String = "k8bas_skyblock_utility/$modVersion (+https://github.com/Kesuhi/K8basSkyblockUtility)"

	enum class Kind {
		OK,
		NOT_MODIFIED,
		HTTP_ERROR,
		NO_RESPONSE,
		MALFORMED,
		BAD_REDIRECT,
	}

	/**
	 * Nullable as the Java record's components were.
	 *
	 * @param headers the lower-case rate-limit headers of an HTTP error (retry-after,
	 *                x-ratelimit-remaining, x-ratelimit-reset)
	 * @param problem what went wrong, for the one log line; null on success
	 */
	@JvmRecord
	data class Result(
		val kind: Kind?,
		val status: Int,
		val releases: List<Release>?,
		val etag: String?,
		val headers: Map<String, String>?,
		val problem: String?,
	) {
		override fun toString(): String = "Result[kind=$kind, status=$status, releases=$releases, etag=$etag, headers=$headers, problem=$problem]"

		companion object {
			@JvmStatic
			fun failure(kind: Kind?, status: Int, headers: Map<String, String>?, problem: String?): Result =
				Result(kind, status, java.util.List.of(), null, headers, problem)
		}
	}

	/** Never throws: every failure is a Result, so the back-off always applies (AC-UPD-10). */
	fun fetch(etag: String?): Result = try {
		fetchOrThrow(etag)
	} catch (e: RuntimeException) {
		Result.failure(Kind.NO_RESPONSE, 0, java.util.Map.of(), e.javaClass.simpleName)
	}

	private fun fetchOrThrow(etag: String?): Result {
		var target = uri
		// A 301 (repo renamed) is followed once, and only to the same host (EC-UPD-12).
		for (hop in 0 until 2) {
			val response: HttpResponse<InputStream> = try {
				// The request first, then the client, in Java's argument order.
				val request = request(target, etag)
				client!!.send(request, HttpResponse.BodyHandlers.ofInputStream())
			} catch (e: HttpTimeoutException) {
				return Result.failure(Kind.NO_RESPONSE, 0, java.util.Map.of(), "timed out")
			} catch (e: IOException) {
				return Result.failure(Kind.NO_RESPONSE, 0, java.util.Map.of(), e.javaClass.simpleName + (if (e.message == null) "" else ": " + e.message))
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
				return Result.failure(Kind.NO_RESPONSE, 0, java.util.Map.of(), "interrupted")
			}
			val status = response.statusCode()
			if (status == 301) {
				close(response)
				val next = resolve(target!!, response.headers().firstValue("location"))
				if (next.isEmpty || !sameOrigin(next.get())) {
					return Result.failure(Kind.BAD_REDIRECT, status, java.util.Map.of(), "redirect to " + next.map { it.host }.orElse("nowhere"))
				}
				target = next.get()
				continue
			}
			if (status == 304) {
				close(response)
				return Result(Kind.NOT_MODIFIED, status, java.util.List.of(), etag, java.util.Map.of(), null)
			}
			if (status != 200) {
				close(response)
				return Result.failure(Kind.HTTP_ERROR, status, rateLimitHeaders(response.headers()), "HTTP $status")
			}
			return readBody(response)
		}
		return Result.failure(Kind.BAD_REDIRECT, 301, java.util.Map.of(), "too many redirects")
	}

	private fun request(target: URI?, etag: String?): HttpRequest {
		val builder = HttpRequest.newBuilder(target)
			.timeout(requestTimeout)
			.header("User-Agent", userAgent)
			.header("Accept", "application/vnd.github+json")
			.header("X-GitHub-Api-Version", API_VERSION)
			.GET()
		if (etag != null) {
			builder.header("If-None-Match", etag)
		}
		return builder.build()
	}

	/** A base without a scheme or host fails here as Java's equalsIgnoreCase did (and [fetch] reports it). */
	private fun sameOrigin(next: URI): Boolean =
		uri!!.scheme!!.equals(next.scheme, ignoreCase = true) && uri.host!!.equals(next.host, ignoreCase = true) && uri.port == next.port

	private fun readBody(response: HttpResponse<InputStream>): Result {
		val body: ByteArray
		val input = response.body()
		// The request timeout ends when the headers arrive; a body that stalls is cut off by
		// closing the stream after the same time again.
		val timedOut = AtomicBoolean()
		val watchdog = Thread.ofVirtual().name("k8bas-update-body-timeout").start {
			try {
				Thread.sleep(requestTimeout!!)
				timedOut.set(true)
				input.close()
			} catch (finished: InterruptedException) {
				// read in time
			} catch (finished: IOException) {
				// already closed
			}
		}
		try {
			body = input.use { it.readNBytes(MAX_BODY_BYTES + 1) }
		} catch (e: IOException) {
			return Result.failure(Kind.NO_RESPONSE, 200, java.util.Map.of(), if (timedOut.get()) "timed out" else "body: " + e.javaClass.simpleName)
		} finally {
			watchdog.interrupt()
		}
		if (body.size > MAX_BODY_BYTES) {
			return Result.failure(Kind.MALFORMED, 200, java.util.Map.of(), "the release list is larger than 2 MiB")
		}
		return try {
			val root = JsonParser.parseString(String(body, StandardCharsets.UTF_8))
			if (!root.isJsonArray) {
				Result.failure(Kind.MALFORMED, 200, java.util.Map.of(), "the release list is not a JSON array")
			} else {
				Result(Kind.OK, 200, parseReleases(root.asJsonArray), response.headers().firstValue("etag").orElse(null), java.util.Map.of(), null)
			}
		} catch (e: JsonParseException) {
			Result.failure(Kind.MALFORMED, 200, java.util.Map.of(), "the release list is not valid JSON")
		}
	}

	companion object {
		@JvmField
		val RELEASES: URI = URI.create("https://api.github.com/repos/Kesuhi/K8basSkyblockUtility/releases?per_page=30")
		const val API_VERSION: String = "2022-11-28"

		@JvmField
		val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(20)
		const val MAX_BODY_BYTES: Int = 2 * 1024 * 1024

		/** Points the updater at a test server; honoured only in the development environment. */
		const val SOURCE_PROPERTY: String = "k8bas.update.source"

		/** The release list URL: the override only in the development environment, else GitHub. */
		@JvmStatic
		fun resolve(override: String?, developmentEnvironment: Boolean): URI =
			// Java's String.isBlank: Character.isWhitespace only.
			if (developmentEnvironment && override != null && !override.all(Character::isWhitespace)) URI.create(override) else RELEASES

		private fun resolve(base: URI, location: Optional<String>): Optional<URI> = try {
			location.map { base.resolve(it) }
		} catch (malformed: IllegalArgumentException) {
			Optional.empty()
		}

		/** Malformed releases and assets are skipped; the rest are kept (REQ-UPD-09). */
		@JvmStatic
		fun parseReleases(array: JsonArray): List<Release> {
			val releases = ArrayList<Release>()
			for (element in array) {
				try {
					val obj = element.asJsonObject
					val tag = string(obj, "tag_name")
					val htmlUrl = string(obj, "html_url")
					if (tag == null || htmlUrl == null) {
						continue
					}
					val assets = ArrayList<Release.Asset>()
					val assetArray = obj.get("assets")
					if (assetArray is JsonArray) {
						for (assetElement in assetArray) {
							parseAsset(assetElement)?.let { assets.add(it) }
						}
					}
					releases.add(Release(tag, htmlUrl, bool(obj, "draft"), bool(obj, "prerelease"), assets))
				} catch (malformed: RuntimeException) {
					// skipped
				}
			}
			return java.util.List.copyOf(releases)
		}

		private fun parseAsset(element: JsonElement): Release.Asset? = try {
			val obj = element.asJsonObject
			val name = string(obj, "name")
			if (name == null) {
				null
			} else {
				val s = obj.get("size")
				val size = if (s != null && s.isJsonPrimitive) s.asLong else 0L
				Release.Asset(name, string(obj, "state"), string(obj, "browser_download_url"), string(obj, "digest"), size)
			}
		} catch (malformed: RuntimeException) {
			null
		}

		private fun string(obj: JsonObject, key: String): String? {
			val value = obj.get(key)
			return if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
		}

		private fun bool(obj: JsonObject, key: String): Boolean {
			val value = obj.get(key)
			return value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && value.asBoolean
		}

		private fun rateLimitHeaders(headers: HttpHeaders): Map<String, String> {
			val result = HashMap<String, String>()
			for (name in listOf("retry-after", "x-ratelimit-remaining", "x-ratelimit-reset")) {
				// Java's String.trim: code points up to U+0020.
				headers.firstValue(name).ifPresent { value -> result[name.lowercase(Locale.ROOT)] = value.trim { it <= ' ' } }
			}
			return java.util.Map.copyOf(result)
		}

		private fun close(response: HttpResponse<InputStream>) {
			try {
				response.body().close()
			} catch (ignored: IOException) {
				// nothing to release
			}
		}
	}
}
