package com.k8bas.skyblockutility.settings;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A text field that reports its value on every keystroke, unlike Cloth Config's Str/Text field
 * entries which only fire their save consumer when the whole screen is saved. Modeled directly
 * on Cloth Config's own internal SearchFieldEntry (pulled from its v26.1 branch source), which
 * isn't reusable as-is: it's package-private to the library and wired directly into
 * ClothConfigScreen's own list filtering, not exposed for a mod's own live-update logic.
 *
 * Used by the mob database picker to expand island/event folders as the user types, since Cloth
 * Config's built-in search box (confirmed via its real source) filters which top-level entries
 * are shown but never auto-expands a collapsed SubCategory to reveal a match inside it.
 */
public final class LiveTextFieldEntry extends AbstractConfigListEntry<String> {
	private final EditBox editBox;

	public LiveTextFieldEntry(Component fieldName, String suggestion, Consumer<String> onChange) {
		super(fieldName, false);
		this.editBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 18, Component.empty());
		this.editBox.setResponder(onChange);
		if (suggestion != null) {
			this.editBox.setSuggestion(suggestion);
		}
	}

	@Override
	public Iterator<String> getSearchTags() {
		return Collections.emptyIterator();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth, int entryHeight,
			int mouseX, int mouseY, boolean isHovered, float delta) {
		this.editBox.setWidth(Mth.clamp(entryWidth - 10, 0, 500));
		this.editBox.setX(x + entryWidth / 2 - this.editBox.getWidth() / 2);
		this.editBox.setY(y + entryHeight / 2 - 9);
		this.editBox.extractRenderState(graphics, mouseX, mouseY, delta);
		super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
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
