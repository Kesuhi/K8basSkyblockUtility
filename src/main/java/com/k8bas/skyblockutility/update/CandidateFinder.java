package com.k8bas.skyblockutility.update;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Which release, if any, to offer (REQ-UPD-05 to REQ-UPD-08): the highest version in the active
 * channel that is newer than the running one and has a jar for the running Minecraft version.
 * Releases may come in any order (EC-UPD-09); drafts are ignored.
 */
public final class CandidateFinder {
	private CandidateFinder() {
	}

	public sealed interface Outcome {
	}

	/** A newer release with a jar. {@code skippedWithoutJar} are newer releases without one. */
	public record Candidate(Release release, SemVer version, Release.Asset jar, List<SemVer> skippedWithoutJar) implements Outcome {
	}

	/** A newer release exists, but no newer release has a jar for this Minecraft version. */
	public record NoJar(SemVer newest) implements Outcome {
	}

	public record UpToDate() implements Outcome {
	}

	/** The running version is not SemVer, so nothing is offered; the newest version is information. */
	public record RunningNotSemVer(SemVer newest) implements Outcome {
	}

	/**
	 * @param log receives one line per skipped release (an unparsable tag, a newer release without a
	 *            jar for this MC); the caller keeps them to once per tag
	 */
	public static Outcome find(List<Release> releases, String runningVersion, String runningMc, UpdateChannel stored,
			Consumer<String> log) {
		Optional<SemVer> running = SemVer.parse(runningVersion);
		UpdateChannel channel = UpdateChannel.effective(stored, running.orElse(null));

		record Versioned(Release release, SemVer version) {
		}
		List<Versioned> considered = new ArrayList<>();
		for (Release release : releases) {
			if (release == null || release.draft()) {
				continue;
			}
			Optional<SemVer> version = SemVer.fromTag(release.tag());
			if (version.isEmpty()) {
				log.accept("Release tag '" + release.tag() + "' is not a SemVer version; skipped");
				continue;
			}
			if (channel == UpdateChannel.STABLE && UpdateChannel.isPreRelease(release, version.get())) {
				continue;
			}
			considered.add(new Versioned(release, version.get()));
		}
		considered.sort(Comparator.comparing(Versioned::version).reversed());

		if (running.isEmpty()) {
			return new RunningNotSemVer(considered.isEmpty() ? null : considered.getFirst().version());
		}
		List<SemVer> withoutJar = new ArrayList<>();
		for (Versioned entry : considered) {
			if (entry.version().compareTo(running.get()) <= 0) {
				break;
			}
			Optional<Release.Asset> jar = AssetSelector.select(entry.version(), entry.release().assets(), runningMc);
			if (jar.isPresent()) {
				return new Candidate(entry.release(), entry.version(), jar.get(), List.copyOf(withoutJar));
			}
			log.accept("v" + entry.version() + " has no build for Minecraft " + runningMc);
			withoutJar.add(entry.version());
		}
		return withoutJar.isEmpty() ? new UpToDate() : new NoJar(withoutJar.getFirst());
	}
}
