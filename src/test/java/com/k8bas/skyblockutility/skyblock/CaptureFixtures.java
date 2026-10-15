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
 * Reads the sanitised captures back into reader input (REQ-GS-14). Each folder's README gives the provenance:
 * <ul>
 *   <li>{@link #G1} (src/test/resources/fixtures/captures/g1-2026-10-01): `/ksu debug dump tab|sidebar|containers` on
 *   Hypixel, 2026-10-01, names masked, server ids replaced;</li>
 *   <li>{@link #G3} (src/test/resources/fixtures/captures/g3-2026-10-15): the same dumps on 2026-10-15 (a mineshaft
 *   tab and sidebar, Croesus, `/sblevels` pages, Heart of the Mountain, the Dwarven Mines Sack), sanitised the same way.</li>
 * </ul>
 * Git may check them out with CRLF, so lines are split on \r?\n.
 */
public final class CaptureFixtures {
	public static final String G1 = "/fixtures/captures/g1-2026-10-01/";
	public static final String G3 = "/fixtures/captures/g3-2026-10-15/";

	private static final Pattern TAB = Pattern.compile("^\\[K8BAS-DUMP] tab \\d+ order=(-?\\d+) name=(\\S+) \\| (.*)$");
	private static final Pattern SIDEBAR_TITLE = Pattern.compile("^\\[K8BAS-DUMP] sidebar title \\| (.*)$");
	private static final Pattern SIDEBAR_LINE = Pattern.compile("^\\[K8BAS-DUMP] sidebar \\d+ score=-?\\d+ \\| (.*)$");
	private static final Pattern CONTAINER_TITLE = Pattern.compile("^\\[K8BAS-DUMP] container (\\d+) title=(.*) slots=(\\d+) state=(-?\\d+)$");
	private static final Pattern CONTAINER_SLOT = Pattern.compile("^\\[K8BAS-DUMP] container \\d+ slot (\\d+) item=(\\S+) sbid=(\\S+) count=(\\d+) \\| (.*)$");
	private static final Pattern CONTAINER_LORE = Pattern.compile("^\\[K8BAS-DUMP] container \\d+ slot (\\d+) lore (\\d+) \\| (.*)$");

	/** One menu capture: the dump's capture number and the snapshot it was written from. */
	public record MenuCapture(int number, MenuSnapshot menu) {
	}

	private CaptureFixtures() {
	}

	/** The tab rows of a G1 tab capture, in the order the client listed them (not tab order). */
	static List<TabRow> tabRows(String file) {
		return tabRows(G1, file);
	}

	/** The tab rows of a tab capture in [folder] ({@link #G1} or {@link #G3}), in the client's list order. */
	static List<TabRow> tabRows(String folder, String file) {
		List<TabRow> rows = new ArrayList<>();
		for (String line : lines(folder + file)) {
			Matcher m = TAB.matcher(line);
			if (!m.matches()) {
				throw new IllegalArgumentException(file + ": not a tab dump line: " + line);
			}
			rows.add(new TabRow(m.group(2), Integer.parseInt(m.group(1)), m.group(3)));
		}
		return rows;
	}

	/** A G1 sidebar capture as reader input, see {@link #sidebar(String, String)}. */
	static SidebarRaw sidebar(String file) {
		return sidebar(G1, file);
	}

	/**
	 * A sidebar capture as reader input. The dump logged each line composed (prefix + entry + suffix), so the
	 * whole text is the prefix here; the split itself is covered by synthetic lines.
	 */
	static SidebarRaw sidebar(String folder, String file) {
		String title = null;
		List<SidebarLine> lines = new ArrayList<>();
		for (String line : lines(folder + file)) {
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

	/** A menu capture file (`containers/…` in [folder]) read back into the snapshot the dump wrote it from. */
	public static MenuCapture menu(String folder, String file) {
		return menu(lines(folder + file));
	}

	/**
	 * Reads one container capture back into a menu snapshot: the header line, then each slot line followed by its
	 * lore lines in order. `sbid=-` is no SkyBlock id.
	 */
	public static MenuCapture menu(List<String> lines) {
		Matcher title = CONTAINER_TITLE.matcher(lines.get(0));
		if (!title.matches()) {
			throw new IllegalArgumentException("not a container header: " + lines.get(0));
		}
		List<MenuSlot> slots = new ArrayList<>();
		int index = -1;
		String item = null;
		String sbid = null;
		int count = 0;
		String name = null;
		List<String> lore = new ArrayList<>();
		for (String line : lines.subList(1, lines.size())) {
			Matcher slot = CONTAINER_SLOT.matcher(line);
			Matcher loreLine = CONTAINER_LORE.matcher(line);
			if (slot.matches()) {
				if (index >= 0) {
					slots.add(new MenuSlot(index, item, count, name, List.copyOf(lore), sbid.equals("-") ? null : sbid));
				}
				index = Integer.parseInt(slot.group(1));
				item = slot.group(2);
				sbid = slot.group(3);
				count = Integer.parseInt(slot.group(4));
				name = slot.group(5);
				lore.clear();
			} else if (loreLine.matches()) {
				if (index != Integer.parseInt(loreLine.group(1)) || lore.size() != Integer.parseInt(loreLine.group(2))) {
					throw new IllegalArgumentException("lore line out of order: " + line);
				}
				lore.add(loreLine.group(3));
			} else {
				throw new IllegalArgumentException("not a container dump line: " + line);
			}
		}
		if (index >= 0) {
			slots.add(new MenuSlot(index, item, count, name, List.copyOf(lore), sbid.equals("-") ? null : sbid));
		}
		return new MenuCapture(Integer.parseInt(title.group(1)),
				new MenuSnapshot(title.group(2), Integer.parseInt(title.group(4)), Integer.parseInt(title.group(3)), List.copyOf(slots)));
	}

	/** The non-empty lines of a fixture resource, without line ends. */
	public static List<String> lines(String resource) {
		try (InputStream in = CaptureFixtures.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalArgumentException("missing fixture " + resource);
			}
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return text.lines().map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
					.filter(line -> !line.isEmpty()).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
