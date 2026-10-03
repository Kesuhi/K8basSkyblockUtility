package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.Clip;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/**
 * A single-line text field over a {@link TextEditModel} (REQ-UI-06): caret, selection, horizontal
 * scrolling that keeps the caret in view and the field filled, and a placeholder while empty. While
 * focused it takes every key (REQ-UI-22, through {@link WidgetScreen}).
 */
public final class TextField extends Widget {
	static final int PADDING = 4;
	private static final long BLINK_MS = 300;

	private final TextEditModel model;
	private final String placeholder;
	private final Consumer<String> onChange;
	private final LongSupplier clock;
	private ToIntFunction<String> widths;
	/** The index of the first visible character; checked against the text before every use. */
	private int scroll;
	private long focusedAt;
	private boolean selectingWithMouse;
	private boolean forcedHot;
	private boolean selectAllOnFocus;
	private BooleanSupplier invalid = () -> false;

	public TextField(TextEditModel model, String placeholder, Consumer<String> onChange, LongSupplier clock) {
		this.model = model;
		this.placeholder = placeholder;
		this.onChange = onChange;
		this.clock = clock;
	}

	public static TextField of(TextEditModel model, String placeholder, Consumer<String> onChange) {
		return new TextField(model, placeholder, onChange, UiClock.MILLIS);
	}

	public TextEditModel model() {
		return model;
	}

	/** Draws the hovered outline regardless of the mouse, for screenshots of every state. */
	public void forceHot(boolean hot) {
		this.forcedHot = hot;
	}

	/** Marks the field in the error colour while the condition holds (e.g. an invalid hex colour). */
	public void markInvalidWhen(BooleanSupplier condition) {
		this.invalid = condition;
	}

	/** The text's width measure; set from the font on each draw, or by tests. */
	void useWidths(ToIntFunction<String> widths) {
		this.widths = widths;
	}

	int scroll() {
		return scroll;
	}

	int innerWidth() {
		return width - 2 * PADDING;
	}

	@Override
	public boolean wantsKeyboard() {
		return true;
	}

	/** Selects the whole text when the field gains focus (a hex field: a paste then replaces it). */
	public void selectAllOnFocus(boolean selectAll) {
		this.selectAllOnFocus = selectAll;
	}

	@Override
	public void setFocused(boolean focused) {
		if (focused && !this.focused) {
			focusedAt = clock.getAsLong();
			if (selectAllOnFocus) {
				model.selectAll();
				selectingWithMouse = false;
			}
		}
		super.setFocused(focused);
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		model.moveTo(indexAt(mouseX), false);
		selectingWithMouse = true;
		focusedAt = clock.getAsLong();
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		if (!selectingWithMouse) {
			return;
		}
		if (mouseX < x + PADDING && validScroll() > 0) {
			// Past the left edge: select one character further left; the view follows.
			String text = model.text();
			model.moveTo(text.offsetByCodePoints(validScroll(), -1), true);
		} else {
			model.moveTo(indexAt(mouseX), true);
		}
	}

	@Override
	public void release(double mouseX, double mouseY) {
		selectingWithMouse = false;
	}

	@Override
	public boolean key(int key, boolean ctrl, boolean shift) {
		String before = model.text();
		boolean used = model.key(key, ctrl, shift);
		changed(before);
		focusedAt = clock.getAsLong();
		return used;
	}

	@Override
	public boolean typed(int codePoint) {
		String before = model.text();
		boolean used = model.typed(codePoint);
		changed(before);
		focusedAt = clock.getAsLong();
		return used;
	}

	private void changed(String before) {
		if (!model.text().equals(before)) {
			onChange.accept(model.text());
		}
	}

	/** The scroll, held inside the current text (an edit may have shortened it since the last draw). */
	private int validScroll() {
		String text = model.text();
		int clamped = Math.max(0, Math.min(scroll, text.length()));
		if (clamped > 0 && clamped < text.length() && Character.isLowSurrogate(text.charAt(clamped))) {
			clamped--;
		}
		scroll = clamped;
		return clamped;
	}

	/** The caret index nearest to a mouse x, by the widths of the visible prefixes. */
	int indexAt(double mouseX) {
		String text = model.text();
		int start = validScroll();
		if (widths == null) {
			return text.length();
		}
		double target = mouseX - (x + PADDING);
		int best = start;
		double bestDistance = Double.MAX_VALUE;
		int i = start;
		while (true) {
			double distance = Math.abs(widths.applyAsInt(text.substring(start, i)) - target);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
			if (i >= text.length()) {
				return best;
			}
			i = text.offsetByCodePoints(i, 1);
		}
	}

	/** Scrolls so the caret is in view, and back while the rest of the text still fits, so the field stays filled. */
	void keepCaretVisible() {
		if (widths == null) {
			return;
		}
		String text = model.text();
		int caret = model.caret();
		int inner = innerWidth();
		int start = Math.min(validScroll(), caret);
		while (start < caret && widths.applyAsInt(text.substring(start, caret)) > inner - 1) {
			start = text.offsetByCodePoints(start, 1);
		}
		while (start > 0 && widths.applyAsInt(text.substring(text.offsetByCodePoints(start, -1))) <= inner - 1) {
			start = text.offsetByCodePoints(start, -1);
		}
		scroll = start;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		widths = font::width;
		Theme theme = Theme.current();
		boolean wrong = invalid.getAsBoolean();
		Shapes.roundedRect(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, faded(wrong ? Theme.ERROR_BACKGROUND : Theme.SIDEBAR));
		boolean hot = enabled && (forcedHot || contains(mouseX, mouseY));
		float hover = forcedHot ? 1F : hoverAmount(hot);
		int outline = !enabled ? Theme.CARD_HOVER : wrong ? Theme.ERROR : focused ? theme.accent()
				: ColorMath.lerp(Theme.SEPARATOR, Theme.TEXT_DISABLED, hover);
		Shapes.roundedOutline(graphics, x, y, width, height, Shapes.RADIUS_CONTROL, 1, faded(outline));
		String text = model.text();
		keepCaretVisible();
		int textY = y + (height - font.lineHeight) / 2 + 1;
		try (Clip clip = Clip.push(graphics, x + PADDING, y + 1, innerWidth(), height - 2)) {
			if (!clip.visible()) {
				return;
			}
			if (text.isEmpty() && !focused) {
				UiText.draw(graphics, font, placeholder, x + PADDING, textY, faded(Theme.TEXT_DISABLED));
				return;
			}
			int originX = x + PADDING - font.width(text.substring(0, scroll));
			if (model.hasSelection()) {
				int from = originX + font.width(text.substring(0, model.selectionStart()));
				int to = originX + font.width(text.substring(0, model.selectionEnd()));
				graphics.fill(from, textY - 1, to, textY + font.lineHeight, theme.accentBackground());
			}
			UiText.draw(graphics, font, text, originX, textY, faded(enabled ? Theme.TEXT_PRIMARY : Theme.TEXT_DISABLED));
			if (focused && ((clock.getAsLong() - focusedAt) / BLINK_MS) % 2 == 0) {
				int caretX = originX + font.width(text.substring(0, model.caret()));
				graphics.fill(caretX, textY - 1, caretX + 1, textY + font.lineHeight - 1, Theme.TEXT_PRIMARY);
			}
		}
	}
}
