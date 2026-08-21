package com.k8bas.skyblockutility.settings;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Color;

/**
 * A single shared hue/saturation wheel texture, reused by every ColorWheelWidget instead of each
 * owning its own — the wheel's pixel content only depends on the "Value" (brightness) component
 * of HSB, and rule colors overwhelmingly default to full brightness, so sharing one texture
 * (regenerated only when the requested brightness actually differs from what's currently baked
 * in) avoids creating and leaking a GPU texture every time a rule's color field is built, with no
 * meaningful visual downside: the selected hue/saturation itself is drawn as a puck on top by the
 * widget, not baked into this texture, so two simultaneously-open wheels at different brightness
 * only ever disagree on background shading, never on where their own puck sits.
 *
 * Built with java.awt.Color's static HSBtoRGB — used purely as a battle-tested math function
 * here (no AWT window/toolkit involved), rather than hand-rolling HSV-to-RGB conversion.
 */
final class ColorWheelTexture {
	static final int SIZE = 90;
	static final Identifier ID = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "dynamic/color_wheel");

	private static DynamicTexture texture;
	private static float bakedValue = -1;

	private ColorWheelTexture() {
	}

	static void ensureValue(float value) {
		if (texture == null) {
			texture = new DynamicTexture(() -> "k8bas color wheel", buildImage(value));
			Minecraft.getInstance().getTextureManager().register(ID, texture);
			bakedValue = value;
			return;
		}
		if (Math.abs(bakedValue - value) < 0.001f) {
			return;
		}
		texture.setPixels(buildImage(value));
		texture.upload();
		bakedValue = value;
	}

	private static NativeImage buildImage(float value) {
		NativeImage image = new NativeImage(SIZE, SIZE, false);
		float radius = SIZE / 2F;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				float dx = x + 0.5F - radius;
				float dy = y + 0.5F - radius;
				float distance = (float) Math.sqrt(dx * dx + dy * dy) / radius;
				if (distance > 1F) {
					image.setPixelABGR(x, y, 0);
					continue;
				}
				float hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2)) + 0.5F;
				int rgb = Color.HSBtoRGB(hue, Math.min(distance, 1F), value);
				int r = (rgb >> 16) & 0xFF;
				int g = (rgb >> 8) & 0xFF;
				int b = rgb & 0xFF;
				image.setPixelABGR(x, y, (0xFF << 24) | (b << 16) | (g << 8) | r);
			}
		}
		return image;
	}
}
