package com.k8bas.skyblockutility.ui.color;

/** The hue/saturation wheel: the angle picks the hue (red at the left), the distance from the centre the saturation. */
public final class WheelGeometry {
	private WheelGeometry() {
	}

	/**
	 * Hue and saturation at an offset from the wheel's centre; saturation is held at 1 outside the
	 * circle, and at the very centre the hue stays {@code previousHue}.
	 */
	public static float[] hsAt(double dx, double dy, double radius, float previousHue) {
		double distance = Math.sqrt(dx * dx + dy * dy);
		float saturation = (float) Math.min(1.0, distance / radius);
		float hue = distance > 1e-4 ? (float) (Math.atan2(dy, dx) / (Math.PI * 2) + 0.5) % 1F : previousHue;
		return new float[] {hue, saturation};
	}

	/** Where hue and saturation sit, as an offset from the centre. */
	public static double[] puck(float hue, float saturation, double radius) {
		double angle = (hue - 0.5) * Math.PI * 2;
		return new double[] {Math.cos(angle) * saturation * radius, Math.sin(angle) * saturation * radius};
	}
}
