package com.k8bas.skyblockutility.ui.notice;

import java.util.List;
import java.util.Objects;

/**
 * A notice (toast, REQ-UI-21): a title and up to 2 body lines. It only informs: it may name a screen
 * to open, but takes no input and never starts an action itself.
 */
public record Notice(String title, List<String> body) {
	public static final int MAX_BODY_LINES = 2;

	public Notice {
		Objects.requireNonNull(title, "title");
		if (title.isBlank()) {
			throw new IllegalArgumentException("a notice needs a title");
		}
		body = List.copyOf(body);
		if (body.size() > MAX_BODY_LINES) {
			throw new IllegalArgumentException("a notice has at most " + MAX_BODY_LINES + " body lines: " + body);
		}
	}
}
