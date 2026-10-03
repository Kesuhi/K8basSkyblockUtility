package com.k8bas.skyblockutility.ui.notice;

/** Where notices appear (REQ-UI-21): a corner or the top centre. */
public enum NoticePosition {
	TOP_LEFT("Top left"),
	TOP_CENTRE("Top centre"),
	TOP_RIGHT("Top right"),
	BOTTOM_LEFT("Bottom left"),
	BOTTOM_RIGHT("Bottom right");

	private final String label;

	NoticePosition(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	boolean top() {
		return this == TOP_LEFT || this == TOP_CENTRE || this == TOP_RIGHT;
	}

	boolean left() {
		return this == TOP_LEFT || this == BOTTOM_LEFT;
	}
}
