package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.ui.notice.Notice;
import com.k8bas.skyblockutility.ui.notice.Notices;
import com.k8bas.skyblockutility.util.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The automatic check's notices (REQ-UPD-08, REQ-UPD-10): for a found update a toast on whatever screen is open, once per
 * version in a session, and once per game session, on the first world join, a local chat line with [Changelog] [Update]
 * [Skip this version] [Open release page]. [Update] only opens the confirmation screen; nothing is downloaded without its
 * one click. A notify-only update says why and has no [Update]. A skipped version, a version already staged for install
 * (R53), a checksum conflict and every other outcome show nothing. The updater never opens a screen or takes focus on its own, and sends nothing to the server.
 */
public final class UpdateNotifier {
	static final String REPOSITORY = "https://github.com/Kesuhi/K8basSkyblockUtility";
	/** One release page: /releases/tag/<tag>, with no "." or ".." segment. */
	private static final java.util.regex.Pattern RELEASE_PAGE =
			java.util.regex.Pattern.compile("^/Kesuhi/K8basSkyblockUtility/releases/tag/[0-9A-Za-z_+-][0-9A-Za-z._+-]*$");

	private final String runningVersion;
	private final Logger logger;
	private final Consumer<Notice> toasts;
	private final AtomicBoolean loggedNotSemVer = new AtomicBoolean();
	private final Set<String> toasted = ConcurrentHashMap.newKeySet();
	/** Lines the launch's reconciliation left for the first world join (T4.3b). */
	private final Queue<String> launchLines = new ConcurrentLinkedQueue<>();
	private volatile CheckOutcome.Found pending;
	/** The version staged for install at the next exit, or null. */
	private volatile Supplier<String> staged = () -> null;
	/** What the service offers now (its last offer), or null when it has no view of its own. */
	private volatile Supplier<CheckOutcome> currentOffer = () -> null;
	/** Client thread only. */
	private boolean shown;
	private int shownCount;

	public UpdateNotifier(String runningVersion, Logger logger) {
		this(runningVersion, logger, Notices::post);
	}

	/** @param toasts posts a toast; the notice component in the game */
	public UpdateNotifier(String runningVersion, Logger logger, Consumer<Notice> toasts) {
		this.runningVersion = runningVersion;
		this.logger = logger;
		this.toasts = toasts;
	}

	/** Takes a check's outcome; any thread. Only a found, unskipped update without a checksum conflict leads to a notice. */
	public void offer(CheckOutcome outcome) {
		// A conflict shows nothing (REQ-UPD-08), nor a checksum file not read yet: a later check may read it and offer the install.
		if (outcome instanceof CheckOutcome.Found found && !found.skipped() && !(found.hash() instanceof UpdateHooks.Hash.Conflict)
				&& !(found.hash() instanceof UpdateHooks.Hash.Unavailable)) {
			pending = found;
			if (!isStaged(found) && toasted.add(found.candidate().version().toString())) {
				toasts.accept(toast(found));
			}
			return;
		}
		pending = null;
		if (outcome instanceof CheckOutcome.NotARelease notARelease && loggedNotSemVer.compareAndSet(false, true)) {
			logger.info("Running version {} is not a SemVer version; no update is offered (newest release: {})",
					runningVersion, notARelease.newest());
		}
	}

	/** Where the version staged for install is read (the service's pending install); its notices stop (R53). */
	public void setStaged(Supplier<String> stagedVersion) {
		staged = stagedVersion;
	}

	/**
	 * Where the service's offer is read when the join's line is due: a skip or a channel pick after the toast changes it,
	 * and the line offers only what is still on offer.
	 */
	public void setCurrentOffer(Supplier<CheckOutcome> offer) {
		currentOffer = offer;
	}

	/** Lines to show once, as soon as the player is in a world; any thread. */
	public void showOnJoin(List<String> lines) {
		launchLines.addAll(lines);
	}

