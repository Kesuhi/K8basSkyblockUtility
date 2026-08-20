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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A hex color text field with a live color swatch drawn right next to it, combining what used to
 * be two separate rows ("Color (hex RRGGBB)" and a Cloth-provided "Color Preview" color field)
 * into one. The swatch updates as valid hex is typed, purely visual — the actual rule.color
 * mutation still only happens on screen Save (via the saveConsumer passed in), matching every
 * other field in the rule editor so Cancel still discards it like everything else.
 */
public final class HexColorFieldEntry extends AbstractConfigListEntry<String> {
	private final EditBox editBox;
	private final Consumer<String> saveConsumer;
	private int previewColor;

	public HexColorFieldEntry(Component fieldName, String initialHex, int initialColor, Consumer<String> saveConsumer) {
		super(fieldName, false);
		this.previewColor = 0xFF000000 | initialColor;
		this.saveConsumer = saveConsumer;
		this.editBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 18, Component.empty());
		this.editBox.setValue(initialHex);
		this.editBox.setResponder(value -> {
			if (value.matches("[0-9A-Fa-f]{6}")) {
				this.previewColor = 0xFF000000 | Integer.parseInt(value, 16);
			}
		});
	}

	@Override
	public void save() {
		saveConsumer.accept(editBox.getValue());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth, int entryHeight,
			int mouseX, int mouseY, boolean isHovered, float delta) {
		int swatchSize = 14;
		int swatchX = x;
		int swatchY = y + (entryHeight - swatchSize) / 2;
		graphics.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, 0xFFFFFFFF);
		graphics.fill(swatchX + 1, swatchY + 1, swatchX + swatchSize - 1, swatchY + swatchSize - 1, previewColor);

		this.editBox.setWidth(Mth.clamp(entryWidth - swatchSize - 14, 60, 200));
		this.editBox.setX(swatchX + swatchSize + 6);
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
