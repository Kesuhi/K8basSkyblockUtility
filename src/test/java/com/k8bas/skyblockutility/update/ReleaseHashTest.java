package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.net.SharedHttpClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * T4.2 (REQ-UPD-14, AC-UPD-15): where the SHA-256 a download is checked against comes from. GitHub's digest first; the
 * sidecar `<name>.sha256` when the digest is missing or not SHA-256; both present and different is a conflict.
 */
class ReleaseHashTest {
	private static final String JAR = "k8bas_skyblock_utility-1.2.0+26.2.jar";
	private static final String HEX = "ab".repeat(32);
	private static final String OTHER = "cd".repeat(32);
	private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

	private final List<String> warnings = new CopyOnWriteArrayList<>();

	private ReleaseHash hash(MockGitHub github) {
		URI base = github.at("/");
		return new ReleaseHash(new SecureDownloader(SharedHttpClient.get(), "test",
				uri -> "127.0.0.1".equals(uri.getHost()) && uri.getPort() == base.getPort(), Duration.ofSeconds(5)), warnings::add);
	}

	private static Release release(MockGitHub github, String digest, String sidecarState, long sidecarSize) {
		List<Release.Asset> assets = new ArrayList<>();
		assets.add(new Release.Asset(JAR, "uploaded", github.at("/download/" + JAR).toString(), digest, 1000));
		if (sidecarState != null) {
			assets.add(new Release.Asset(JAR + ".sha256", sidecarState, github.at("/download/" + JAR + ".sha256").toString(), null, sidecarSize));
		}
		return new Release("v1.2.0", "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v1.2.0", false, false, assets);
	}

	private static byte[] sidecar(String hex) {
		return (hex + "  " + JAR + "\n").getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] withBom(byte[] bytes) {
		byte[] out = new byte[BOM.length + bytes.length];
		System.arraycopy(BOM, 0, out, 0, BOM.length);
		System.arraycopy(bytes, 0, out, BOM.length, bytes.length);
		return out;
	}

	/** REQ-UPD-14: at most 1 KiB, a leading BOM tolerated, the first token 64 hex characters. */
	@Test
	void theSidecarRules() {
		assertEquals(HEX, ReleaseHash.parseSidecar(sidecar(HEX)));
		assertEquals(HEX, ReleaseHash.parseSidecar(withBom(sidecar(HEX))));
		assertEquals(HEX, ReleaseHash.parseSidecar(HEX.getBytes(StandardCharsets.UTF_8)), "the hash alone");
		assertEquals(HEX, ReleaseHash.parseSidecar(sidecar(HEX.toUpperCase())), "lowercased");
		assertEquals(HEX, ReleaseHash.parseSidecar((" \t" + HEX + "\r\n").getBytes(StandardCharsets.UTF_8)));
		assertNull(ReleaseHash.parseSidecar(sidecar(HEX.substring(1))), "63 hex characters");
		assertNull(ReleaseHash.parseSidecar(sidecar(HEX + "0")), "65 hex characters");
		assertNull(ReleaseHash.parseSidecar(sidecar("zz".repeat(32))));
		assertNull(ReleaseHash.parseSidecar(("sha256:" + HEX).getBytes(StandardCharsets.UTF_8)));
		assertNull(ReleaseHash.parseSidecar(new byte[0]));
		assertNull(ReleaseHash.parseSidecar(withBom(withBom(sidecar(HEX)))), "one BOM only");
		byte[] long1 = new byte[ReleaseHash.MAX_SIDECAR_BYTES + 1];
		System.arraycopy(sidecar(HEX), 0, long1, 0, sidecar(HEX).length);
		java.util.Arrays.fill(long1, sidecar(HEX).length, long1.length, (byte) ' ');
		assertNull(ReleaseHash.parseSidecar(long1), "over 1 KiB");
		assertEquals(HEX, ReleaseHash.parseSidecar(java.util.Arrays.copyOf(long1, ReleaseHash.MAX_SIDECAR_BYTES)), "exactly 1 KiB");
		assertNull(ReleaseHash.parseSidecar(new byte[] {(byte) 0xC3, (byte) 0x28}), "not UTF-8");
	}

