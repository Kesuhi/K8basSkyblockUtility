package com.k8bas.skyblockutility.update;

/**
 * What the General › Updates card shows and does (REQ-UPD-10), so the settings declarations load no Minecraft class. In
 * the game {@link UpdatesCard} over the running service; {@link #NONE} where there is none (tests, the catalog's build).
 * The texts are read every frame, so each answer comes from what is already known, never from the network.
 */
public interface UpdatesPanel {
	/** The running version, e.g. "v1.3.1". */
	String version();

	/** The latest version known, marked "(skipped)" or "(beta)", or why none is. */
	String latest();

	/** When the last check got an answer, in local time, or "Never". */
	String lastCheck();

	/** The pending install, the card's last answer, or the last check's text. */
	String status();

	/** The channel an untouched setting stands for (REQ-UPD-07). */
	UpdateChannel defaultChannel();

	/** *Check now*: a manual check (REQ-UPD-04's 60 s cooldown). */
	void checkNow();

	/** *Install*: the confirmation screen, whose click is the consent (REQ-UPD-11). */
	void install();

	/** *Open release page*: the checked page, through the game's link confirmation. */
	void openReleasePage();

	/** *Cancel pending update* (REQ-UPD-12). */
	void cancelPending();

	/** A channel was picked: what is on offer is read again, off the render thread (REQ-UPD-07). */
	void channelChanged();

	/** No updater: empty texts, buttons that do nothing. */
	UpdatesPanel NONE = new UpdatesPanel() {
		@Override
		public String version() {
			return "";
		}

		@Override
		public String latest() {
			return "";
		}

		@Override
		public String lastCheck() {
			return "";
		}

		@Override
		public String status() {
			return "";
		}

		@Override
		public UpdateChannel defaultChannel() {
			return UpdateChannel.STABLE;
		}

		@Override
		public void checkNow() {
		}

		@Override
		public void install() {
		}

		@Override
		public void openReleasePage() {
		}

		@Override
		public void cancelPending() {
		}

		@Override
		public void channelChanged() {
		}
	};
}
