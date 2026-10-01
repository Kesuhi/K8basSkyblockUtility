package com.k8bas.skyblockutility.net;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedHttpClientTest {
	/** AC-NPCDB-04 [A] (T1.12): a server that accepts the connection and never answers ends the
	 *  request within 10 ± 1 s, with one warning. */
	@Test
	void aServerThatNeverAnswersTimesOutAfterTenSeconds() throws Exception {
		List<Socket> held = new CopyOnWriteArrayList<>();
		try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
			Thread acceptor = Thread.ofVirtual().start(() -> {
				try {
					while (!server.isClosed()) {
						held.add(server.accept()); // read nothing, answer nothing
					}
				} catch (IOException closed) {
					// the test is over
				}
			});
			List<String> warnings = new ArrayList<>();
			long start = System.nanoTime();
			String body = SharedHttpClient.fetchText(URI.create("http://127.0.0.1:" + server.getLocalPort() + "/db.json"),
					"Test fetch", warnings::add);
			double seconds = (System.nanoTime() - start) / 1e9;

			assertNull(body);
			assertTrue(seconds >= 9 && seconds <= 11, "ended after " + seconds + " s");
			assertEquals(1, warnings.size(), warnings.toString());
			assertTrue(warnings.get(0).startsWith("Test fetch timed out"), warnings.get(0));
			server.close();
			acceptor.join();
		} finally {
			for (Socket socket : held) {
				socket.close();
			}
		}
	}

	@Test
	void timeoutsAreTenSeconds() {
		assertEquals(10, SharedHttpClient.CONNECT_TIMEOUT.toSeconds());
		assertEquals(10, SharedHttpClient.REQUEST_TIMEOUT.toSeconds());
		assertEquals(SharedHttpClient.CONNECT_TIMEOUT, SharedHttpClient.get().connectTimeout().orElseThrow());
	}
}
