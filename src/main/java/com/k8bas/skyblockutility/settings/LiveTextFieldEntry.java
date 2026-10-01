package com.k8bas.skyblockutility.settings;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A text field that reports its value on every keystroke, unlike Cloth Config's Str/Text field
 * entries which only fire their save consumer when the whole screen is saved. It uses Cloth Config
 * only through its public entry API (a library, LGPL-3.0); the layout is this mod's own: a field
 * as wide as the row (at most MAX_WIDTH), centred, with vanilla's own hint text while empty.
 *
 * Used by the mob database picker to expand island/event folders as the user types, since Cloth
 * Config's built-in search box (confirmed via its real source) filters which top-level entries
 * are shown but never auto-expands a collapsed SubCategory to reveal a match inside it.
 */
public final class LiveTextFieldEntry extends AbstractConfigListEntry<String> {
	private static final int MAX_WIDTH = 400;
	private static final int MARGIN = 4;
	private static final int HEIGHT = 18;

	private final EditBox editBox;

	public LiveTextFieldEntry(Component fieldName, String placeholder, Consumer<String> onChange) {
		super(fieldName, false);
		this.editBox = new EditBox(Minecraft.getInstance().font, 0, 0, MAX_WIDTH, HEIGHT, fieldName);
		this.editBox.setHint(Component.literal(placeholder).withStyle(EditBox.SEARCH_HINT_STYLE));
		this.editBox.setResponder(onChange);
	}

	@Override
	public Iterator<String> getSearchTags() {
		return Collections.emptyIterator();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth, int entryHeight,
			int mouseX, int mouseY, boolean isHovered, float delta) {
		int width = Math.max(0, Math.min(MAX_WIDTH, entryWidth - 2 * MARGIN));
		editBox.setWidth(width);
		editBox.setPosition(x + (entryWidth - width) / 2, y + (Math.max(HEIGHT, entryHeight) - HEIGHT) / 2);
		editBox.extractRenderState(graphics, mouseX, mouseY, delta);
		super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
	}

	/** Types a value as the player would (used by the client gametests). */
	public void typeForTest(String value) {
		editBox.setValue(value);
	}

	@Override
	public String getValue() {
		return editBox.getValue();
	}

	@Override
	public Optional<String> getDefaultValue() {
		return Optional.empty();
	}

	@Override
	public List<? extends NarratableEntry> narratables() {
		return Collections.singletonList(editBox);
	}

	@Override
	public List<? extends GuiEventListener> children() {
		return Collections.singletonList(editBox);
	}
}
