package com.k8bas.skyblockutility.ui.color;

/**
 * What the colour picker holds while open (REQ-UI-13): HSV as the truth (so brightness to 0 and back
 * keeps the hue and saturation, AC-UI-12), alpha only for colours that store it, and the hex text
 * with whether it is valid. Nothing leaves it before Save.
 */
public final class ColorPickerState {
	private final int initial;
	private final boolean storesAlpha;
	private Hsv hsv;
	private float alpha;
	private String hexText;
	private boolean hexValid = true;
	private boolean externalChange;

	public ColorPickerState(int initial, boolean storesAlpha) {
		this.initial = storesAlpha ? initial : initial & 0xFFFFFF;
		this.storesAlpha = storesAlpha;
		this.hsv = Hsv.fromRgb(initial & 0xFFFFFF, new Hsv(0F, 0F, 1F));
		this.alpha = storesAlpha ? (initial >>> 24) / 255F : 1F;
		this.hexText = HexColor.format(color(), storesAlpha);
	}

	public boolean storesAlpha() {
		return storesAlpha;
	}

	public Hsv hsv() {
		return hsv;
	}

	public float alpha() {
		return alpha;
	}

	/** 0xAARRGGBB for a colour with alpha, 0xRRGGBB without. */
	public int color() {
		int rgb = hsv.toRgb();
		return storesAlpha ? Math.round(alpha * 255) << 24 | rgb : rgb;
	}

	public int initial() {
		return initial;
	}

	public boolean changed() {
		return color() != initial;
	}

	public String hexText() {
		return hexText;
	}

	public boolean hexValid() {
		return hexValid;
	}

	public void setWheel(float hue, float saturation) {
		hsv = new Hsv(hue, clamp(saturation), hsv.v());
		syncHex();
	}

	public void setBrightness(float value) {
		hsv = new Hsv(hsv.h(), hsv.s(), clamp(value));
		syncHex();
	}

	public void setAlpha(float value) {
		if (storesAlpha) {
			alpha = clamp(value);
			syncHex();
		}
	}

	public void pickPreset(int rgb) {
		hsv = Hsv.fromRgb(rgb & 0xFFFFFF, hsv);
		syncHex();
	}

	/**
	 * Typed or pasted text: a valid colour is applied, an invalid one only marked. The text is kept as
	 * typed (a valid #RRGGBB may be the start of #AARRGGBB); {@link #normalizedHex()} is what the field
	 * shows once the player leaves it.
	 */
	public void typeHex(String text) {
		hexText = text;
		Integer parsed = HexColor.parse(text, storesAlpha);
		if (parsed == null) {
			hexValid = false;
			return;
		}
		hexValid = true;
		hsv = Hsv.fromRgb(parsed & 0xFFFFFF, hsv);
		if (storesAlpha) {
			alpha = (parsed >>> 24) / 255F;
		}
	}

	/** The current colour as hex, e.g. "#29B6B2" for a typed "29b6b2" (AC-UI-12). */
	public String normalizedHex() {
		return HexColor.format(color(), storesAlpha);
	}

	/** True once after the wheel, a slider or a preset changed the colour, so the hex field follows it. */
	public boolean takeExternalChange() {
		boolean changed = externalChange;
		externalChange = false;
		return changed;
	}

	private void syncHex() {
		hexText = normalizedHex();
		hexValid = true;
		externalChange = true;
	}

	private static float clamp(float value) {
		return Math.max(0F, Math.min(1F, value));
	}
}
