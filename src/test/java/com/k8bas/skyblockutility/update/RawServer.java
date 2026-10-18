package com.k8bas.skyblockutility.update;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A plain-socket HTTP/1.1 server on localhost for answers {@link MockGitHub}'s HttpServer cannot give: a body cut off
 * mid-way by closing the connection, or a malformed header. Each connection takes the next queued answer, then closes.
 */
final class RawServer implements AutoCloseable {
	/** @param keepBytes how many body bytes to send before closing; the whole body if negative */
	record Answer(String head, byte[] body, int keepBytes) {
		/** A 200 whose Content-Length is the body's, cut off after half of it. */
		static Answer droppedMidBody(byte[] body) {
			return new Answer("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n", body, body.length / 2);
		}

		static Answer full(byte[] body) {
			return new Answer("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n", body, -1);
		}

		static Answer withHead(String head, byte[] body) {
			return new Answer(head, body, -1);
		}
	}

	private final ServerSocket server;
	private final Deque<Answer> answers = new ArrayDeque<>();
	final List<String> requests = new CopyOnWriteArrayList<>();

	RawServer() throws IOException {
		server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		Thread.ofVirtual().name("raw-http-server").start(this::serve);
	}

	private void serve() {
		while (!server.isClosed()) {
			try (Socket socket = server.accept()) {
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
				String requestLine = in.readLine();
				String line;
				while ((line = in.readLine()) != null && !line.isEmpty()) {
					// the headers are not needed
				}
				requests.add(requestLine);
				Answer answer;
				synchronized (answers) {
					answer = answers.isEmpty() ? Answer.withHead("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n", new byte[0]) : answers.poll();
				}
				OutputStream out = socket.getOutputStream();
				out.write((answer.head() + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
				out.write(answer.body(), 0, answer.keepBytes() < 0 ? answer.body().length : answer.keepBytes());
				out.flush();
			} catch (IOException closedOrGone) {
				// the next connection, or the end
			}
		}
	}

	void enqueue(Answer... queued) {
		synchronized (answers) {
			answers.addAll(List.of(queued));
		}
	}

	URI at(String path) {
		return URI.create("http://127.0.0.1:" + server.getLocalPort() + path);
	}

	int port() {
		return server.getLocalPort();
	}

	@Override
	public void close() throws IOException {
		server.close();
	}
}