	/** Shows the pending notice once per session, as soon as the player is in a world. */
	public void tick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		flushLaunchLines(ChatUtils::chat);
		showJoinLine(ChatUtils::chat);
	}

	/**
	 * Hands the found update's line to {@code chat}, once per session, when it is due in a world. Not for a version staged
	 * for install (R53): an install confirmed after the toast leaves nothing to offer. Cancelled again before the join, the
	 * line still shows; cancelled later, it does not come back, as the join it belonged to has passed. Nor for a version
	 * skipped, or left out by a channel pick, since the toast.
	 */
	void showJoinLine(Consumer<Component> chat) {
		CheckOutcome.Found found = pending;
		if (shown || found == null) {
			return;
		}
		shown = true;
		if (!isStaged(found) && stillOffered(found)) {
			shownCount++;
			chat.accept(message(found, runningVersion));
		}
	}

	private boolean stillOffered(CheckOutcome.Found found) {
		CheckOutcome now = currentOffer.get();
		return now == null || now instanceof CheckOutcome.Found current && !current.skipped()
				&& current.candidate().version().compareTo(found.candidate().version()) == 0;
	}

	private boolean isStaged(CheckOutcome.Found found) {
		SemVer version = SemVer.Companion.fromTagOrNull(staged.get());
		return version != null && version.compareTo(found.candidate().version()) == 0;
	}

	/** Hands the launch's lines to {@code chat}, each once (T4.3b). */
	void flushLaunchLines(Consumer<Component> chat) {
		String line;
		while ((line = launchLines.poll()) != null) {
			chat.accept(Component.literal(line).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** For tests: how many notices this session showed. */
	int shownCount() {
		return shownCount;
	}

	/** For tests: the update the chat line would name. */
	CheckOutcome.Found pendingForTests() {
		return pending;
	}

	/** The toast: the version, and where to go from here. */
	static Notice toast(CheckOutcome.Found found) {
		return new Notice(Updates.NOTICE_TITLE, List.of("v" + found.candidate().version() + (found.beta() ? " (beta)" : "") + " is available",
				found.installOffered() ? "Open /ksu update to install it" : "Install it by hand, see /ksu update"));
	}

	static Component message(CheckOutcome.Found found, String runningVersion) {
		String running = SemVer.parse(runningVersion).map(SemVer::toString).orElse(runningVersion);
		String version = found.candidate().version().toString();
		MutableComponent text = Component.literal("K8bas Skyblock Utility v" + version + (found.beta() ? " (beta)" : "")
				+ " is available; you have v" + running + ". ").withStyle(ChatFormatting.YELLOW);
		String reason = UpdateHooks.notOfferedReason(found.hash(), found.eligibility());
		if (reason != null) {
			text.append(Component.literal(reason + ". ").withStyle(ChatFormatting.YELLOW));
		}
		text.append(link("[Changelog]", changelog(found.candidate().release().tag())));
		text.append(Component.literal(" "));
		if (found.installOffered()) {
			text.append(command("[Update]", "/ksu update show", "Shows the update before anything is downloaded"));
			text.append(Component.literal(" "));
		}
		text.append(command("[Skip this version]", "/ksu update skip " + version, "Asks once more before skipping"));
		text.append(Component.literal(" "));
		text.append(link("[Open release page]", releasePage(found.candidate().release().htmlUrl())));
		return text;
	}

	private static Component link(String label, URI target) {
		return Component.literal(label).withStyle(Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(target))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(target.toString()))));
	}

	/** A local command of this mod's own (Fabric runs client commands from a chat click); nothing reaches the server. */
	private static Component command(String label, String command, String hover) {
		return Component.literal(label).withStyle(Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	/** The release's own page if it is one of this repository's release pages, else the release list. */
	static URI releasePage(String htmlUrl) {
		try {
			URI uri = URI.create(htmlUrl);
			if ("https".equals(uri.getScheme()) && "github.com".equals(uri.getHost()) && uri.getPort() == -1
					&& uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
					&& uri.getRawPath() != null && RELEASE_PAGE.matcher(uri.getRawPath()).matches()) {
				return uri;
			}
		} catch (IllegalArgumentException | NullPointerException invalid) {
			// falls through to the release list
		}
		return URI.create(REPOSITORY + "/releases");
	}

	/** CHANGELOG.md as of the release's tag. */
	static URI changelog(String tag) {
		return URI.create(REPOSITORY + "/blob/" + URLEncoder.encode(tag, StandardCharsets.UTF_8) + "/CHANGELOG.md");
	}
}
