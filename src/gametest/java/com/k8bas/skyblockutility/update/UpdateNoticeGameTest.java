package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.net.SharedHttpClient;
import com.k8bas.skyblockutility.ui.notice.Notice;
import com.k8bas.skyblockutility.ui.notice.Notices;
import com.k8bas.skyblockutility.ui.render.UiTime;
import com.k8bas.skyblockutility.util.RuntimeVersions;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * T1.4 and T4.4: AC-UPD-01's request rules and AC-UPD-11 [C]. With a local stand-in for GitHub serving a newer version,
 * the automatic check posts one toast on the title screen; the first world join of the session shows one local chat
 * line with [Changelog] [Update] [Skip this version] [Open release page], the next join none. The confirmation screen
 * shows every REQ-UPD-11 field, with notes full of `§` codes and Markdown as plain text cut at 4,000 characters. One GET
 * reaches the stand-in, without a 26.1 version, and nothing is downloaded.
 */
public class UpdateNoticeGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String PAGE = "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v99.0.0";
	private static final String DIGEST = "0123456789abcdef".repeat(4);
	/** Notes as GitHub could hold them: headings, emphasis, links, formatting codes and far more than 4,000 characters. */
	private static final String NOTES = "## Added\\n\\n- **Bold** feature with §cred§r text and [a link](https://example.com)\\n- `code` and *emphasis*\\n\\n"
			+ "### Fixed\\n\\n" + "- A long line of fixes that keeps going and going. ".repeat(120);

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> requests = new CopyOnWriteArrayList<>();
		HttpServer server = startServer(requests);
		// The client is shared with the other gametests: the updater they find afterwards is the one they had.
		UpdateService previousService = Updates.service();
		UpdateNotifier previousNotifier = Updates.notifier();
		try {
			Path stateDir = Files.createTempDirectory("k8bas-update-test");
			String modVersion = RuntimeVersions.mod(K8basSkyblockUtilityClient.MOD_ID);
			String minecraft = RuntimeVersions.minecraft();
			URI source = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/repos/Kesuhi/K8basSkyblockUtility/releases?per_page=30");
			// The hash from GitHub's digest; eligible as an installed jar would be, so the line has [Update]. Nothing is downloaded.
			UpdateService service = new UpdateService(
					new GitHubReleaseSource(source, SharedHttpClient.get(), modVersion, GitHubReleaseSource.REQUEST_TIMEOUT),
					new UpdateStateStore(stateDir.resolve(UpdateStateStore.FILE_NAME), LOGGER::warn),
					System::currentTimeMillis, () -> true, modVersion, minecraft, () -> null, LOGGER::info, LOGGER::warn,
					new UpdateHooks(UpdateHooks.DEFAULT.getHash(), () -> UpdateHooks.Eligibility.ELIGIBLE));
			UpdateNotifier notifier = new UpdateNotifier(modVersion, LOGGER);
			context.runOnClient(client -> Notices.clear());
			Updates.use(service, notifier);
			check(service.checkAutomatic(), "the check sent a request");
			check(requests.size() == 1, "one GET: " + requests);
			check(!String.join("\n", requests).contains("26.1"), "no 26.1 version in the request: " + requests);

			// AC-UPD-11: the toast on the title screen.
			context.waitTicks(2);
			List<Notice> toasts = context.computeOnClient(client -> Notices.visible().stream().map(entry -> entry.notice()).toList());
			LOGGER.info("update toasts: {}", toasts);
			check(toasts.equals(List.of(new Notice("K8bas Skyblock Utility", List.of("v99.0.0 is available", "Open /ksu update to install it")))),
					"one toast names the version: " + toasts);
			context.takeScreenshot("t4.4-update-toast-title-screen");

			List<String> firstJoin;
			try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
				context.waitTicks(5);
				firstJoin = context.computeOnClient(UpdateNoticeGameTest::noticeLines);
				List<String> clicks = context.computeOnClient(UpdateNoticeGameTest::noticeClicks);
				LOGGER.info("update notice on the first join: {} with clicks {}", firstJoin, clicks);
				check(firstJoin.size() == 1, "one notice on the first join: " + firstJoin);
				check(firstJoin.getFirst().contains("v99.0.0 is available")
						&& firstJoin.getFirst().endsWith("[Changelog] [Update] [Skip this version] [Open release page]"), "the notice has its four links");
				check(clicks.equals(List.of("https://github.com/Kesuhi/K8basSkyblockUtility/blob/v99.0.0/CHANGELOG.md", "/ksu update show",
						"/ksu update skip 99.0.0", PAGE)), "each link does what it says: " + clicks);
				context.takeScreenshot("t4.4-update-chat-line");
			}
			int shownBeforeSecondJoin = context.computeOnClient(client -> notifier.shownCount());
			try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
				context.waitTicks(5);
				// Read from the chat itself, which the game empties when a world is left: no line came, from any source.
				List<String> secondJoin = context.computeOnClient(UpdateNoticeGameTest::noticeLines);
				int shown = context.computeOnClient(client -> notifier.shownCount());
				LOGGER.info("update notices on the second join: {} (shown in all {})", secondJoin, shown);
				check(secondJoin.isEmpty() && shownBeforeSecondJoin == 1 && shown == 1, "no notice on the second join: " + secondJoin);
				context.takeScreenshot("t4.4-update-second-join");
			}

			// AC-UPD-11: the confirmation screen with every REQ-UPD-11 field; the notes plain and cut.
			CheckOutcome.Found found = (CheckOutcome.Found) service.offer();
			Confirmation confirmation = service.confirmation(found);
			check(confirmation.getNotes().length() <= ReleaseNotes.MAX_CHARS && confirmation.getNotes().endsWith("…"), "the notes are cut at 4,000");
			context.setScreen(() -> new UpdateScreen(null, found, confirmation, action -> CompletableFuture.completedFuture(List.of()), lines -> { }));
			context.waitTicks(3);
			List<String> drawn = context.computeOnClient(client -> ((UpdateScreen) client.gui.screen()).drawnTextForTests());
			String running = SemVer.parse(modVersion).map(SemVer::toString).orElse(modVersion);
			LOGGER.info("update screen texts: {}", drawn.subList(0, Math.min(14, drawn.size())));
			for (String field : List.of("v" + running + " → v99.0.0", "Stable", "k8bas_skyblock_utility-99.0.0+26.2.jar", "1.2 MB",
					DIGEST.substring(0, 12) + "...", "Added", "• Bold feature with red text and a link", "• code and emphasis")) {
				check(drawn.contains(field), "the screen shows " + field + ": " + drawn);
			}
			String notes = String.join("\n", drawn);
			check(!notes.contains("§") && !notes.contains("**") && !notes.contains("](") && !notes.contains("##"), "no formatting codes or Markdown");
			boolean download = context.computeOnClient(client -> ((UpdateScreen) client.gui.screen()).downloadButtonForTests().isEnabled());
			check(download, "Download & install on restart is offered");
			context.takeScreenshot("t4.4-update-confirmation-screen");
			// EC-UPD-10: the notes end in the cut, scrolled to their end.
			context.runOnClient(client -> ((UpdateScreen) client.gui.screen()).scrollNotesToEndForTests());
			context.waitTicks(2);
			List<String> scrolled = context.computeOnClient(client -> ((UpdateScreen) client.gui.screen()).drawnTextForTests());
			check(scrolled.stream().anyMatch(line -> line.endsWith("…")), "the last note line ends in the cut");
			context.takeScreenshot("t4.4-update-notes-cut");
			context.setScreen(() -> null);

			screenActions(context, service, found, confirmation);

			check(requests.size() == 1, "still one request, nothing downloaded: " + requests);
			try (var files = Files.list(stateDir)) {
				List<String> names = files.map(path -> path.getFileName().toString()).toList();
				check(names.equals(List.of(UpdateStateStore.FILE_NAME)), "only the state file is written: " + names);
			}
		} catch (IOException e) {
			throw new AssertionError(e);
		} finally {
			context.runOnClient(client -> Notices.clear());
			if (previousService != null && previousNotifier != null) {
				Updates.use(previousService, previousNotifier);
			}
			server.stop(0);
		}
	}

	/**
	 * T4.4 review: what the confirmation screen's buttons do. Later and Esc run nothing; Skip needs a second click within
	 * 3 s, not a double-click, and its answer reaches the player after the screen closed; the download click carries the
	 * checksum the screen showed; a notify-only screen has no download button and says why.
	 */
	private static void screenActions(ClientGameTestContext context, UpdateService service, CheckOutcome.Found found, Confirmation confirmation) {
		List<Function<UpdateActions, List<String>>> calls = new CopyOnWriteArrayList<>();
		List<List<String>> said = new CopyOnWriteArrayList<>();
		Function<Function<UpdateActions, List<String>>, CompletableFuture<List<String>>> recording = action -> {
			calls.add(action);
			return CompletableFuture.completedFuture(List.of("ran"));
		};
		AtomicLong clock = new AtomicLong(1_000);
		java.util.function.Supplier<UpdateScreen> screen = () -> new UpdateScreen(null, found, confirmation, recording, said::add,
				UiTime.of(clock::get, () -> false));

		// AC-UPD-13 "Later": Esc and Later download nothing and skip nothing.
		context.setScreen(screen::get);
		context.waitTicks(2);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> !(client.gui.screen() instanceof UpdateScreen)), "Esc closes the screen");
		context.setScreen(screen::get);
		context.waitTicks(2);
		context.runOnClient(client -> ((UpdateScreen) client.gui.screen()).laterButtonForTests().activate());
		context.waitTicks(2);
		check(context.computeOnClient(client -> !(client.gui.screen() instanceof UpdateScreen)), "Later closes the screen");
		check(calls.isEmpty() && service.state().skippedVersion == null, "Esc and Later ran nothing: " + calls.size());

		// REQ-UPD-12: Skip asks first; the ask runs out after 3 s; a double-click is not the second click.
		context.setScreen(screen::get);
		context.waitTicks(2);
		context.runOnClient(client -> ((UpdateScreen) client.gui.screen()).skipButtonForTests().activate());
		check(context.computeOnClient(client -> ((UpdateScreen) client.gui.screen()).skipButtonForTests().label()).equals(UpdateScreen.CONFIRM_SKIP),
				"the first click asks");
		context.takeScreenshot("t4.4-update-skip-asks");
		clock.addAndGet(UpdateScreen.SKIP_CONFIRM_MS + 1);
		context.waitTicks(2);
		check(context.computeOnClient(client -> ((UpdateScreen) client.gui.screen()).skipButtonForTests().label()).equals(UpdateScreen.SKIP),
				"the ask runs out after 3 s");
		context.runOnClient(client -> ((UpdateScreen) client.gui.screen()).skipButtonForTests().activate());
		clock.addAndGet(100);
		context.runOnClient(client -> ((UpdateScreen) client.gui.screen()).skipButtonForTests().activate());
		check(calls.isEmpty() && context.computeOnClient(client -> client.gui.screen() instanceof UpdateScreen), "a double-click skips nothing");
		clock.addAndGet(UpdateScreen.SKIP_MIN_GAP_MS);
		context.runOnClient(client -> ((UpdateScreen) client.gui.screen()).skipButtonForTests().activate());
		context.waitTicks(2);
		check(calls.size() == 1 && context.computeOnClient(client -> !(client.gui.screen() instanceof UpdateScreen)), "the second click skips and closes");
		check(said.equals(List.of(List.of("ran"))), "the skip's answer reaches the player after the screen closed: " + said);
		calls.clear();

		// The download click carries the checksum the screen showed: shown one thing, GitHub now another, it is refused.
		CheckOutcome.Found seen = new CheckOutcome.Found(found.candidate(), new UpdateHooks.Hash.Verifiable("ab".repeat(32)), found.eligibility(),
				false, false, found.minecraft());
		Confirmation shown = service.confirmation(seen);
		context.setScreen(() -> new UpdateScreen(null, seen, shown,
				action -> CompletableFuture.supplyAsync(() -> action.apply(new UpdateActions(service, java.time.ZoneId.of("UTC")))), lines -> { }));
		context.waitTicks(2);
		context.runOnClient(client -> ((UpdateScreen) client.gui.screen()).downloadButtonForTests().activate());
		for (int i = 0; i < 40 && context.computeOnClient(client -> ((UpdateScreen) client.gui.screen()).busyForTests()); i++) {
			context.waitTicks(1);
		}
		List<String> status = context.computeOnClient(client -> ((UpdateScreen) client.gui.screen()).statusForTests());
		check(status.equals(List.of("v99.0.0 changed on GitHub since you saw it; open it again to confirm")), "refused with the shown checksum: " + status);
		context.takeScreenshot("t4.4-update-changed");
		context.setScreen(() -> null);

		// REQ-UPD-08: a notify-only update: no download button, the reason shown.
		CheckOutcome.Found manual = new CheckOutcome.Found(found.candidate(), UpdateHooks.Hash.Missing.INSTANCE, found.eligibility(), false, false,
				found.minecraft());
		context.setScreen(() -> new UpdateScreen(null, manual, service.confirmation(manual), recording, lines -> { }));
		context.waitTicks(2);
		check(!context.computeOnClient(client -> ((UpdateScreen) client.gui.screen()).hasDownloadButtonForTests()), "no download button");
		List<String> notifyOnly = context.computeOnClient(client -> ((UpdateScreen) client.gui.screen()).drawnTextForTests());
		check(String.join(" ", notifyOnly).contains("It has no SHA-256 checksum, so install it by hand from the release page"), "the whole reason: " + notifyOnly);
		check(notifyOnly.stream().noneMatch(line -> line.startsWith("It has no") && line.endsWith("...")), "the reason is not cut");
		context.takeScreenshot("t4.4-update-notify-only");
		context.setScreen(() -> null);
		check(calls.isEmpty(), "nothing ran on the notify-only screen");
	}

	private static HttpServer startServer(List<String> requests) {
		String body = """
				[{"tag_name": "v99.0.0", "html_url": "%s", "draft": false, "prerelease": false, "body": "%s",
				  "assets": [{"name": "k8bas_skyblock_utility-99.0.0+26.2.jar", "state": "uploaded",
				              "browser_download_url": "https://github.com/x.jar", "digest": "sha256:%s", "size": 1234567}]}]
				""".formatted(PAGE, NOTES, DIGEST);
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/", exchange -> {
				requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().entrySet());
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, bytes.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(bytes);
				}
			});
			server.start();
			return server;
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static List<GuiMessage> messages(Minecraft client) {
		try {
			Field field = ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<GuiMessage> messages = (List<GuiMessage>) field.get(client.gui.hud.getChat());
			return List.copyOf(messages);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("cannot read the chat", e);
		}
	}

	private static List<String> noticeLines(Minecraft client) {
		return messages(client).stream().map(message -> message.content().getString()).filter(text -> text.contains("is available")).toList();
	}

	private static List<String> noticeClicks(Minecraft client) {
		List<String> clicks = new ArrayList<>();
		for (GuiMessage message : messages(client)) {
			Component content = message.content();
			if (content.getString().contains("is available")) {
				content.visit((style, text) -> {
					if (style.getClickEvent() instanceof ClickEvent.OpenUrl(URI uri)) {
						clicks.add(uri.toString());
					} else if (style.getClickEvent() instanceof ClickEvent.RunCommand(String command)) {
						clicks.add(command);
					}
					return Optional.empty();
				}, Style.EMPTY);
			}
		}
		return clicks;
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
