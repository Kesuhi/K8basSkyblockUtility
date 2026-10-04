package com.k8bas.skyblockutility.hud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the HUD editor changes, without drawing or input plumbing, so it is tested without a game
 * (REQ-HUD-05, REQ-HUD-08). Coordinates are GUI pixels on a {@code screenW}×{@code screenH} screen,
 * given with each call, so a resize keeps the selection and the unsaved changes (EC-HUD-01).
 */
public final class HudEditorModel {
	/** An element as the editor knows it. */
	public record Item(String id, HudPosition defaultPosition) {
	}

	/** An element's size at scale 1 now (its live content, else its preview). */
	public interface Sizes {
		double width(String id);

		double height(String id);
	}

	private final List<Item> items;
	private final Map<String, HudPosition> opened;
	private final Map<String, HudPosition> working;
	private final Sizes sizes;
	private String selected;
	private boolean dragging;
	/** Whether the current drag has moved the element; a click that only selects re-anchors nothing. */
	private boolean moved;
	/** Where the element was grabbed, from its top-left. */
	private double grabX;
	private double grabY;

	/** @param opened every item's position when the editor opened */
	public HudEditorModel(List<Item> items, Map<String, HudPosition> opened, Sizes sizes) {
		this.items = List.copyOf(items);
		this.opened = Map.copyOf(opened);
		this.working = new LinkedHashMap<>(opened);
		this.sizes = sizes;
	}

	public List<Item> items() {
		return items;
	}

	/** Where element {@code id} was when the editor opened. */
	public HudPosition opened(String id) {
		return opened.get(id);
	}

	/** The positions as edited, by id; changed in place by the editor. */
	public Map<String, HudPosition> positions() {
		return working;
	}

	public String selected() {
		return selected;
	}

	public void select(String id) {
		selected = id;
	}

	/** Where element {@code id} is drawn: its stored place on whole pixels, inside the window (REQ-HUD-04). */
	public HudRect rect(String id, double screenW, double screenH) {
		HudPosition position = working.get(id);
		return HudRect.onPixels(position.place(screenW, screenH, sizes.width(id) * position.scale(), sizes.height(id) * position.scale()), screenW, screenH);
	}

	/** The topmost element under the point (the one registered last, drawn on top: EC-HUD-03), or null. */
	public String at(double x, double y, double screenW, double screenH) {
		for (int i = items.size() - 1; i >= 0; i--) {
			HudRect rect = rect(items.get(i).id(), screenW, screenH);
			if (x >= rect.x() && y >= rect.y() && x < rect.x() + rect.w() && y < rect.y() + rect.h()) {
				return items.get(i).id();
			}
		}
		return null;
	}

	/** Selects the element under the point and grabs it where it was pressed, or deselects on empty space. */
	public void press(double x, double y, double screenW, double screenH) {
		selected = at(x, y, screenW, screenH);
		dragging = selected != null;
		moved = false;
		if (dragging) {
			HudRect rect = rect(selected, screenW, screenH);
			grabX = x - rect.x();
			grabY = y - rect.y();
		}
	}

	/** Moves the grabbed element with the mouse, inside the window. */
	public void drag(double x, double y, double screenW, double screenH) {
		if (dragging) {
			HudPosition before = working.get(selected);
			HudRect drawn = rect(selected, screenW, screenH);
			moveTo(selected, x - grabX, y - grabY, screenW, screenH);
			if (rect(selected, screenW, screenH).equals(drawn)) {
				// Not moved on screen: the stored place stays as it was (a clamped one included).
				working.put(selected, before);
			} else {
				moved = true;
			}
		}
	}

	/** Ends a drag that moved the element: anchored to the screen third of its centre, where it is (REQ-HUD-03). */
	public void release(double screenW, double screenH) {
		if (dragging && moved) {
			reanchor(selected, screenW, screenH);
		}
		dragging = false;
		moved = false;
	}

	/** Scales the element under the mouse, else the selected one, in steps of 0.1 (REQ-HUD-05); false if there is none. */
	public boolean scroll(double x, double y, int notches, double screenW, double screenH) {
		String target = at(x, y, screenW, screenH);
		if (target == null) {
			target = selected;
		}
		if (target == null) {
			return false;
		}
		HudPosition position = working.get(target);
		working.put(target, position.withScale(HudScale.step(position.scale(), notches)));
		return true;
	}

	/** Moves the selected element by ({@code dx}, {@code dy}) pixels, inside the window. */
	public void nudge(int dx, int dy, double screenW, double screenH) {
		if (selected == null || dragging) {
			return;
		}
		HudRect rect = rect(selected, screenW, screenH);
		moveTo(selected, rect.x() + dx, rect.y() + dy, screenW, screenH);
		reanchor(selected, screenW, screenH);
	}

	public void resetSelected() {
		if (selected != null) {
			working.put(selected, item(selected).defaultPosition());
		}
	}

	public void resetAll() {
		items.forEach(item -> working.put(item.id(), item.defaultPosition()));
	}

	/** Every element back where it was when the editor opened. */
	public void cancel() {
		dragging = false;
		working.clear();
		working.putAll(opened);
	}

	private void moveTo(String id, double left, double top, double screenW, double screenH) {
		HudPosition position = working.get(id);
		double w = sizes.width(id) * position.scale();
		double h = sizes.height(id) * position.scale();
		double x = Math.max(0, Math.min(left, screenW - w));
		double y = Math.max(0, Math.min(top, screenH - h));
		working.put(id, position.movedTo(x, y, screenW, screenH, w, h));
	}

	private void reanchor(String id, double screenW, double screenH) {
		HudPosition position = working.get(id);
		working.put(id, position.reanchored(screenW, screenH, sizes.width(id) * position.scale(), sizes.height(id) * position.scale()));
	}

	private Item item(String id) {
		for (Item item : items) {
			if (item.id().equals(id)) {
				return item;
			}
		}
		throw new IllegalArgumentException(id);
	}
}
