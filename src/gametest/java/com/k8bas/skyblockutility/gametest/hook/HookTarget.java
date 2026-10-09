package com.k8bas.skyblockutility.gametest.hook;

/**
 * The target of the test hooks (T3.0f): {@code value()} is 21 untouched; the applied hooks make it
 * ({@code base()} + 1 + 1) * 2 = 44.
 */
public class HookTarget {
	public int value() {
		return base() + 1;
	}

	int base() {
		return 20;
	}
}
