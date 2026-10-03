package com.k8bas.skyblockutility.ui.option;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Where an option's value lives. {@link #set} stores the value and applies it at once (live apply,
 * REQ-UI-15). Writing the file is the screen's job (on discrete commits and once per close,
 * SaveSession), though a module switch also saves at once, as its keybind does.
 */
public interface Binding<T> {
	T get();

	void set(T value);

	static <T> Binding<T> of(Supplier<T> getter, Consumer<T> setter) {
		Objects.requireNonNull(getter, "getter");
		Objects.requireNonNull(setter, "setter");
		return new Binding<>() {
			@Override
			public T get() {
				return getter.get();
			}

			@Override
			public void set(T value) {
				setter.accept(value);
			}
		};
	}
}
