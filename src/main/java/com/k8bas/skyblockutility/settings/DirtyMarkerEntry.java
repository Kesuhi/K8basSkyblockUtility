package com.k8bas.skyblockutility.settings;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * An invisible, zero-height entry whose only job is reporting isEdited() from an arbitrary
 * supplier. Cloth Config's "Save & Done" button is disabled unless the screen's own isEdited()
 * (which just OR's every top-level entry's isEdited()) is true — but Add/Delete rule buttons
 * mutate a working-copy list directly rather than going through any Cloth-tracked field, so
 * without this, Cloth Config has no way to know a pending add/delete exists and the Save button
 * stays greyed out even though there's something to save.
 */
public final class DirtyMarkerEntry extends AbstractConfigListEntry<Object> {
	private final BooleanSupplier dirty;

	public DirtyMarkerEntry(BooleanSupplier dirty) {
		super(Component.empty(), false);
		this.dirty = dirty;
	}

	@Override
	public boolean isEdited() {
		return dirty.getAsBoolean();
	}

	@Override
	public int getItemHeight() {
		return 0;
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
	public List<? extends GuiEventListener> children() {
		return Collections.emptyList();
	}

	@Override
	public List<? extends NarratableEntry> narratables() {
		return Collections.emptyList();
	}
}
