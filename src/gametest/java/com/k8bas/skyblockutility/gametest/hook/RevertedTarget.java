package com.k8bas.skyblockutility.gametest.hook;

/**
 * The target of a test hook that goes in but is lost (T3.0f): another mixin on this class fails in MixinExtras'
 * late phase, so Mixin loads the class with its original bytes and {@code value()} stays 7.
 */
public class RevertedTarget {
	public int value() {
		return base();
	}

	int base() {
		return 7;
	}
}
