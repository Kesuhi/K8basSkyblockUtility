package com.k8bas.skyblockutility.update;

import java.net.URI;

/**
 * What a test build of the mod swaps in for the Windows integration run (T4.5, REQ-UPD-03: "a source override for tests
 * may exist only in the development environment or in test builds"). Only a jar built with
 * {@code -Pk8bas.updaterTestBuild=true} carries an implementation (registered as a service, found with
 * {@link java.util.ServiceLoader}); the release jar carries none, so it always reads GitHub and downloads only from GitHub
 * (ReleaseArtifactTest checks it).
 */
public interface UpdateTestHook {
	/** The release list to read instead of GitHub's. */
	URI releases();

	/** Whether a download hop may go to {@code uri} besides GitHub (the stand-in's own address). */
	boolean allowsDownload(URI uri);

	/** Called once the launch's reconciliation has run; the test build may act on the service here (its scripted click). */
	void afterLaunch(UpdateService service);
}
