package com.k8bas.skyblockutility.skyblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reader base (T3.0a): REQ-GS-16 / AC-GS-16 (first bullet), REQ-GS-02's "parse at most once per tick",
 * REQ-GS-15 (clear), REQ-GS-11 (thread guard) and REQ-GS-10 / AC-GS-10 (one WARN per unknown shape).
 */
class PassiveReaderTest {
	private final AtomicReference<String> raw = new AtomicReference<>("a");
	private final AtomicInteger reads = new AtomicInteger();
	private final AtomicInteger guards = new AtomicInteger();
	private final PassiveReader<String, String> reader = new PassiveReader<>("test", "", () -> {
		reads.incrementAndGet();
		return raw.get();
	}, text -> text.toUpperCase(), guards::incrementAndGet);

	@Test
	void withEveryConsumerDisabledNothingIsReadOrParsed() {
		// AC-GS-16: all consumers of the reader disabled, then its parse counter stays at 0 over 600 ticks.
		AtomicBoolean first = new AtomicBoolean(false);
		AtomicBoolean second = new AtomicBoolean(false);
		reader.subscribe("first", first::get);
		reader.subscribe("second", second::get);
		for (int tick = 0; tick < 600; tick++) {
			reader.tick();
		}
		assertEquals(0, reader.parseCount());
		assertEquals(0, reads.get(), "not even read");
		assertEquals("", reader.snapshot());
		second.set(true);
		reader.tick();
		assertEquals(1, reader.parseCount());
		assertEquals("A", reader.snapshot());
	}

	@Test
	void withoutAnySubscriberNothingIsRead() {
		for (int tick = 0; tick < 600; tick++) {
			reader.tick();
		}
		assertEquals(0, reads.get());
		assertEquals(0, reader.parseCount());
	}

	@Test
	void anUnchangedSourceIsParsedOnce() {
		reader.subscribe("on", () -> true);
		for (int tick = 0; tick < 100; tick++) {
			reader.tick();
		}
		assertEquals(100, reads.get(), "read once per tick");
		assertEquals(1, reader.parseCount(), "parsed only when the raw input changed");
		raw.set("b");
		reader.tick();
		reader.tick();
		assertEquals(2, reader.parseCount());
		assertEquals("B", reader.snapshot());
	}

	@Test
	void listenersHearEachChangeOnce() {
		List<String> heard = new ArrayList<>();
		reader.subscribe("on", () -> true);
		reader.onChange(heard::add);
		reader.tick();
		reader.tick();
		raw.set("b");
		reader.tick();
		raw.set(null);
		reader.tick();
		reader.tick();
		assertEquals(List.of("A", "B", ""), heard, "a source with nothing to read gives the empty snapshot");
	}

	@Test
	void disablingTheLastConsumerDropsTheState() {
		// REQ-GS-16: with every consumer disabled the reader stores nothing either.
		AtomicBoolean on = new AtomicBoolean(true);
		reader.subscribe("on", on::get);
		reader.tick();
		assertEquals("A", reader.snapshot());
		on.set(false);
		reader.tick();
		assertEquals("", reader.snapshot());
		on.set(true);
		reader.tick();
		assertEquals("A", reader.snapshot());
		assertEquals(2, reader.parseCount(), "parsed again after the drop");
	}

	@Test
	void clearForgetsTheLastServer() {
		// REQ-GS-15: on disconnect, server switch or world change nothing from before is reported.
		List<String> heard = new ArrayList<>();
		reader.subscribe("on", () -> true);
		reader.onChange(heard::add);
		reader.tick();
		reader.clear();
		assertEquals("", reader.snapshot());
		reader.tick();
		assertEquals("A", reader.snapshot(), "read afresh, even if the raw input looks the same");
		assertEquals(List.of("A", "", "A"), heard);
	}

	@Test
	void everyEntryPointChecksTheThread() {
		reader.subscribe("on", () -> true);
		reader.tick();
		reader.clear();
		assertEquals(2, guards.get());
		PassiveReader<String, String> offThread = new PassiveReader<>("test", "", () -> "x", text -> text, () -> {
			throw new IllegalStateException("not the client thread");
		});
		assertThrows(IllegalStateException.class, offThread::tick);
		assertThrows(IllegalStateException.class, offThread::clear);
	}

	@Test
	void aParserFailureKeepsTheReaderAlive() {
		AtomicBoolean fail = new AtomicBoolean(true);
		PassiveReader<String, String> flaky = new PassiveReader<>("test", "", raw::get, text -> {
			if (fail.get()) {
				throw new IllegalArgumentException("odd shape");
			}
			return text;
		}, () -> { });
		flaky.subscribe("on", () -> true);
		flaky.tick();
		assertEquals("", flaky.snapshot(), "no snapshot from a failed parse");
		flaky.tick();
		assertEquals(1, flaky.parseCount(), "the same input is not parsed again");
		fail.set(false);
		raw.set("b");
		flaky.tick();
		assertEquals("b", flaky.snapshot());
	}

	@Test
	void theSameUnknownShapeIsLoggedOnce() {
		// AC-GS-10: 1,000 ticks of the same unknown widget line, then exactly one WARN.
		List<String> warnings = new ArrayList<>();
		ShapeLog shapes = new ShapeLog((kind, shape) -> warnings.add(kind + " " + shape));
		AtomicReference<String> line = new AtomicReference<>("Something new 12");
		PassiveReader<String, String> tab = new PassiveReader<>("tab", "", line::get, text -> {
			shapes.once("tab", text);
			return text;
		}, () -> { });
		tab.subscribe("on", () -> true);
		for (int tick = 0; tick < 1000; tick++) {
			tab.tick();
			tab.clear();
		}
		assertEquals(1, warnings.size(), warnings.toString());
		// The same shape with other numbers is the same shape; a new shape is logged once more.
		assertTrue(!shapes.once("tab", "Something new 13"));
		assertTrue(shapes.once("tab", "Something: else"));
		assertEquals(2, warnings.size());
	}

	@Test
	void theLoggedShapeHoldsNoWordsOrNumbers() {
		// REQ-GS-12: a WARN goes to latest.log on disk, so it carries the shape, never the raw text.
		List<String> warnings = new ArrayList<>();
		ShapeLog shapes = new ShapeLog((kind, shape) -> warnings.add(shape));
		shapes.once("tab", "[435] SecretName ✿ 2,346/3,000");
		assertEquals(List.of("[9] a ✿ 9,9/9,9"), warnings);
	}

	@Test
	void snapshotsAreHandedOutAsIs() {
		reader.subscribe("on", () -> true);
		reader.tick();
		assertSame(reader.snapshot(), reader.snapshot());
	}
}
