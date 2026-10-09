package com.k8bas.skyblockutility.settings;

import com.k8bas.skyblockutility.ui.option.ActionOption;
import com.k8bas.skyblockutility.ui.option.Card;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.OptionText;
import com.k8bas.skyblockutility.ui.screen.HudEditorScreen;

import java.util.List;

/** The HUD editor's entry points in the settings screen (REQ-HUD-06): General › HUD, and a button on each HUD feature's card. */
public final class HudOptions {
	private HudOptions() {
	}

	/** General › HUD with "Edit HUD layout" (REQ-UI-04). */
	static Card layoutCard(Runnable editLayout) {
		return new Card("hud", Category.GENERAL, "HUD", null, List.of(new ActionOption("hud.edit_layout",
				new OptionText("Edit HUD layout", "Move and scale the mod's on-screen HUD elements.",
						"Opens the HUD editor: drag an element to move it, scroll over it to scale it, and press Esc or Save when done.",
						List.of("hud", "layout", "position", "move", "scale", "overlay")),
				"Edit HUD layout", false, editLayout)));
	}

	/**
	 * "Edit position" for the card of a feature that owns HUD element {@code elementId}: opens the editor with
	 * that element selected. Closing the editor returns to the settings screen.
	 */
	public static ActionOption editPosition(String cardId, String elementId) {
		return new ActionOption(cardId + ".edit_position",
				new OptionText("Edit position", "Move and scale this feature's HUD element.", "", List.of("hud", "position", "move", "scale")),
				"Edit position", false, () -> HudEditorScreen.openEditor(elementId));
	}
}
