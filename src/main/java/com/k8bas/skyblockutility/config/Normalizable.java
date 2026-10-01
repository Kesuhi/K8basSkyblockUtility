package com.k8bas.skyblockutility.config;

/** A config section that can repair values Gson leaves invalid (REQ-CFG-07). */
public interface Normalizable {
	/** Repairs invalid values in place. @return true if anything changed, so the file is saved. */
	boolean normalize();
}
