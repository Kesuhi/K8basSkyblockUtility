package com.k8bas.skyblockutility.skyblock;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the sanitised G1 captures (src/test/resources/fixtures/captures/g1-2026-10-01, see its README for
 * provenance: `/ksu debug dump tab|sidebar` on Hypixel, 2026-10-01, names masked, server ids replaced) back
 * into reader input (REQ-GS-14). Git may check them out with CRLF, so lines are split on \r?\n.
 */
final class CaptureFixtures {
	static final String G1 = "/fixtures/captures/g1-2026-10-01/";

	private static final Pattern TAB = Pattern.compile("^\\[K8BAS-DUMP] tab \\d+ order=(-?\\d+) name=(\\S+) \\| (.*)$");
	private static final Pattern SIDEBAR_TITLE = Pattern.compile("^\\[K8BAS-DUMP] sidebar title \\| (.*)$");
	private static final Pattern SIDEBAR_LINE = Pattern.compile("^\\[K8BAS-DUMP] sidebar \\d+ score=-?\\d+ \\| (.*)$");

	private CaptureFixtures() {
	}

	/** The tab rows of a tab capture, in the order the client listed them (not tab order). */
	static List<TabRow> tabRows(String file) {
		List<TabRow> rows = new ArrayList<>();
		for (String line : lines(file)) {
			Matcher m = TAB.matcher(line);
			if (!m.matches()) {
				throw new IllegalArgumentException(file + ": not a tab dump line: " + line);
			}
			rows.add(new TabRow(m.group(2), Integer.parseInt(m.group(1)), m.group(3)));
		}
		return rows;
	}

	/**
	 * A sidebar capture as reader input. The dump logged each line composed (prefix + entry + suffix), so the
	 * whole text is the prefix here; the split itself is covered by synthetic lines.
	 */
	static SidebarRaw sidebar(String file) {
		String title = null;
		List<SidebarLine> lines = new ArrayList<>();
		for (String line : lines(file)) {
			Matcher t = SIDEBAR_TITLE.matcher(line);
			Matcher l = SIDEBAR_LINE.matcher(line);
			if (t.matches()) {
				title = t.group(1);
			} else if (l.matches()) {
				lines.add(new SidebarLine(l.group(1), "", ""));
			} else {
				throw new IllegalArgumentException(file + ": not a sidebar dump line: " + line);
			}
		}
		return new SidebarRaw(title, lines);
	}

	private static List<String> lines(String file) {
		try (InputStream in = CaptureFixtures.class.getResourceAsStream(G1 + file)) {
			if (in == null) {
				throw new IllegalArgumentException("missing fixture " + G1 + file);
			}
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return text.lines().map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
					.filter(line -> !line.isEmpty()).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
