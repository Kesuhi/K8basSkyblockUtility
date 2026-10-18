package com.k8bas.skyblockutility.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Fetches one release file from GitHub (REQ-UPD-13, REQ-UPD-20): HTTPS to github.com or a *.githubusercontent.com host on
 * every hop, redirects followed by hand (at most 5), a final 200, and exactly the asset's size, by header and by count,
 * under a hard cap. The bytes stream through SHA-256 into the caller's sink; nothing is buffered whole, and the file is
 * never named from the URL. It sends the User-Agent and an Accept header only: no token, no cookie, no player data.
 * Downloads by {@code browser_download_url} cost no API quota.
 */
final class SecureDownloader {
	static final int MAX_REDIRECTS = 5;
	/** No jar of this mod comes near it; a larger size is refused before any request (REQ-UPD-13). */
	static final long MAX_JAR_BYTES = 32L * 1024 * 1024;
	/** The whole download, every hop and the body included (REQ-UPD-09). */
	static final Duration TIMEOUT = Duration.ofSeconds(120);
	/** HTTPS on the default port to github.com or a subdomain of githubusercontent.com, with no user part. */
	static final Predicate<URI> GITHUB = SecureDownloader::isGitHub;

	/** What a fetch ended with. */
	sealed interface Result permits Done, Failed {
	}

	/** @param sha256 the streamed bytes' SHA-256, lowercase hex */
	record Done(String sha256, long bytes) implements Result {
	}

	/** @param reason one line for the log */
	record Failed(String reason) implements Result {
	}

	private final HttpClient client;
	private final String userAgent;
	private final Predicate<URI> allowed;
	private final Duration timeout;

	/** @param allowed the hosts a hop may go to: {@link #GITHUB} in the game, the mock server in tests */
	SecureDownloader(HttpClient client, String userAgent, Predicate<URI> allowed, Duration timeout) {
		this.client = client;
		this.userAgent = userAgent;
		this.allowed = allowed;
		this.timeout = timeout;
	}

	private static boolean isGitHub(URI uri) {
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getHost() == null) {
			return false;
		}
		if (uri.getPort() != -1 && uri.getPort() != 443) {
			return false;
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		return host.equals("github.com") || host.endsWith(".githubusercontent.com") && host.length() > ".githubusercontent.com".length();
	}

	/**
	 * Streams {@code url} into {@code sink}. Never throws: every failure is a {@link Failed} whose reason names the rule.
	 *
	 * @param size the asset's size from the API; the body must be exactly this long
	 * @param cap  the largest size accepted for this kind of file
	 */
	Result fetch(String url, long size, long cap, OutputStream sink) {
		if (size <= 0) {
			return new Failed("the asset has no size");
		}
		if (size > cap) {
			return new Failed("the asset's size " + size + " is over the " + cap + "-byte cap");
		}
		URI target;
		try {
			target = new URI(url);
		} catch (java.net.URISyntaxException | NullPointerException e) {
			return new Failed("the download URL is not valid");
		}
		long deadline = System.nanoTime() + timeout.toNanos();
		for (int hop = 0; ; hop++) {
			if (!target.isAbsolute() || !allowed.test(target)) {
				return new Failed((hop == 0 ? "a download from " : "a redirect to ") + origin(target) + " is not allowed");
			}
			Duration left = Duration.ofNanos(deadline - System.nanoTime());
			if (left.isNegative() || left.isZero()) {
				return new Failed("timed out");
			}
			HttpResponse<InputStream> response;
			try {
				response = client.send(HttpRequest.newBuilder(target)
						.timeout(left)
						.header("User-Agent", userAgent)
						.header("Accept", "application/octet-stream")
						.GET()
						.build(), HttpResponse.BodyHandlers.ofInputStream());
			} catch (HttpTimeoutException e) {
				return new Failed("timed out");
			} catch (IOException e) {
				return new Failed(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return new Failed("interrupted");
			} catch (IllegalArgumentException e) {
				return new Failed("the download URL is not valid");
			}
			int status = response.statusCode();
			if (isRedirect(status)) {
				close(response);
				if (hop == MAX_REDIRECTS) {
					return new Failed("more than " + MAX_REDIRECTS + " redirects");
				}
				String location = response.headers().firstValue("location").orElse(null);
				if (location == null) {
					return new Failed("HTTP " + status + " without a Location");
				}
				try {
					target = target.resolve(location);
				} catch (IllegalArgumentException e) {
					return new Failed("HTTP " + status + " to a Location that is not valid");
				}
				continue;
			}
			if (status != 200) {
				close(response);
				return new Failed("HTTP " + status);
			}
			OptionalLong length;
			try {
				length = response.headers().firstValueAsLong("content-length");
			} catch (NumberFormatException e) {
				close(response);
				return new Failed("Content-Length is not a number");
			}
			if (length.isPresent() && length.getAsLong() != size) {
				close(response);
				return new Failed("Content-Length " + length.getAsLong() + " is not the asset's size " + size);
			}
			return read(response.body(), size, deadline, sink);
		}
	}

	private static Result read(InputStream input, long size, long deadline, OutputStream sink) {
		MessageDigest sha256;
		try {
			sha256 = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			close(input);
			return new Failed("no SHA-256 in this Java");
		}
		// The request timeout ends when the headers arrive; a body that stalls is cut off at the deadline by closing it.
		AtomicBoolean timedOut = new AtomicBoolean();
		Thread watchdog = Thread.ofVirtual().name("k8bas-update-download-timeout").start(() -> {
			try {
				Thread.sleep(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
				timedOut.set(true);
				input.close();
			} catch (InterruptedException | IOException finished) {
				// read in time, or already closed
			}
		});
		long count = 0;
		byte[] buffer = new byte[16 * 1024];
		try (input) {
			int read;
			while ((read = input.read(buffer)) != -1) {
				count += read;
				if (count > size) {
					return new Failed("the body is longer than the asset's " + size + " bytes");
				}
				sha256.update(buffer, 0, read);
				sink.write(buffer, 0, read);
			}
		} catch (IOException e) {
			return new Failed(timedOut.get() ? "timed out" : "the body broke off: " + e.getClass().getSimpleName());
		} finally {
			watchdog.interrupt();
		}
		if (timedOut.get()) {
			return new Failed("timed out");
		}
		if (count != size) {
			return new Failed("the body has " + count + " bytes, the asset " + size);
		}
		return new Done(HexFormat.of().formatHex(sha256.digest()), count);
	}

	private static boolean isRedirect(int status) {
		return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
	}

	/** Scheme and host only: a signed CDN URL's query never reaches the log. */
	private static String origin(URI uri) {
		if (uri.getScheme() == null || uri.getHost() == null) {
			return "a relative or hostless URL";
		}
		return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT);
	}

	private static void close(HttpResponse<InputStream> response) {
		close(response.body());
	}

	private static void close(InputStream input) {
		try {
			input.close();
		} catch (IOException ignored) {
			// nothing to release
		}
	}
}
