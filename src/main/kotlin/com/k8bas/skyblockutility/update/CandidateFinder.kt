package com.k8bas.skyblockutility.update

import java.util.function.Consumer

/**
 * Which release, if any, to offer (REQ-UPD-05 to REQ-UPD-08): the highest version in the active
 * channel that is newer than the running one and has a jar for the running Minecraft version.
 * Releases may come in any order (EC-UPD-09); drafts are ignored.
 */
object CandidateFinder {
	sealed interface Outcome

	/** A newer release with a jar. [skippedWithoutJar] are newer releases without one. */
	@JvmRecord
	data class Candidate(val release: Release, val version: SemVer, val jar: Release.Asset, val skippedWithoutJar: List<SemVer>) : Outcome {
		override fun toString(): String = "Candidate[release=$release, version=$version, jar=$jar, skippedWithoutJar=$skippedWithoutJar]"
	}

	/** A newer release exists, but no newer release has a jar for this Minecraft version. */
	@JvmRecord
	data class NoJar(val newest: SemVer) : Outcome {
		override fun toString(): String = "NoJar[newest=$newest]"
	}

	data object UpToDate : Outcome {
		override fun toString(): String = "UpToDate[]"
	}

	/** The running version is not SemVer, so nothing is offered; the newest version is information. */
	@JvmRecord
	data class RunningNotSemVer(val newest: SemVer?) : Outcome {
		override fun toString(): String = "RunningNotSemVer[newest=$newest]"
	}

	/**
	 * @param log receives one line per skipped release (an unparsable tag, a newer release without a
	 *            jar for this MC); the caller keeps them to once per tag
	 */
	@JvmStatic
	fun find(releases: List<Release?>, runningVersion: String?, runningMc: String, stored: UpdateChannel?, log: Consumer<String>): Outcome {
		val running = SemVer.parseOrNull(runningVersion)
		val channel = UpdateChannel.effective(stored, running)

		val considered = releases.asSequence()
			.filterNotNull()
			.filterNot { it.draft() }
			.mapNotNull { release ->
				val version = SemVer.fromTagOrNull(release.tag())
				if (version == null) {
					log.accept("Release tag '" + release.tag() + "' is not a SemVer version; skipped")
					null
				} else if (channel == UpdateChannel.STABLE && UpdateChannel.isPreRelease(release, version)) {
					null
				} else {
					release to version
				}
			}
			.sortedByDescending { it.second }
			.toList()

		if (running == null) {
			return RunningNotSemVer(considered.firstOrNull()?.second)
		}
		val withoutJar = ArrayList<SemVer>()
		for ((release, version) in considered) {
			if (version <= running) {
				break
			}
			val jar = AssetSelector.selectOrNull(version, release.assets(), runningMc)
			if (jar != null) {
				return Candidate(release, version, jar, java.util.List.copyOf(withoutJar))
			}
			log.accept("v$version has no build for Minecraft $runningMc")
			withoutJar.add(version)
		}
		return if (withoutJar.isEmpty()) UpToDate else NoJar(withoutJar.first())
	}
}
