package com.k8bas.skyblockutility.ui.widget;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A slider's values (REQ-UI-06, AC-UI-06), in whole units of its precision so decimal steps never
 * drift: a decimal slider with two decimals counts hundredths. A step lands on the grid of steps from
 * the minimum; Shift gives the fine step of one unit; the maximum is always reachable. A stored value
 * outside the range is shown clamped and only replaced when the player changes it (EC-UI-05).
 */
public final class SliderModel {
	private final long min;
	private final long max;
	private final long step;
	private final long fine;
	private final int decimals;
	private final long unitsPerOne;

	private SliderModel(long min, long max, long step, long fine, int decimals) {
		if (min > max || step <= 0 || fine <= 0) {
			throw new IllegalArgumentException("a slider needs min <= max and positive steps");
		}
		this.min = min;
		this.max = max;
		this.step = step;
		this.fine = fine;
		this.decimals = decimals;
		this.unitsPerOne = (long) Math.pow(10, decimals);
	}

	public static SliderModel ofInt(int min, int max, int step) {
		return new SliderModel(min, max, step, 1, 0);
	}

	/** @param decimals the stored precision, e.g. 2 for hundredths */
	public static SliderModel ofDecimal(double min, double max, double step, int decimals) {
		long perOne = (long) Math.pow(10, decimals);
		return new SliderModel(Math.round(min * perOne), Math.round(max * perOne), Math.round(step * perOne), 1, decimals);
	}

	/** A value in units, e.g. 1.3 -> 130 with two decimals. */
	public long units(double value) {
		return Math.round(value * unitsPerOne);
	}

	public double toDouble(long units) {
		return units / (double) unitsPerOne;
	}

	public long min() {
		return min;
	}

	public long max() {
		return max;
	}

	/** The value as the slider shows it: clamped to the range. */
	public long shown(long stored) {
		return Math.max(min, Math.min(max, stored));
	}

	/**
	 * The value after {@code notches} steps (scroll wheel or keys), up for positive. Off the grid, the
	 * first step goes to the neighbouring grid value in that direction.
	 */
	public long stepFrom(long stored, int notches, boolean useFine) {
		long grid = useFine ? fine : step;
		long value = shown(stored);
		if (notches == 0) {
			return value;
		}
		long offset = Math.floorMod(value - min, grid);
		long result;
		if (offset == 0) {
			result = value + (long) notches * grid;
		} else if (notches > 0) {
			result = value - offset + grid + (long) (notches - 1) * grid;
		} else {
			result = value - offset + (long) (notches + 1) * grid;
		}
		return shown(result);
	}

	/** The value at a point of the track, as a fraction from 0 (left) to 1 (right). */
	public long fromTrack(double fraction, boolean useFine) {
		if (fraction >= 1.0) {
			return max;
		}
		if (fraction <= 0.0) {
			return min;
		}
		long grid = useFine ? fine : step;
		long steps = Math.round(fraction * (max - min) / grid);
		return shown(min + steps * grid);
	}

	/** Where a value sits on the track, 0 to 1. */
	public double fraction(long stored) {
		return max == min ? 0.0 : (shown(stored) - min) / (double) (max - min);
	}

	/** The value as text with its precision, e.g. "1.30" or "64". */
	public String format(long stored) {
		return BigDecimal.valueOf(shown(stored), decimals).setScale(decimals, RoundingMode.UNNECESSARY).toPlainString();
	}
}
