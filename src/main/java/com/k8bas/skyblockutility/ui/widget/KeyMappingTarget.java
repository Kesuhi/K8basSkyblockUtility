package com.k8bas.skyblockutility.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** A game key mapping as a {@link KeyTarget}: binding it updates the game and writes options.txt at once (REQ-UI-14). */
public final class KeyMappingTarget implements KeyTarget {
	private final KeyMapping mapping;

	public KeyMappingTarget(KeyMapping mapping) {
		this.mapping = mapping;
	}

	@Override
	public String mapping() {
		return mapping.getName();
	}

	@Override
	public String bound() {
		return mapping.saveString();
	}

	@Override
	public String defaultKey() {
		return mapping.getDefaultKey().getName();
	}

	@Override
	public void bind(String keyName) {
		mapping.setKey(InputConstants.getKey(keyName));
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	/** Every mapping of the game, for the conflict check. */
	public static List<KeyConflicts.KeyState> allKeys() {
		List<KeyConflicts.KeyState> all = new ArrayList<>();
		for (KeyMapping key : Minecraft.getInstance().options.keyMappings) {
			all.add(new KeyConflicts.KeyState(key.getName(), key.saveString(), key.getDefaultKey().getName()));
		}
		return all;
	}

	/** A key's name as shown, e.g. "F7". */
	public static String displayName(String keyName) {
		return InputConstants.getKey(keyName).getDisplayName().getString();
	}

	/** A mouse button's options.txt name. */
	public static String mouseKeyName(int button) {
		return InputConstants.Type.MOUSE.getOrCreate(button).getName();
	}
}
