package com.k8bas.skyblockutility.update;

import com.k8bas.skyblockutility.K8basSkyblockUtilityClient;
import com.k8bas.skyblockutility.net.SharedHttpClient;
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * T1.4, AC-UPD-01 and the chat-line part of AC-UPD-11 [C]: with a local stand-in for GitHub serving
 * a newer version, the first world join of the session shows one local chat line with
 * [Changelog] and [Open release page]; the next join shows none. One GET reaches the stand-in,
 * without a 26.1 version, and nothing is downloaded.
 */
public class UpdateNoticeGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	private static final String PAGE = "https://github.com/Kesuhi/K8basSkyblockUtility/releases/tag/v99.0.0";

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> requests = new CopyOnWriteArrayList<>();
		HttpServer server = startServer(requests);
		try {
			Path stateDir = Files.createTempDirectory("k8bas-update-test");
			String modVersion = RuntimeVersions.mod(K8basSkyblockUtilityClient.MOD_ID);
			String minecraft = RuntimeVersions.minecraft();
			URI source = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/repos/Kesuhi/K8basSkyblockUtility/releases?per_page=30");
			UpdateService service = new UpdateService(
					new GitHubReleaseSource(source, SharedHttpClient.get(), modVersion, GitHubReleaseSource.REQUEST_TIMEOUT),
					new UpdateStateStore(stateDir.resolve(UpdateStateStore.FILE_NAME), LOGGER::warn),
					System::currentTimeMillis, () -> true, modVersion, minecraft, () -> null, LOGGER::info, LOGGER::warn);
			UpdateNotifier notifier = new UpdateNotifier(modVersion, LOGGER);
			Updates.use(service, notifier);
			check(service.checkAutomatic(), "the check sent a request");
			check(requests.size() == 1, "one GET: " + requests);
			check(!String.join("\n", requests).contains("26.1"), "no 26.1 version in the request: " + requests);

			List<String> firstJoin;
			try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
				context.waitTicks(5);
				firstJoin = context.computeOnClient(UpdateNoticeGameTest::noticeLines);
				List<URI> links = context.computeOnClient(UpdateNoticeGameTest::noticeLinks);
				LOGGER.info("update notice on the first join: {} with links {}", firstJoin, links);
				check(firstJoin.size() == 1, "one notice on the first join: " + firstJoin);
				check(firstJoin.getFirst().contains("v99.0.0 is available") && firstJoin.getFirst().endsWith("[Changelog] [Open release page]"),
						"the notice names the version and has both links");
				check(links.contains(URI.create(PAGE)), "[Open release page] opens the release page");
			}
			int shownBeforeSecondJoin = context.computeOnClient(client -> notifier.shownCount());
			try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
				context.waitTicks(5);
				int shown = context.computeOnClient(client -> notifier.shownCount());
				LOGGER.info("update notices after the second join: {}", shown);
				check(shownBeforeSecondJoin == 1 && shown == 1, "no notice on the second join");
			}
			check(requests.size() == 1, "still one request: " + requests);
			try (var files = Files.list(stateDir)) {
				List<String> names = files.map(path -> path.getFileName().toString()).toList();
				check(names.equals(List.of(UpdateStateStore.FILE_NAME)), "only the state file is written: " + names);
			}
		} catch (IOException e) {
			throw new AssertionError(e);
		} finally {
			server.stop(0);
		}
	}

	private static HttpServer startServer(List<String> requests) {
		String body = """
				[{"tag_name": "v99.0.0", "html_url": "%s", "draft": false, "prerelease": false,
				  "assets": [{"name": "k8bas_skyblock_utility-99.0.0+26.2.jar", "state": "uploaded",
				              "browser_download_url": "https://github.com/x.jar", "digest": null, "size": 1}]}]
				""".formatted(PAGE);
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

	private static List<URI> noticeLinks(Minecraft client) {
		List<URI> links = new ArrayList<>();
		for (GuiMessage message : messages(client)) {
			Component content = message.content();
			if (content.getString().contains("is available")) {
				content.visit((style, text) -> {
					if (style.getClickEvent() instanceof ClickEvent.OpenUrl(URI uri)) {
						links.add(uri);
					}
					return Optional.empty();
				}, Style.EMPTY);
			}
		}
		return links;
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
