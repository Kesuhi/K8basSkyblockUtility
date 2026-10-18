package com.k8bas.skyblockutility.update;

import java.time.ZoneId;
import java.util.List;

/**
 * What `/ksu update check | install | skip | cancel` and the confirmation screen do, as lines to show (REQ-UPD-10 to
 * REQ-UPD-12). It runs on the caller's thread, which the caller keeps off the render thread; it sends nothing to the
 * server and starts nothing on its own: every action follows a command typed or a click.
 */
public final class UpdateActions {
	private final UpdateService service;
	private final ZoneId zone;

	UpdateActions(UpdateService service, ZoneId zone) {
		this.service = service;
		this.zone = zone;
	}

	/** `/ksu update check`: a manual check (60 s cooldown, REQ-UPD-04). */
	List<String> check() {
		return service.checkManual().lines(zone);
	}

	/**
	 * `/ksu update install <version>`: why it is refused (another version is on offer, or none), or null when it names the
	 * candidate, which then opens the confirmation screen. The command itself downloads nothing: a chat click a server sent
	 * could run it, so the screen's click stays the only consent (REQ-UPD-11).
	 */
	List<String> refusalToInstall(String version) {
		String requested = version.startsWith("v") ? version.substring(1) : version;
		SemVer wanted = SemVer.Companion.fromTagOrNull(version);
		CheckOutcome.Found found = showable();
		if (found == null || wanted == null || wanted.compareTo(found.candidate().version()) != 0) {
			return new DownloadOutcome.Stale(requested, found == null ? null : found.candidate().version()).lines(zone);
		}
		return null;
	}

	/** The confirmation screen's click, with the checksum it showed. */
	List<String> install(Consent consent) {
		return service.download(consent).lines(zone);
	}

	/**
	 * `/ksu update skip <version>`: the first step only asks (REQ-UPD-12's second click); {@code confirmed} stores it.
	 */
	List<String> skip(String version, boolean confirmed) {
		SemVer parsed = SemVer.Companion.fromTagOrNull(version);
		if (parsed == null) {
			return List.of(version + " is not a version");
		}
		// A skip link from an earlier offer must not change what is skipped now (EC-UPD-11).
		CheckOutcome.Found found = showable();
		if (found == null || parsed.compareTo(found.candidate().version()) != 0) {
			return List.of("v" + parsed + " is not the update on offer; " + (found == null ? "there is none" : "that is v" + found.candidate().version()));
		}
		if (!confirmed) {
			return List.of("Skip v" + parsed + "? Its notices stay silent until a newer version is out");
		}
		service.skip(version);
		return List.of("Skipped v" + parsed + "; a newer version notifies again");
	}

	/** Reads the cached release list again (a channel was picked): nothing to say, the card shows the new offer. */
	List<String> refresh() {
		service.offer();
		return List.of();
	}

	/** `/ksu update cancel` and the card's *Cancel pending update* (REQ-UPD-12). */
	List<String> cancel() {
		return switch (service.cancelPending()) {
			case CANCELLED -> List.of("The pending update is cancelled; nothing is installed when you quit");
			case NOTHING_PENDING -> List.of("No update is pending");
			case BUSY -> List.of("An update is downloading, try again when it is done");
		};
	}

	/** The candidate the confirmation screen shows, or null when nothing is on offer. */
	CheckOutcome.Found showable() {
		return service.offer() instanceof CheckOutcome.Found found ? found : null;
	}

	List<String> nothingToShow() {
		return List.of("No update is known yet; /ksu update check looks for one");
	}
}
