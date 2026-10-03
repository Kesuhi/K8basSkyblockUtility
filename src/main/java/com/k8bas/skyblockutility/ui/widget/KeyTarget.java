package com.k8bas.skyblockutility.ui.widget;

/** A key mapping a capture widget edits, by options.txt key names ("key.keyboard.f7", "key.mouse.4"). */
public interface KeyTarget {
	String mapping();

	String bound();

	String defaultKey();

	/** Binds and saves at once (a discrete commit, REQ-UI-15). */
	void bind(String keyName);
}
