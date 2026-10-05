package com.k8bas.skyblockutility.update

import java.util.Optional
import java.util.regex.Pattern

/**
 * Picks a release's jar for the running Minecraft version (REQ-UPD-06, the release contract in
 * REQ-REL-07). Only the asset name is used, never a name derived from the download URL.
 */
object AssetSelector {
	@JvmField
	val JAR: Pattern = Pattern.compile(
		"^k8bas_skyblock_utility-(?<ver>(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
			"(?:-[0-9A-Za-z.-]+)?)\\+(?<mc>\\d+\\.\\d+(?:\\.\\d+)?)\\.jar$",
	)

	/** The uploaded jar whose version is the tag's and whose MC part is the running version (best)
	 *  or a dotted prefix of it ("+26.2" serves 26.2.1); null when there is none. */
	fun selectOrNull(tagVersion: SemVer, assets: List<Release.Asset?>, runningMc: String): Release.Asset? {
		var prefixMatch: Release.Asset? = null
		for (asset in assets) {
			if (asset?.name() == null || asset.state() != "uploaded") {
				continue
			}
			val matcher = JAR.matcher(asset.name())
			if (!matcher.matches() || SemVer.parseOrNull(matcher.group("ver")) != tagVersion) {
				continue
			}
			val mc = matcher.group("mc")
			if (mc == runningMc) {
				return asset
			}
			if (prefixMatch == null && runningMc.startsWith("$mc.")) {
				prefixMatch = asset
			}
		}
		return prefixMatch
	}

	@JvmStatic
	fun select(tagVersion: SemVer, assets: List<Release.Asset?>, runningMc: String): Optional<Release.Asset> =
		Optional.ofNullable(selectOrNull(tagVersion, assets, runningMc))
}
