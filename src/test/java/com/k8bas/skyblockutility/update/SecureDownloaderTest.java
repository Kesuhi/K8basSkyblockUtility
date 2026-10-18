package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.net.SharedHttpClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.2 (REQ-UPD-13, REQ-UPD-20): the download rules on their own. The host rule is tested on URIs, since the mock
 * servers speak plain HTTP on localhost, and once through {@code fetch} to show the game's rule refuses them before any
 * request; the transfer tests give the downloader a rule that allows only the mock, which shows it is asked on every hop.
 */
class SecureDownloaderTest {
	private static final byte[] BODY = "jar bytes".getBytes(StandardCharsets.UTF_8);
	private static final String USER_AGENT = "k8bas_skyblock_utility/1.1.0+26.2 (+https://github.com/Kesuhi/K8basSkyblockUtility)";

	private static SecureDownloader downloader(int port, Duration timeout) {
		Predicate<URI> onlyTheMock = uri -> "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort() == port;
		return new SecureDownloader(SharedHttpClient.get(), USER_AGENT, onlyTheMock, timeout);
	}

	private static SecureDownloader downloader(MockGitHub github) {
		return downloader(github.at("/").getPort(), Duration.ofSeconds(5));
	}

	private static SecureDownloader.Result fetch(SecureDownloader downloader, URI url, long size) {
		return downloader.fetch(url.toString(), size, SecureDownloader.MAX_JAR_BYTES, new ByteArrayOutputStream());
	}

	/** REQ-UPD-13, REQ-UPD-20: HTTPS on the default port, to github.com or a *.githubusercontent.com host, with no user part. */
	@Test
	void onlyGitHubOverHttpsIsAllowed() throws Exception {
		Predicate<URI> github = SecureDownloader.GITHUB;
		assertTrue(github.test(URI.create("https://github.com/Kesuhi/K8basSkyblockUtility/releases/download/v1.2.0/a.jar")));
		assertTrue(github.test(URI.create("https://release-assets.githubusercontent.com/github-production-release-asset/1?sig=x")));
		assertTrue(github.test(URI.create("https://objects.githubusercontent.com/x")));
		assertTrue(github.test(URI.create("https://GitHub.com/x")), "host names are case-insensitive");
		assertTrue(github.test(URI.create("https://github.com:443/x")));
		assertFalse(github.test(URI.create("http://github.com/x")), "plain http");
		assertFalse(github.test(URI.create("http://release-assets.githubusercontent.com/x")), "plain http");
		assertFalse(github.test(URI.create("https://example.com/x")));
		assertFalse(github.test(URI.create("https://githubusercontent.com.example.com/x")));
		assertFalse(github.test(URI.create("https://github.com.example.com/x")));
		assertFalse(github.test(URI.create("https://github.com./x")), "a trailing dot is another name");
		assertFalse(github.test(URI.create("https://evilgithub.com/x")));
		assertFalse(github.test(URI.create("https://evilgithubusercontent.com/x")));
		assertFalse(github.test(URI.create("https://githubusercontent.com/x")), "only its subdomains");
		assertFalse(github.test(URI.create("https://api.github.com/repos/x")), "the API host serves no downloads");
		assertFalse(github.test(URI.create("https://github.com:8443/x")));
		assertFalse(github.test(new URI("https", "someone", "github.com", -1, "/x", null, null)), "a user part");
		assertFalse(github.test(URI.create("ftp://github.com/x")));
		assertFalse(github.test(URI.create("/relative/x")));
	}

