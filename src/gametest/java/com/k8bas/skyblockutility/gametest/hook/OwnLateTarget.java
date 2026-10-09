package com.k8bas.skyblockutility.gametest.hook;

/**
 * The target of a test hook of ours whose late injection fails (T3.0f): Mixin loads the class with its original
 * bytes, so {@code value()} stays 5, without the other mixin's change either.
 */
public class OwnLateTarget {
	public int value() {
		return base();
	}

	int base() {
		return 5;
	}
}
