package com.k8bas.skyblockutility.settings;

import com.google.common.collect.Lists;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * A clickable action button for a Cloth Config screen. Cloth Config has no button entry type —
 * every other entry is bound to the screen's save cycle. This hosts a vanilla Button whose action
 * fires immediately on click. It uses Cloth Config only through its public entry API (a library,
 * LGPL-3.0); the layout below is this mod's own: the label on the left, the button at the right
 * edge, both centred in the row, mirrored for right-to-left languages.
 */
public class ButtonEntry extends TooltipListEntry<Object> {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;

	private final Button buttonWidget;
	private final List<AbstractWidget> widgets;

	private final Runnable action;

	public ButtonEntry(Component fieldName, Component buttonLabel, Runnable action) {
		super(fieldName, Optional::empty);
		this.action = action;
		this.buttonWidget = Button.builder(buttonLabel, widget -> action.run()).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		this.widgets = Lists.newArrayList(buttonWidget);
	}

	/** Runs the button's action as a click would (used by the client gametests). */
	public void press() {
		action.run();
	}

	/**
	 * Action buttons aren't a "value" someone searches for by name — an unrelated search query
	 * (e.g. typing a mob name in the Mob Database picker) shouldn't be able to hide the Return
	 * button or an "Add rule" button. Cloth Config's search (confirmed via its real source)
	 * treats an entry with zero search tags as an automatic, unconditional match, so this simply
	 * opts every button out of filtering entirely.
	 */
	@Override
	public Iterator<String> getSearchTags() {
		return Collections.emptyIterator();
	}

	@Override
	public Object getValue() {
		return null;
	}

	@Override
	public Optional<Object> getDefaultValue() {
		return Optional.empty();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth, int entryHeight,
			int mouseX, int mouseY, boolean isHovered, float delta) {
		super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
		Font font = Minecraft.getInstance().font;
		Component label = getDisplayedFieldName();
		int rowMiddle = y + Math.max(BUTTON_HEIGHT, entryHeight) / 2;
		int labelY = rowMiddle - font.lineHeight / 2;
		boolean rightToLeft = font.isBidirectional();
		int labelX = rightToLeft ? x + entryWidth - font.width(label) : x;
		graphics.text(font, label, labelX, labelY, getPreferredTextColor());

		buttonWidget.active = isEditable();
		buttonWidget.setPosition(rightToLeft ? x : x + entryWidth - BUTTON_WIDTH, rowMiddle - BUTTON_HEIGHT / 2);
		buttonWidget.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public List<? extends GuiEventListener> children() {
		return widgets;
	}

	@Override
	public List<? extends NarratableEntry> narratables() {
		return widgets;
	}
}
