package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.option.Binding;
import com.k8bas.skyblockutility.ui.render.Animated;
import com.k8bas.skyblockutility.ui.render.ColorMath;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiSound;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.LongSupplier;

/** An on/off switch whose knob slides in 150 ms (REQ-UI-06, REQ-UI-17). A click flips the binding at once (live apply). */
public final class ToggleSwitch extends Widget {
	public static final int WIDTH = 22;
	public static final int HEIGHT = 12;

	private final Binding<Boolean> binding;
	private final Runnable clickSound;
	private final Animated knob;

	public ToggleSwitch(Binding<Boolean> binding, Runnable clickSound, LongSupplier clock) {
		this.binding = binding;
		this.clickSound = clickSound;
		this.knob = new Animated(binding.get() ? 1F : 0F, Animated.DEFAULT_DURATION_MS, clock);
	}

	/** A switch with the click sound, on the game's clock. */
	public static ToggleSwitch of(Binding<Boolean> binding) {
		return new ToggleSwitch(binding, UiSound::click, UiClock.MILLIS);
	}

	public boolean value() {
		return binding.get();
	}

	public void toggle() {
		boolean value = !binding.get();
		binding.set(value);
		knob.animateTo(value ? 1F : 0F);
		clickSound.run();
	}

	@Override
	public boolean press(double mouseX, double mouseY, int button) {
		if (!enabled || button != 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		toggle();
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		// A change from elsewhere (a keybind, a reset) slides the knob too.
		knob.animateTo(binding.get() ? 1F : 0F);
		Theme theme = Theme.current();
		float t = knob.value();
		int track = enabled ? ColorMath.lerp(Theme.SEPARATOR, theme.accent(), t) : Theme.CARD_HOVER;
		int knobColor = enabled ? ColorMath.lerp(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, t) : Theme.TEXT_DISABLED;
		Shapes.roundedRect(graphics, x, y, width, height, height / 2, faded(track));
		int knobSize = height - 4;
		int knobX = x + 2 + Math.round((width - 4 - knobSize) * t);
		Shapes.roundedRect(graphics, knobX, y + 2, knobSize, knobSize, knobSize / 2, faded(knobColor));
	}
}