	/** REQ-UPD-20: the game's own rule refuses the local mock before a single request. */
	@Test
	void theGamesRuleRefusesAnythingButGitHub() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			SecureDownloader game = new SecureDownloader(SharedHttpClient.get(), USER_AGENT, SecureDownloader.GITHUB, SecureDownloader.TIMEOUT);
			assertEquals(new SecureDownloader.Failed("a download from http://127.0.0.1 is not allowed"), fetch(game, github.at("/a.jar"), BODY.length));
			assertEquals(0, github.requests.size());
		}
		assertEquals(Duration.ofSeconds(120), SecureDownloader.TIMEOUT, "REQ-UPD-09's download timeout");
	}

	/** REQ-UPD-03, REQ-UPD-20: the download sends its User-Agent and asks for bytes, and nothing else of its own. */
	@Test
	void aDownloadSendsOnlyItsHeaders() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(200, BODY));
			SecureDownloader.Result result = fetch(downloader(github), github.at("/a.jar"), BODY.length);
			SecureDownloader.Done done = assertInstanceOf(SecureDownloader.Done.class, result);
			assertEquals(TestJars.sha256(BODY), done.sha256());
			assertEquals(BODY.length, done.bytes());
			MockGitHub.Request request = github.requests.getFirst();
			assertEquals("GET", request.method());
			assertEquals(USER_AGENT, request.header("User-Agent"));
			assertEquals("application/octet-stream", request.header("Accept"));
			Set<String> names = new TreeSet<>();
			request.headers().keySet().forEach(name -> names.add(name.toLowerCase(Locale.ROOT)));
			// The JDK client adds the transport headers (Host, and the h2c upgrade on plain http).
			names.removeAll(Set.of("host", "connection", "upgrade", "http2-settings", "content-length"));
			assertEquals(Set.of("user-agent", "accept"), names, "no token, cookie or player data");
		}
	}

	/** REQ-UPD-13: five redirects are followed (relative ones too); a sixth aborts before its request. */
	@Test
	void atMostFiveRedirectsAreFollowed() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			for (int i = 1; i <= 5; i++) {
				github.enqueue(MockGitHub.Answer.redirect(i % 2 == 0 ? 302 : 307, i == 3 ? "/hop" + i : github.at("/hop" + i).toString()));
			}
			github.enqueue(MockGitHub.Answer.bytes(200, BODY));
			assertInstanceOf(SecureDownloader.Done.class, fetch(downloader(github), github.at("/a.jar"), BODY.length));
			assertEquals(6, github.requests.size());
			assertEquals("/hop5", github.requests.getLast().pathAndQuery());
		}
		try (MockGitHub github = new MockGitHub()) {
			for (int i = 1; i <= 6; i++) {
				github.enqueue(MockGitHub.Answer.redirect(302, github.at("/hop" + i).toString()));
			}
			assertEquals(new SecureDownloader.Failed("more than 5 redirects"), fetch(downloader(github), github.at("/a.jar"), BODY.length));
			assertEquals(6, github.requests.size(), "the sixth redirect's target is never asked");
		}
	}

	/** REQ-UPD-13: a hop the host rule refuses ends the download before any request goes there. */
	@Test
	void aRedirectTheRuleRefusesEndsTheDownload() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.redirect(302, "http://localhost:" + github.at("/").getPort() + "/elsewhere.jar"));
			assertEquals(new SecureDownloader.Failed("a redirect to http://localhost is not allowed"), fetch(downloader(github), github.at("/a.jar"), BODY.length));
			assertEquals(1, github.requests.size());
		}
		try (MockGitHub github = new MockGitHub()) {
			assertEquals(new SecureDownloader.Failed("a download from https://example.com is not allowed"),
					fetch(downloader(github), URI.create("https://example.com/a.jar"), BODY.length));
			assertEquals(0, github.requests.size());
		}
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(302, new byte[0]));
			assertEquals(new SecureDownloader.Failed("HTTP 302 without a Location"), fetch(downloader(github), github.at("/a.jar"), BODY.length));
		}
	}

	/** REQ-UPD-13: the final status must be 200, and the size must be the asset's, by header and by count. */
	@Test
	void statusAndSizeMustMatch() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			SecureDownloader downloader = downloader(github);
			github.enqueue(MockGitHub.Answer.bytes(404, "{}".getBytes(StandardCharsets.UTF_8)));
			assertEquals(new SecureDownloader.Failed("HTTP 404"), fetch(downloader, github.at("/a.jar"), BODY.length));
			github.enqueue(MockGitHub.Answer.bytes(200, BODY));
			assertEquals(new SecureDownloader.Failed("Content-Length " + BODY.length + " is not the asset's size " + (BODY.length + 1)),
					fetch(downloader, github.at("/a.jar"), BODY.length + 1));
			github.enqueue(MockGitHub.Answer.bytes(200, BODY).chunked());
			assertEquals(new SecureDownloader.Failed("the body is longer than the asset's " + (BODY.length - 1) + " bytes"),
					fetch(downloader, github.at("/a.jar"), BODY.length - 1));
			github.enqueue(MockGitHub.Answer.bytes(200, BODY).chunked());
			assertEquals(new SecureDownloader.Failed("the body has " + BODY.length + " bytes, the asset " + (BODY.length + 1)),
					fetch(downloader, github.at("/a.jar"), BODY.length + 1));
			github.enqueue(MockGitHub.Answer.bytes(200, BODY).chunked());
			assertInstanceOf(SecureDownloader.Done.class, fetch(downloader, github.at("/a.jar"), BODY.length), "a chunked body of exactly the size");
			int before = github.requests.size();
			assertEquals(new SecureDownloader.Failed("the asset's size " + (SecureDownloader.MAX_JAR_BYTES + 1) + " is over the 33554432-byte cap"),
					fetch(downloader, github.at("/a.jar"), SecureDownloader.MAX_JAR_BYTES + 1));
			assertEquals(new SecureDownloader.Failed("the asset has no size"), fetch(downloader, github.at("/a.jar"), 0));
			assertEquals(before, github.requests.size(), "a size over the cap is refused before any request");
			github.enqueue(MockGitHub.Answer.bytes(404, new byte[0]));
			assertEquals(new SecureDownloader.Failed("HTTP 404"), fetch(downloader, github.at("/a.jar"), SecureDownloader.MAX_JAR_BYTES),
					"exactly 32 MiB is still asked for");
			assertEquals(before + 1, github.requests.size());
		}
	}

	/** REQ-UPD-09: a body that stalls is cut off when the download's time is up. */
	@Test
	void aStalledBodyTimesOut() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(200, BODY).stallingMidBody(4_000));
			long start = System.nanoTime();
			SecureDownloader.Result result = fetch(downloader(github.at("/").getPort(), Duration.ofMillis(800)), github.at("/a.jar"), BODY.length);
			long ms = (System.nanoTime() - start) / 1_000_000;
			assertEquals(new SecureDownloader.Failed("timed out"), result);
			assertTrue(ms < 3_000, "cut off at the deadline: " + ms + " ms");
		}
	}

	/** EC-UPD-03: a connection that drops mid-body fails at once, as a broken body, not as a short file or a timeout. */
	@Test
	void aDroppedConnectionFails() throws Exception {
		try (RawServer server = new RawServer()) {
			server.enqueue(RawServer.Answer.droppedMidBody(BODY));
			long start = System.nanoTime();
			SecureDownloader.Result result = fetch(downloader(server.port(), Duration.ofSeconds(5)), server.at("/a.jar"), BODY.length);
			long ms = (System.nanoTime() - start) / 1_000_000;
			SecureDownloader.Failed failed = assertInstanceOf(SecureDownloader.Failed.class, result);
			assertTrue(failed.reason().startsWith("the body broke off"), failed.reason());
			assertTrue(ms < 2_000, "at once, not at the timeout: " + ms + " ms");
		}
	}

	/** A Content-Length that is not a number is a failure, never an exception out of {@code fetch}. */
	@Test
	void aMalformedContentLengthFails() throws Exception {
		try (RawServer server = new RawServer()) {
			server.enqueue(RawServer.Answer.withHead("HTTP/1.1 200 OK\r\nContent-Length: nine\r\nConnection: close\r\n", BODY));
			assertInstanceOf(SecureDownloader.Failed.class, fetch(downloader(server.port(), Duration.ofSeconds(5)), server.at("/a.jar"), BODY.length));
		}
	}
}
