package com.k8bas.skyblockutility.settings;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.Window;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
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
 * A genuinely clickable action button for a Cloth Config screen. Cloth Config has no built-in
 * button entry type (checked directly against the jar) — every other entry is bound to the
 * screen's save cycle. This hosts a real vanilla Button whose action fires immediately on
 * click, modeled directly on Cloth Config's own BooleanListEntry (pulled from its v26.1 branch
 * source, matching the exact Cloth Config version this mod depends on) rather than guessed from
 * scratch, since the newer split-render extractRenderState(GuiGraphicsExtractor, ...) API isn't
 * otherwise documented.
 */
public class ButtonEntry extends TooltipListEntry<Object> {
	private final Button buttonWidget;
	private final List<AbstractWidget> widgets;

	public ButtonEntry(Component fieldName, Component buttonLabel, Runnable action) {
		super(fieldName, Optional::empty);
		this.buttonWidget = Button.builder(buttonLabel, widget -> action.run()).bounds(0, 0, 150, 20).build();
		this.widgets = Lists.newArrayList(buttonWidget);
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
		Window window = Minecraft.getInstance().getWindow();
		this.buttonWidget.active = isEditable();
		this.buttonWidget.setY(y);
		Component displayedFieldName = getDisplayedFieldName();
		if (Minecraft.getInstance().font.isBidirectional()) {
			graphics.text(Minecraft.getInstance().font, displayedFieldName,
					window.getGuiScaledWidth() - x - Minecraft.getInstance().font.width(displayedFieldName), y + 6, 0xffffffff);
			this.buttonWidget.setX(x);
		} else {
			graphics.text(Minecraft.getInstance().font, displayedFieldName, x, y + 6, getPreferredTextColor());
			this.buttonWidget.setX(x + entryWidth - 150);
		}
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
