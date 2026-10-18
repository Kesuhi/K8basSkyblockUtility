package com.k8bas.skyblockutility.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.ZoneId;
import java.util.List;

/**
 * The Windows integration run's stand-in (T4.5, AC-UPD-18): only in jars built with {@code -Pk8bas.updaterTestBuild=true},
 * never in a release (ReleaseArtifactTest). It points the updater at a local stand-in for GitHub
 * ({@code -Dk8bas.updateTest.releases=http://127.0.0.1:<port>/...}), lets downloads reach that address only, and plays
 * the player's part once after the launch's reconciliation ({@code -Dk8bas.updateTest.autopilot}):
 * {@code confirm} checks and then gives the consent the confirmation screen would give for what it showed;
 * {@code check} only checks; anything else does nothing. Each step is one "UpdateTest:" log line.
 */
public final class StandInHook implements UpdateTestHook {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/update-test");
	private final URI releases = URI.create(System.getProperty("k8bas.updateTest.releases", "http://127.0.0.1:1/releases"));

	@Override
	public URI releases() {
		return releases;
	}

	@Override
	public boolean allowsDownload(URI uri) {
		return "http".equals(uri.getScheme()) && uri.getHost() != null && uri.getHost().equals(releases.getHost())
				&& releases.getPort() == uri.getPort() && uri.getRawUserInfo() == null;
	}

	@Override
	public void afterLaunch(UpdateService service) {
		String autopilot = System.getProperty("k8bas.updateTest.autopilot", "none");
		LOGGER.info("UpdateTest: autopilot {}, running {}, pending {}", autopilot, service.runningVersion(), service.pendingInstall());
		if (!autopilot.equals("confirm") && !autopilot.equals("check")) {
			return;
		}
		// The same actions `/ksu update` and the confirmation screen run, so the log shows the player's own text.
		UpdateActions actions = new UpdateActions(service, ZoneId.systemDefault());
		long start = System.nanoTime();
		List<String> checked = actions.check();
		LOGGER.info("UpdateTest: manual check after {} ms: {} {}", (System.nanoTime() - start) / 1_000_000, checked, service.lastOffer());
		if (!autopilot.equals("confirm") || !(service.lastOffer() instanceof CheckOutcome.Found found)) {
			return;
		}
		Consent consent = service.confirmation(found).consent();
		if (consent == null) {
			LOGGER.info("UpdateTest: the screen offers no install: {}", UpdateHooks.notOfferedReason(found.hash(), found.eligibility()));
			return;
		}
		LOGGER.info("UpdateTest: confirming {}", consent);
		LOGGER.info("UpdateTest: download: {}", actions.install(consent));
	}
}
