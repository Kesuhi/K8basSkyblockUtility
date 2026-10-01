package com.k8bas.skyblockutility.config;

public class GeneralConfig implements Normalizable {
	public boolean autoUpdateCheckEnabled = true;
	public boolean autoUpdateDownloadEnabled = true;
	/** Blanket cap, in blocks, on how far away mobs are considered for highlighting. 0 = unlimited. */
	public int mobScanRangeBlocks = 64;

	/** A negative scan range means unlimited (0), as the scan treats it. */
	@Override
	public boolean normalize() {
		if (mobScanRangeBlocks < 0) {
			mobScanRangeBlocks = 0;
			return true;
		}
		return false;
	}
}
