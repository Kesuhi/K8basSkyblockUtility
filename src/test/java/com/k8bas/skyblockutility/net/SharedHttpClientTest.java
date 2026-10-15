package com.k8bas.skyblockutility.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The one HttpClient, now used only by the update check (REQ-UPD-09): connecting gives up after 10 s. */
class SharedHttpClientTest {
	@Test
	void connectingGivesUpAfterTenSeconds() {
		assertEquals(10, SharedHttpClient.CONNECT_TIMEOUT.toSeconds());
		assertEquals(SharedHttpClient.CONNECT_TIMEOUT, SharedHttpClient.get().connectTimeout().orElseThrow());
	}
}
