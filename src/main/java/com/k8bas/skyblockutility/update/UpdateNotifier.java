package com.k8bas.skyblockutility.update;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The only update notice before the Phase 4 updater (REQ-UPD-01, REQ-UPD-10): one local chat line
 * per game session, once the player is in a world, with [Changelog] and [Open release page].
 * Clicking a link goes through the game's own link confirmation. No toast, nothing downloaded,
 * nothing sent to the server.
 */
public final class UpdateNotifier {
	static final String REPOSITORY = "https://github.com/Kesuhi/K8basSkyblockUtility";
	/** One release page: /releases/tag/<tag>, with no "." or ".." segment. */
	private static final java.util.regex.Pattern RELEASE_PAGE =
			java.util.regex.Pattern.compile("^/Kesuhi/K8basSkyblockUtility/releases/tag/[0-9A-Za-z_+-][0-9A-Za-z._+-]*$");

	private final String runningVersion;
	private final Logger logger;
	private final AtomicBoolean loggedNotSemVer = new AtomicBoolean();
	private volatile CandidateFinder.Candidate pending;
	/** Client thread only. */
	private boolean shown;
	private int shownCount;

	public UpdateNotifier(String runningVersion, Logger logger) {
		this.runningVersion = runningVersion;
		this.logger = logger;
	}

	/** Takes a check's outcome; any thread. Only a candidate leads to a notice. */
	public void offer(CandidateFinder.Outcome outcome) {
		if (outcome instanceof CandidateFinder.Candidate candidate) {
			pending = candidate;
		} else if (outcome instanceof CandidateFinder.RunningNotSemVer notSemVer && loggedNotSemVer.compareAndSet(false, true)) {
			logger.info("Running version {} is not a SemVer version; no update is offered (newest release: {})",
					runningVersion, notSemVer.newest());
		}
	}

	/** Shows the pending notice once per session, as soon as the player is in a world. */
	public void tick(Minecraft client) {
		CandidateFinder.Candidate candidate = pending;
		if (!shown && candidate != null && client.player != null) {
			shown = true;
			shownCount++;
			ChatUtils.chat(message(candidate, runningVersion));
		}
	}

	/** For tests: how many notices this session showed. */
	int shownCount() {
		return shownCount;
	}

	static Component message(CandidateFinder.Candidate candidate, String runningVersion) {
		String running = SemVer.parse(runningVersion).map(SemVer::toString).orElse(runningVersion);
		boolean beta = UpdateChannel.isPreRelease(candidate.release(), candidate.version());
		MutableComponent text = Component.literal("K8bas Skyblock Utility v" + candidate.version() + (beta ? " (beta)" : "")
				+ " is available; you have v" + running + ". ").withStyle(ChatFormatting.YELLOW);
		text.append(link("[Changelog]", changelog(candidate.release().tag())));
		text.append(Component.literal(" "));
		text.append(link("[Open release page]", releasePage(candidate.release().htmlUrl())));
		return text;
	}

	private static Component link(String label, URI target) {
		return Component.literal(label).withStyle(Style.EMPTY
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(target))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(target.toString()))));
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
