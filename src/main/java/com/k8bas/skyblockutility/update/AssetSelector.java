package com.k8bas.skyblockutility.update;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks a release's jar for the running Minecraft version (REQ-UPD-06, the release contract in
 * REQ-REL-07). Only the asset name is used, never a name derived from the download URL.
 */
public final class AssetSelector {
	static final Pattern JAR = Pattern.compile("^k8bas_skyblock_utility-(?<ver>(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
			+ "(?:-[0-9A-Za-z.-]+)?)\\+(?<mc>\\d+\\.\\d+(?:\\.\\d+)?)\\.jar$");

	private AssetSelector() {
	}

	/** The uploaded jar whose version is the tag's and whose MC part is the running version (best)
	 *  or a dotted prefix of it ("+26.2" serves 26.2.1). */
	public static Optional<Release.Asset> select(SemVer tagVersion, List<Release.Asset> assets, String runningMc) {
		Release.Asset prefixMatch = null;
		for (Release.Asset asset : assets) {
			if (asset == null || asset.name() == null || !"uploaded".equals(asset.state())) {
				continue;
			}
			Matcher matcher = JAR.matcher(asset.name());
			if (!matcher.matches() || !SemVer.parse(matcher.group("ver")).equals(Optional.of(tagVersion))) {
				continue;
			}
			String mc = matcher.group("mc");
			if (mc.equals(runningMc)) {
				return Optional.of(asset);
			}
			if (prefixMatch == null && runningMc.startsWith(mc + ".")) {
				prefixMatch = asset;
			}
		}
		return Optional.ofNullable(prefixMatch);
	}
}
