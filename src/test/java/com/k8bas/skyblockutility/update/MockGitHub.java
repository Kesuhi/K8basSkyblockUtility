package com.k8bas.skyblockutility.update;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/** A recording stand-in for the GitHub API and its download hosts on localhost; answers are queued per request. */
final class MockGitHub implements AutoCloseable {
	record Request(String method, String pathAndQuery, Map<String, List<String>> headers) {
		String header(String name) {
			for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
				if (entry.getKey().equalsIgnoreCase(name)) {
					return entry.getValue().getFirst();
				}
			}
			return null;
		}
	}

	/**
	 * @param stallMs after sending the headers and half the body, wait this long before the rest
	 * @param noLength send the body without a Content-Length
	 * @param gate if not null, the answer waits until it opens (at most 10 s)
	 */
	record Answer(int status, Map<String, String> headers, byte[] body, long delayMs, long stallMs, boolean noLength, CountDownLatch gate) {
		static Answer json(int status, String body, String... headerPairs) {
			return bytes(status, body.getBytes(StandardCharsets.UTF_8), headerPairs);
		}

		static Answer bytes(int status, byte[] body, String... headerPairs) {
			Map<String, String> headers = new java.util.LinkedHashMap<>();
			for (int i = 0; i + 1 < headerPairs.length; i += 2) {
				headers.put(headerPairs[i], headerPairs[i + 1]);
			}
			return new Answer(status, headers, body, 0, 0, false, null);
		}

		/** A redirect with no body. */
		static Answer redirect(int status, String location) {
			return bytes(status, new byte[0], "Location", location);
		}

		Answer delayed(long ms) {
			return new Answer(status, headers, body, ms, stallMs, noLength, gate);
		}

		Answer stallingMidBody(long ms) {
			return new Answer(status, headers, body, delayMs, ms, noLength, gate);
		}

		Answer chunked() {
			return new Answer(status, headers, body, delayMs, stallMs, true, gate);
		}

		/** Holds the answer back until {@code latch} opens, so a test decides when the request ends. */
		Answer gated(CountDownLatch latch) {
			return new Answer(status, headers, body, delayMs, stallMs, noLength, latch);
		}
	}

	private final HttpServer server;
	private final Deque<Answer> answers = new ArrayDeque<>();
	final List<Request> requests = new CopyOnWriteArrayList<>();

	MockGitHub() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> {
			Headers in = exchange.getRequestHeaders();
			requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(), Map.copyOf(in)));
			Answer answer;
			synchronized (answers) {
				answer = answers.isEmpty() ? Answer.json(500, "{}") : answers.poll();
			}
			if (answer.gate() != null) {
				try {
					answer.gate().await(10, java.util.concurrent.TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			if (answer.delayMs() > 0) {
				try {
					Thread.sleep(answer.delayMs());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			answer.headers().forEach(exchange.getResponseHeaders()::add);
			boolean noBody = answer.status() == 304 || answer.status() / 100 == 3 && answer.body().length == 0;
			exchange.sendResponseHeaders(answer.status(), noBody ? -1 : answer.noLength() ? 0 : answer.body().length);
			if (!noBody) {
				try (OutputStream out = exchange.getResponseBody()) {
					if (answer.stallMs() > 0) {
						int half = answer.body().length / 2;
						out.write(answer.body(), 0, half);
						out.flush();
						try {
							Thread.sleep(answer.stallMs());
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						out.write(answer.body(), half, answer.body().length - half);
					} else {
						out.write(answer.body());
					}
				} catch (IOException clientGone) {
					// the client gave up
				}
			}
			exchange.close();
		});
		server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
		server.start();
	}

	void enqueue(Answer... queued) {
		synchronized (answers) {
			answers.addAll(List.of(queued));
		}
	}

	URI releases() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/repos/Kesuhi/K8basSkyblockUtility/releases?per_page=30");
	}

	URI at(String path) {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
