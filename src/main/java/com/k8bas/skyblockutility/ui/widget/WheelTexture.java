package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.color.Hsv;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * One shared hue/saturation wheel texture for the colour picker (REQ-UI-13), baked at the requested
 * brightness and rebaked only when that changes; the picked colour is a puck drawn on top. 128 px with
 * a soft rim, so it stays smooth at large GUI scales. Uploaded through the game's texture API (no
 * direct GL, REQ-PORT-12).
 */
final class WheelTexture {
	static final int SIZE = 128;
	static final Identifier ID = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "dynamic/ui_color_wheel");

	private static DynamicTexture texture;
	private static float bakedValue = -1F;

	private WheelTexture() {
	}

	static void ensureValue(float value) {
		if (texture == null) {
			texture = new DynamicTexture(() -> "k8bas colour wheel", build(value));
			Minecraft.getInstance().getTextureManager().register(ID, texture);
			bakedValue = value;
			return;
		}
		if (Math.abs(bakedValue - value) < 1F / 255F) {
			return;
		}
		texture.setPixels(build(value));
		texture.upload();
		bakedValue = value;
	}

	private static NativeImage build(float value) {
		NativeImage image = new NativeImage(SIZE, SIZE, false);
		float radius = SIZE / 2F;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				float dx = x + 0.5F - radius;
				float dy = y + 0.5F - radius;
				float distance = (float) Math.sqrt(dx * dx + dy * dy);
				// Fully inside up to half a pixel from the rim, then fading over one pixel.
				float coverage = Math.max(0F, Math.min(1F, radius - distance + 0.5F));
				if (coverage <= 0F) {
					image.setPixelABGR(x, y, 0);
					continue;
				}
				float hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2)) + 0.5F;
				int rgb = new Hsv(hue % 1F, Math.min(1F, distance / radius), value).toRgb();
				int alpha = Math.round(coverage * 255);
				image.setPixelABGR(x, y, alpha << 24 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | rgb >> 16 & 0xFF);
			}
		}
		return image;
	}
}