	/** A digest and no sidecar: the digest, with no request. */
	@Test
	void aDigestAloneIsUsedWithoutARequest() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			Release release = release(github, "sha256:" + HEX, null, 0);
			assertEquals(new UpdateHooks.Hash.Verifiable(HEX), hash(github).check(release, release.assets().getFirst()));
			assertEquals(0, github.requests.size());
		}
	}

	/** Our own releases carry both; they agree, and the sidecar is read once per session. */
	@Test
	void aDigestAndAnAgreeingSidecarAreVerifiable() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(200, sidecar(HEX)));
			ReleaseHash hash = hash(github);
			Release release = release(github, "sha256:" + HEX, "uploaded", sidecar(HEX).length);
			assertEquals(new UpdateHooks.Hash.Verifiable(HEX), hash.check(release, release.assets().getFirst()));
			assertEquals(new UpdateHooks.Hash.Verifiable(HEX), hash.check(release, release.assets().getFirst()));
			assertEquals(1, github.requests.size(), "read once");
			assertEquals("/download/" + JAR + ".sha256", github.requests.getFirst().pathAndQuery());
		}
	}

	/** AC-UPD-15: digest and sidecar disagree, so no install; one WARN line. */
	@Test
	void aDigestAndADifferentSidecarConflict() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(200, sidecar(OTHER)));
			ReleaseHash hash = hash(github);
			Release release = release(github, "sha256:" + HEX, "uploaded", sidecar(OTHER).length);
			assertEquals(UpdateHooks.Hash.Conflict.INSTANCE, hash.check(release, release.assets().getFirst()));
			assertEquals(UpdateHooks.Hash.Conflict.INSTANCE, hash.check(release, release.assets().getFirst()));
			assertEquals(List.of("Release checksum conflict for " + JAR + ": the digest says " + HEX + ", its .sha256 file " + OTHER), warnings);
		}
	}

	/** AC-UPD-15: no digest but a sidecar, with or without a BOM; and a sha512 digest falls back to the sidecar. */
	@Test
	void theSidecarStandsInForAMissingOrOtherDigest() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			github.enqueue(MockGitHub.Answer.bytes(200, withBom(sidecar(HEX))), MockGitHub.Answer.bytes(200, sidecar(HEX)));
			Release noDigest = release(github, null, "uploaded", withBom(sidecar(HEX)).length);
			assertEquals(new UpdateHooks.Hash.Verifiable(HEX), hash(github).check(noDigest, noDigest.assets().getFirst()));
			Release sha512 = release(github, "sha512:" + "ef".repeat(64), "uploaded", sidecar(HEX).length);
			assertEquals(new UpdateHooks.Hash.Verifiable(HEX), hash(github).check(sha512, sha512.assets().getFirst()));
			assertEquals(2, github.requests.size());
		}
	}

	/** AC-UPD-15: no digest and no usable sidecar is notify-only; a sidecar that is still uploading or too large is not read. */
	@Test
	void neitherIsMissing() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			Release none = release(github, null, null, 0);
			assertEquals(UpdateHooks.Hash.Missing.INSTANCE, hash(github).check(none, none.assets().getFirst()));
			Release open = release(github, null, "open", sidecar(HEX).length);
			assertEquals(UpdateHooks.Hash.Missing.INSTANCE, hash(github).check(open, open.assets().getFirst()));
			Release huge = release(github, null, "uploaded", ReleaseHash.MAX_SIDECAR_BYTES + 1);
			assertEquals(UpdateHooks.Hash.Missing.INSTANCE, hash(github).check(huge, huge.assets().getFirst()));
			// Over 1 KiB is not a valid sidecar, so the digest alone serves; it is not "unreadable for now".
			Release hugeWithDigest = release(github, "sha256:" + HEX, "uploaded", ReleaseHash.MAX_SIDECAR_BYTES + 1);
			assertEquals(new UpdateHooks.Hash.Verifiable(HEX), hash(github).check(hugeWithDigest, hugeWithDigest.assets().getFirst()));
			assertEquals(0, github.requests.size());
			github.enqueue(MockGitHub.Answer.bytes(200, "not a hash".getBytes(StandardCharsets.UTF_8)));
			Release bad = release(github, null, "uploaded", "not a hash".length());
			assertEquals(UpdateHooks.Hash.Missing.INSTANCE, hash(github).check(bad, bad.assets().getFirst()));
		}
	}

	/**
	 * Review (T4.2): a sidecar that exists but cannot be read now could disagree with the digest, so no install is offered
	 * on the digest alone; it is asked again at the next check.
	 */
	@Test
	void anUnreachableSidecarHoldsTheInstallBackUntilItIsRead() throws Exception {
		try (MockGitHub github = new MockGitHub()) {
			ReleaseHash hash = hash(github);
			Release noDigest = release(github, null, "uploaded", sidecar(HEX).length);
			github.enqueue(MockGitHub.Answer.bytes(500, new byte[0]));
			assertEquals(UpdateHooks.Hash.Unavailable.INSTANCE, hash.check(noDigest, noDigest.assets().getFirst()));
			github.enqueue(MockGitHub.Answer.bytes(200, sidecar(HEX)));
			assertEquals(new UpdateHooks.Hash.Verifiable(HEX), hash.check(noDigest, noDigest.assets().getFirst()));
			Release withDigest = release(github, "sha256:" + HEX, "uploaded", sidecar(HEX).length);
			github.enqueue(MockGitHub.Answer.bytes(500, new byte[0]));
			assertEquals(UpdateHooks.Hash.Unavailable.INSTANCE, hash.check(withDigest, withDigest.assets().getFirst()));
			github.enqueue(MockGitHub.Answer.bytes(200, sidecar(OTHER)));
			assertEquals(UpdateHooks.Hash.Conflict.INSTANCE, hash.check(withDigest, withDigest.assets().getFirst()), "read at the next check");
			assertEquals(4, github.requests.size());
		}
	}
}
