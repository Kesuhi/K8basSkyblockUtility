package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.net.SharedHttpClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC-UPD-03, AC-UPD-09 (source part), AC-UPD-21 [A], EC-UPD-12 (T1.4a). */
class GitHubReleaseSourceTest {
	static final String LIST = """
			[
			  {"tag_name": "v1.2.0", "html_url": "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0",
			   "draft": false, "prerelease": false,
			   "assets": [{"name": "k8bas_skyblock_utility-1.2.0+26.2.jar", "state": "uploaded",
			               "browser_download_url": "https://github.com/x/k8bas_skyblock_utility-1.2.0%2B26.2.jar",
			               "digest": "sha256:abc", "size": 1234}]}
			]
			""";

	private static GitHubReleaseSource source(URI uri) {
		return new GitHubReleaseSource(uri, SharedHttpClient.get(), "1.1.0+26.2", Duration.ofSeconds(5));
	}

	@Test
	void oneGetWithTheRequiredHeadersAndNothingElse() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, LIST, "ETag", "\"abc\""));
			GitHubReleaseSource.Result result = source(github.releases()).fetch(null);

			assertEquals(GitHubReleaseSource.Kind.OK, result.kind());
			assertEquals(1, github.requests.size());
			MockGitHub.Request request = github.requests.getFirst();
			assertEquals("GET", request.method());
			assertEquals("/repos/Kesuhi/K8basSkyblockUtility/releases?per_page=30", request.pathAndQuery());
			assertEquals("k8bas_skyblock_utility/1.1.0+26.2 (+https://github.com/Kesuhi/K8basSkyblockUtility)", request.header("User-Agent"));
			assertEquals("application/vnd.github+json", request.header("Accept"));
			assertEquals(GitHubReleaseSource.API_VERSION, request.header("X-GitHub-Api-Version"));
			assertNull(request.header("Authorization"));
			assertNull(request.header("If-None-Match"));
			// AC-UPD-21: only these headers (plus the HTTP client's own) leave the machine: no player name or UUID.
			Set<String> sent = request.headers().keySet().stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
			Set<String> allowed = Set.of("user-agent", "accept", "x-github-api-version", "if-none-match",
					"host", "connection", "content-length", "upgrade", "http2-settings");
			assertTrue(allowed.containsAll(sent), "sent " + sent);
			assertEquals("\"abc\"", result.etag());
			assertEquals("v1.2.0", result.releases().getFirst().tag());
			assertEquals("k8bas_skyblock_utility-1.2.0+26.2.jar", result.releases().getFirst().assets().getFirst().name());
		}
	}

	@Test
	void theEtagIsSentAndA304KeepsTheCache() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(304, ""));
			GitHubReleaseSource.Result result = source(github.releases()).fetch("\"abc\"");
			assertEquals(GitHubReleaseSource.Kind.NOT_MODIFIED, result.kind());
			assertEquals("\"abc\"", github.requests.getFirst().header("If-None-Match"));
		}
	}

	@Test
	void rateLimitHeadersAreReported() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(403, "{}", "Retry-After", "120", "X-RateLimit-Remaining", "0", "X-RateLimit-Reset", "1700000000"));
			GitHubReleaseSource.Result result = source(github.releases()).fetch(null);
			assertEquals(GitHubReleaseSource.Kind.HTTP_ERROR, result.kind());
			assertEquals(403, result.status());
			assertEquals(Map.of("retry-after", "120", "x-ratelimit-remaining", "0", "x-ratelimit-reset", "1700000000"), result.headers());
		}
	}

	/** EC-UPD-12. */
	@Test
	void aRedirectIsFollowedOnlyToTheSameHost() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(301, "", "Location", "/repos/Kesuhi/Renamed/releases?per_page=30"),
					MockGitHub.Answer.json(200, LIST));
			assertEquals(GitHubReleaseSource.Kind.OK, source(github.releases()).fetch(null).kind());
			assertEquals("/repos/Kesuhi/Renamed/releases?per_page=30", github.requests.get(1).pathAndQuery());

			github.enqueue(MockGitHub.Answer.json(301, "", "Location", "https://example.com/releases"));
			GitHubReleaseSource.Result elsewhere = source(github.releases()).fetch(null);
			assertEquals(GitHubReleaseSource.Kind.BAD_REDIRECT, elsewhere.kind());
			assertEquals(3, github.requests.size());
		}
	}

	@Test
	void malformedBodiesAreErrorsAndMalformedEntriesAreSkipped() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, "{\"message\": \"not a list\"}"),
					MockGitHub.Answer.json(200, "[{\"tag_name\": "),
					MockGitHub.Answer.json(200, "x".repeat(GitHubReleaseSource.MAX_BODY_BYTES + 1)),
					MockGitHub.Answer.json(200, """
							[null, 7, {"tag_name": 5, "html_url": "u"}, {"html_url": "u"},
							 {"tag_name": "v1.0.0", "html_url": "u", "assets": [null, {"state": "uploaded"}, {"name": "a.jar", "size": "big"}, {"name": "b.jar"}]}]
							"""));
			GitHubReleaseSource source = source(github.releases());
			assertEquals(GitHubReleaseSource.Kind.MALFORMED, source.fetch(null).kind());
			assertEquals(GitHubReleaseSource.Kind.MALFORMED, source.fetch(null).kind());
			assertEquals(GitHubReleaseSource.Kind.MALFORMED, source.fetch(null).kind());
			GitHubReleaseSource.Result skipped = source.fetch(null);
			assertEquals(GitHubReleaseSource.Kind.OK, skipped.kind());
			assertEquals(List.of("v1.0.0"), skipped.releases().stream().map(Release::tag).toList());
			assertEquals(List.of("b.jar"), skipped.releases().getFirst().assets().stream().map(Release.Asset::name).toList());
		}
	}

	@Test
	void noAnswerIsReported() throws Exception {
		URI closed;
		try (MockGitHub github = new MockGitHub()) {
			closed = github.releases();
		}
		GitHubReleaseSource.Result refused = source(closed).fetch(null);
		assertEquals(GitHubReleaseSource.Kind.NO_RESPONSE, refused.kind());
		assertFalse(refused.problem().isBlank());

		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, LIST).delayed(3000));
			GitHubReleaseSource slow = new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), "1.1.0", Duration.ofSeconds(1));
			GitHubReleaseSource.Result timedOut = slow.fetch(null);
			assertEquals(GitHubReleaseSource.Kind.NO_RESPONSE, timedOut.kind());
			assertEquals("timed out", timedOut.problem());
		}
	}

	/** Review S-2 (G1): a body that stops halfway ends the request within its timeout. */
	@Test
	void aStalledBodyTimesOut() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(200, LIST).stallingMidBody(5000));
			GitHubReleaseSource source = new GitHubReleaseSource(github.releases(), SharedHttpClient.get(), "1.1.0", Duration.ofSeconds(1));
			long start = System.nanoTime();
			GitHubReleaseSource.Result result = source.fetch(null);
			double seconds = (System.nanoTime() - start) / 1e9;
			assertEquals(GitHubReleaseSource.Kind.NO_RESPONSE, result.kind(), String.valueOf(result.problem()));
			assertTrue(seconds < 2.5, "ended after " + seconds + " s");
		}
	}

	/** Review S-2 (G1): a malformed redirect target is an error, not an exception. */
	@Test
	void aMalformedRedirectIsAnError() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.json(301, "", "Location", "http://[not a host/"));
			assertEquals(GitHubReleaseSource.Kind.BAD_REDIRECT, source(github.releases()).fetch(null).kind());
		}
	}

	/** AC-UPD-03 [R] part, tested: the source override is ignored outside the development environment. */
	@Test
	void theOverrideWorksOnlyInDevelopment() {
		assertEquals(GitHubReleaseSource.RELEASES, GitHubReleaseSource.resolve("http://127.0.0.1:1/x", false));
		assertEquals(URI.create("http://127.0.0.1:1/x"), GitHubReleaseSource.resolve("http://127.0.0.1:1/x", true));
		assertEquals(GitHubReleaseSource.RELEASES, GitHubReleaseSource.resolve(null, true));
		assertEquals("api.github.com", GitHubReleaseSource.RELEASES.getHost());
		assertEquals("https", GitHubReleaseSource.RELEASES.getScheme());
		assertEquals(20, GitHubReleaseSource.REQUEST_TIMEOUT.toSeconds());
		assertTrue(SharedHttpClient.get().connectTimeout().isPresent());
	}
}
