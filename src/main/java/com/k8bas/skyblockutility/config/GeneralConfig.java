package com.k8bas.skyblockutility.config;

import com.k8bas.skyblockutility.ui.notice.NoticePosition;
import com.k8bas.skyblockutility.update.UpdateChannel;

public class GeneralConfig implements Normalizable {
	/** "Check for updates (notify)": a chat line when a newer version is out; nothing is downloaded. */
	public boolean autoUpdateCheckEnabled = true;
	/** Absent (null, never written) until the user picks a channel; then the default applies (REQ-UPD-07). */
	public UpdateChannel updateChannel;
	/** Blanket cap, in blocks, on how far away mobs are considered for highlighting. 0 = unlimited. */
	public int mobScanRangeBlocks = 64;
	/** The settings UI's accent, 0xRRGGBB. Absent (null, never written) until the player picks one; while absent, the
	 *  default teal applies (REQ-UI-17, AC-UI-16). Any alpha bits a hand edit adds are ignored when read (Theme). */
	public Integer accentColor;
	public static final int DEFAULT_ACCENT = 0x29B6B2;
	/** Where notices (toasts) appear (REQ-UI-21). Absent (null, never written) until picked; an unknown name reads as absent. */
	public NoticePosition noticePosition;
	public static final NoticePosition DEFAULT_NOTICE_POSITION = NoticePosition.TOP_RIGHT;
	/**
	 * How long a notice stays, 1-15 s (REQ-UI-21). Absent (null, never written) until picked. A hand-edited
	 * value outside the range is kept as it is until the slider changes it (REQ-UI-16); notices and the
	 * slider hold it to the range when they use it.
	 */
	public Integer noticeSeconds;
	public static final int DEFAULT_NOTICE_SECONDS = 5;

	public NoticePosition noticePosition() {
		return noticePosition == null ? DEFAULT_NOTICE_POSITION : noticePosition;
	}

	public int noticeSeconds() {
		return noticeSeconds == null ? DEFAULT_NOTICE_SECONDS : noticeSeconds;
	}

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
