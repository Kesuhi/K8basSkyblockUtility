package com.k8bas.skyblockutility.highlight;

import net.azureaaron.renderchest.api.GlowConstants;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GlowHandlerTest {
	/** AC-GLOW-04 (T1.10): glow the server applies is never recoloured. */
	@Test
	void serverGlowIsLeftAlone() {
		assertEquals(GlowConstants.NO_GLOW, GlowHandler.colourFor(true, ARGB.opaque(0xFF00FF)));
		assertEquals(GlowConstants.NO_GLOW, GlowHandler.colourFor(true, 0));
	}

	@Test
	void theCachedColourIsPassedOn() {
		assertEquals(0xFFFF00FF, GlowHandler.colourFor(false, ARGB.opaque(0xFF00FF)));
		assertEquals(GlowConstants.NO_GLOW, GlowHandler.colourFor(false, 0));
	}

	/** AC-GLOW-17 [A]: a black rule colour glows black instead of meaning "no glow". */
	@Test
	void blackIsAColour() {
		int black = GlowHandler.colourFor(false, ARGB.opaque(0x000000));
		assertNotEquals(GlowConstants.NO_GLOW, black);
		assertNotEquals(GlowConstants.REMOVE_GLOW, black);
		assertEquals(0xFF000000, black);
	}

	interface Listener {
		void call();
	}

	static final class First implements Listener {
		@Override
		public void call() {
		}
	}

	static final class Second implements Listener {
		@Override
		public void call() {
		}
	}

	/** REQ-GLOW-07: the log line names the providers in the order they are asked. */
	@Test
	void providersAreListedInCallOrder() {
		Event<Listener> event = EventFactory.createArrayBacked(Listener.class, listeners -> () -> {
			for (Listener listener : listeners) {
				listener.call();
			}
		});
		event.register(new Second());
		event.register(new First());
		assertEquals("[" + Second.class.getName() + ", " + First.class.getName() + "]", GlowHandler.providers(event));
	}
}
