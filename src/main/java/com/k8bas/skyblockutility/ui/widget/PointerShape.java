package com.k8bas.skyblockutility.ui.widget;

/**
 * The pointer shape a widget asks for (REQ-UI-43, T3.33). Kept apart from the game's cursor types, so the widgets' choices
 * are unit-testable; {@link WidgetScreen} asks the game for the matching one each frame, so a shape never outlives its
 * screen (EC-UI-27).
 */
public enum PointerShape {
	/** The arrow: empty space, text, a disabled control. */
	DEFAULT,
	/** Over something a click does something to. */
	HAND,
	/** The I-beam, over a text field. */
	TEXT,
	/** While a slider is dragged. */
	RESIZE_HORIZONTAL,
	/** While a scrollbar's thumb is dragged. */
	RESIZE_VERTICAL,
	/** Over, and while dragging, an element in the HUD editor. */
	MOVE,
	/** Over, and while dragging, a corner handle in the HUD editor (26.2 has no diagonal-resize pointer, REQ-HUD-15). */
	CROSSHAIR
}
