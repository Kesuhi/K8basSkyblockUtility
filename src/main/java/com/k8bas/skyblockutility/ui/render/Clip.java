package com.k8bas.skyblockutility.ui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * A clip region for a try-with-resources block (REQ-UI-02). A zero or negative size is clamped to an
 * empty region: everything drawn inside it is dropped, nothing is logged and nothing throws. Push and
 * pop always pair up, also when the region is empty.
 * <pre>{@code try (Clip clip = Clip.push(graphics, x, y, w, h)) { if (clip.visible()) { ... } }}</pre>
 */
public final class Clip implements AutoCloseable {
	private final GuiGraphicsExtractor graphics;
	private final ClipRect rect;
	private boolean closed;

	private Clip(GuiGraphicsExtractor graphics, ClipRect rect) {
		this.graphics = graphics;
		this.rect = rect;
	}

	public static Clip push(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		ClipRect rect = ClipRect.of(x, y, w, h);
		graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
		return new Clip(graphics, rect);
	}

	/** False when nothing drawn inside can show: this region, or the one it is nested in, is empty. */
	public boolean visible() {
		if (rect.isEmpty()) {
			return false;
		}
		ScreenRectangle top = graphics.scissorStack.peek();
		return top != null && top.width() > 0 && top.height() > 0;
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			graphics.disableScissor();
		}
	}
}
