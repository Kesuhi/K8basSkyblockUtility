package com.k8bas.skyblockutility.ui.widget;

/** Opens and closes the one overlay of a screen (a dropdown list, a modal). */
public interface OverlayHost {
	/** Opens an overlay, cancelling one already open. */
	void open(Overlay overlay);

	/** Closes this overlay if it is the open one. */
	void close(Overlay overlay);
}
