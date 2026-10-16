package com.k8bas.skyblockutility.ui.widget;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * AC-UI-44 [C] (REQ-UI-43, T3.33), for gametests only: puts the pointer on a GUI point and reads, two frames later, the shape
 * the open screen asked for and the cursor the game's window then shows. The window's cursor is compared with the game's own
 * cursor types written out here, not with the screen's mapping, so a wrong mapping fails too.
 */
public final class PointerProbe {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");

	private PointerProbe() {
	}

	/** The game's cursor for a shape, as REQ-UI-43 names it. */
	public static CursorType expected(PointerShape shape) {
		return switch (shape) {
			case DEFAULT -> CursorType.DEFAULT;
			case HAND -> CursorTypes.POINTING_HAND;
			case TEXT -> CursorTypes.IBEAM;
			case RESIZE_HORIZONTAL -> CursorTypes.RESIZE_EW;
			case RESIZE_VERTICAL -> CursorTypes.RESIZE_NS;
			case MOVE -> CursorTypes.RESIZE_ALL;
		};
	}

	/** Puts the pointer on the middle of GUI pixel ({@code guiX}, {@code guiY}) and checks the shape there. */
	public static void expect(ClientGameTestContext context, double guiX, double guiY, PointerShape shape, String what) {
		move(context, guiX, guiY);
		check(context, shape, what);
	}

	/** Puts the pointer on the middle of GUI pixel ({@code guiX}, {@code guiY}), two frames before returning. */
	public static void move(ClientGameTestContext context, double guiX, double guiY) {
		double[] at = context.computeOnClient(client -> {
			Window window = client.getWindow();
			return new double[] {(Math.floor(guiX) + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth(),
					(Math.floor(guiY) + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight()};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
	}

	/** Checks the shape the open screen asked for in its last frame and the cursor the window shows. */
	public static void check(ClientGameTestContext context, PointerShape shape, String what) {
		PointerShape requested = context.computeOnClient(client -> client.gui.screen() instanceof WidgetScreen screen ? screen.requestedPointer() : null);
		CursorType shown = windowCursor(context);
		LOGGER.info("pointer over {}: asked for {}, the window shows {}", what, requested, shown);
		if (requested != shape || shown != expected(shape)) {
			throw new AssertionError("FAILED: " + what + ": the screen asked for " + requested + " and the window shows " + shown + ", not " + shape);
		}
	}

	/** The cursor the game's window shows now: what it last selected (it selects the default while cursor changes are off). */
	public static CursorType windowCursor(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			Window window = client.getWindow();
			if (!(boolean) read(window, "allowCursorChanges")) {
				throw new AssertionError("FAILED: the game's Allow Cursor Changes option is off, so no shape can show");
			}
			return (CursorType) read(window, "currentCursor");
		});
	}

	private static Object read(Window window, String name) {
		try {
			Field field = Window.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(window);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("FAILED: the game's window has no readable " + name + " (checked with javap on 26.2)", e);
		}
	}
}
