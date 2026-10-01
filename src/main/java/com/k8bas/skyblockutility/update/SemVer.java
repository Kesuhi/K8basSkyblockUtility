package com.k8bas.skyblockutility.update;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A strict Semantic Versioning 2.0.0 version (REQ-UPD-05). Build metadata ("+26.2") is accepted and
 * ignored, so {@code 1.1.0+26.2} equals {@code 1.1.0+26.1.2}.
 */
public record SemVer(int major, int minor, int patch, List<String> preRelease) implements Comparable<SemVer> {
	private static final String NUMBER = "0|[1-9]\\d*";
	private static final String PRE_ID = "0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*";
	private static final Pattern PATTERN = Pattern.compile("^(" + NUMBER + ")\\.(" + NUMBER + ")\\.(" + NUMBER + ")"
			+ "(?:-((?:" + PRE_ID + ")(?:\\.(?:" + PRE_ID + "))*))?"
			+ "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

	public SemVer {
		preRelease = List.copyOf(preRelease);
	}

	public static Optional<SemVer> parse(String text) {
		if (text == null) {
			return Optional.empty();
		}
		Matcher matcher = PATTERN.matcher(text);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		try {
			List<String> pre = matcher.group(4) == null ? List.of() : List.of(matcher.group(4).split("\\."));
			return Optional.of(new SemVer(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
					Integer.parseInt(matcher.group(3)), pre));
		} catch (NumberFormatException tooLarge) {
			return Optional.empty();
		}
	}

	/** A release tag: one leading "v" is stripped, the rest must be SemVer. */
	public static Optional<SemVer> fromTag(String tag) {
		if (tag == null) {
			return Optional.empty();
		}
		return parse(tag.startsWith("v") ? tag.substring(1) : tag);
	}

	public boolean isPreRelease() {
		return !preRelease.isEmpty();
	}

	@Override
	public int compareTo(SemVer other) {
		int result = Integer.compare(major, other.major);
		if (result == 0) {
			result = Integer.compare(minor, other.minor);
		}
		if (result == 0) {
			result = Integer.compare(patch, other.patch);
		}
		if (result != 0) {
			return result;
		}
		// A version without a pre-release part ranks above the same version with one.
		if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
			return Boolean.compare(preRelease.isEmpty(), other.preRelease.isEmpty());
		}
		for (int i = 0; i < Math.min(preRelease.size(), other.preRelease.size()); i++) {
			result = compareIdentifier(preRelease.get(i), other.preRelease.get(i));
			if (result != 0) {
				return result;
			}
		}
		return Integer.compare(preRelease.size(), other.preRelease.size());
	}

	/** Numeric identifiers compare as numbers and rank below alphanumeric ones, which compare in ASCII order. */
	private static int compareIdentifier(String a, String b) {
		boolean aNumeric = a.chars().allMatch(Character::isDigit);
		boolean bNumeric = b.chars().allMatch(Character::isDigit);
		if (aNumeric && bNumeric) {
			return a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b);
		}
		if (aNumeric != bNumeric) {
			return aNumeric ? -1 : 1;
		}
		return a.compareTo(b);
	}

	@Override
	public String toString() {
		return major + "." + minor + "." + patch + (preRelease.isEmpty() ? "" : "-" + String.join(".", preRelease));
	}
}
