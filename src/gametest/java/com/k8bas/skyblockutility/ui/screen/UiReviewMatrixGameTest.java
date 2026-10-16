package com.k8bas.skyblockutility.ui.screen;

import com.k8bas.skyblockutility.config.ConfigManager;
import com.k8bas.skyblockutility.gametest.ModuleStates;
import com.k8bas.skyblockutility.ui.notice.Notice;
import com.k8bas.skyblockutility.ui.notice.NoticeLayout;
import com.k8bas.skyblockutility.ui.notice.Notices;
import com.k8bas.skyblockutility.ui.option.Category;
import com.k8bas.skyblockutility.ui.option.IntSlider;
import com.k8bas.skyblockutility.ui.option.Option;
import com.k8bas.skyblockutility.ui.option.Toggle;
import com.k8bas.skyblockutility.ui.render.HairlineProbe;
import com.k8bas.skyblockutility.ui.render.Shapes;
import com.k8bas.skyblockutility.ui.render.Theme;
import com.k8bas.skyblockutility.ui.render.TooltipLayout;
import com.k8bas.skyblockutility.ui.widget.Button;
import com.k8bas.skyblockutility.ui.widget.ColorSwatch;
import com.k8bas.skyblockutility.ui.widget.Dropdown;
import com.k8bas.skyblockutility.ui.widget.KeybindButton;
import com.k8bas.skyblockutility.ui.widget.KnobProbe;
import com.k8bas.skyblockutility.ui.widget.OutlineProbe;
import com.k8bas.skyblockutility.ui.widget.Overlay;
import com.k8bas.skyblockutility.ui.widget.ScrollbarProbe;
import com.k8bas.skyblockutility.ui.widget.Slider;
import com.k8bas.skyblockutility.ui.widget.TextField;
import com.k8bas.skyblockutility.ui.widget.VirtualList;
import com.k8bas.skyblockutility.ui.widget.Widget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * T3.37, the UI review's screenshot matrix, AC-UI-49 [C]: the settings screen at GUI scales 1-4 on 1920x1080 and at GUI scale 3
 * on 1366x768, each with Smooth corners ON and OFF, showing the sliders at their minimum, middle and maximum, a dimmed slider
 * with its card idle and hovered, a tooltip placed over a slider (T3.34's hook), a notice, an open dropdown list, the colour
 * picker and the database picker. On every image the automatic checks run where they apply:
 * <ul>
 *   <li>AC-UI-28 at GUI scales 2-4: every shown slider's knob ({@link KnobProbe}), and the track's ends with a knob at either end;</li>
 *   <li>AC-UI-29 at GUI scales 3 and 4: the outline corners of every shown idle button, text field, dropdown, keybind button and
 *       swatch, the open dropdown list and the notice ({@link OutlineProbe}); the tooltip over a slider: the overlap exists in the
 *       image before it is drawn, and it shows no accent pixel inside its rounded box;</li>
 *   <li>AC-UI-34 at GUI scales 2-4: those outlines and the pickers' and the list's are a hairline thick ({@link HairlineProbe});</li>
 *   <li>AC-UI-37 at GUI scales 1-4: the content area's and the database picker's scrollbars, when they scroll ({@link ScrollbarProbe}).</li>
 * </ul>
 * Also AC-UI-30 [C] (EC-UI-21): ON and OFF differ only inside the corner squares of rounded rectangles and outlines; and
 * AC-UI-48 [C] (EC-UI-18): a GUI scale sweep with the colour picker and then an open dropdown list logs no error, and each
 * frame after a change draws from textures made for the new scale, within the caches' bounds. The images are kept for the
 * G3u report.
 */
public class UiReviewMatrixGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas-gametest");
	/** Behind the panel, plain, so two screenshots compare (the title screen's panorama turns). */
	private static final int BACKDROP = 0xFF101418;
	private static final String FILL = "fishing_hotspots.fill_opacity";
	private static final String VOLUME = "fishing_hotspots.sound_volume";
	private static final String HOTSPOTS = "fishing_hotspots.enabled";

	private record Config(int width, int height, int scale) {
		String tag() {
			return width + "x" + height + "-scale-" + scale;
		}
	}

	private static final List<Config> CONFIGS = List.of(new Config(1920, 1080, 1), new Config(1920, 1080, 2), new Config(1920, 1080, 3),
			new Config(1920, 1080, 4), new Config(1366, 768, 3));

	/** How many times each check ran, by name, for the log. */
	private static final Map<String, Integer> RAN = new TreeMap<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleStates modules = context.computeOnClient(client -> ModuleStates.capture());
		Boolean smoothBefore = context.computeOnClient(client -> ConfigManager.general().smoothCorners);
		int[] slidersBefore = context.computeOnClient(client -> new int[] {sliderOption(client, FILL).binding().get(), sliderOption(client, VOLUME).binding().get()});
		context.getInput().setCursorPos(1, 1);
		String start = "k8bas ui review matrix start " + System.nanoTime();
		String end = "k8bas ui review matrix end " + System.nanoTime();
		LOGGER.info(start);
		try {
			for (Config config : CONFIGS) {
				open(context, config);
				for (boolean smooth : new boolean[] {true, false}) {
					context.runOnClient(client -> ConfigManager.general().smoothCorners = smooth);
					context.waitTicks(3);
					check(context.computeOnClient(client -> ConfigManager.general().smoothCorners()) == smooth, "Smooth corners reads " + smooth);
					states(context, config, smooth);
				}
				onOffDiffer(context, config);
				context.setScreen(() -> null);
			}
			scaleSweep(context);
			LOGGER.info(end);
			List<String> errors = ConfigScreenGameTest.errorsBetween(context, start, end);
			check(errors.isEmpty(), "no error or warning logged during the matrix: " + errors);
			LOGGER.info("UI review matrix: checks run {}", RAN);
			for (String kind : List.of("Button", "TextField", "KeybindButton", "ColorSwatch", "Dropdown", "focus ring", "tooltip", "notice", "dropdown list")) {
				check(RAN.containsKey("AC-UI-29 corners " + kind), "AC-UI-29 measured on a " + kind + ": " + RAN);
			}
		} finally {
			context.runOnClient(client -> {
				Notices.clear();
				Shapes.recordCornersForTests(false);
				modules.restore();
				ConfigManager.general().smoothCorners = smoothBefore;
				sliderOption(client, FILL).binding().set(slidersBefore[0]);
				sliderOption(client, VOLUME).binding().set(slidersBefore[1]);
				ConfigManager.save();
			});
			context.setScreen(() -> null);
			context.restoreDefaultGameOptions();
			context.getInput().resizeWindow(854, 480);
		}
	}

	private static void open(ClientGameTestContext context, Config config) {
		context.getInput().resizeWindow(config.width(), config.height());
		context.runOnClient(client -> client.options.guiScale().set(config.scale()));
		context.setScreen(() -> new ConfigScreen(null));
		context.waitForScreen(ConfigScreen.class);
		context.runOnClient(client -> screen(client).plainBackgroundForTests(BACKDROP));
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(3);
		check(context.computeOnClient(client -> client.getWindow().getGuiScale()) == config.scale(), config.tag() + ": the GUI scale applies");
	}

	/** Every state of AC-UI-49 at one size, scale and Smooth corners setting, with the checks on each image. */
	private static void states(ClientGameTestContext context, Config config, boolean smooth) {
		String tag = "t3.37-" + config.tag() + (smooth ? "-on" : "-off");
		showFishingSliders(context);
		String[] names = {"min", "mid", "max"};
		for (int i = 0; i < 3; i++) {
			int step = i;
			context.runOnClient(client -> {
				for (String id : List.of(FILL, VOLUME)) {
					IntSlider slider = sliderOption(client, id);
					slider.binding().set(step == 0 ? slider.min() : step == 1 ? (slider.min() + slider.max()) / 2 : slider.max());
				}
			});
			context.waitTicks(2);
			checkImage(context, config, smooth, tag + "-sliders-" + names[i], step != 1);
		}
		// A dimmed slider (its feature off) in the middle, its card idle and hovered.
		context.runOnClient(client -> {
			for (String id : List.of(FILL, VOLUME)) {
				IntSlider slider = sliderOption(client, id);
				slider.binding().set((slider.min() + slider.max()) / 2);
			}
			toggleOption(client, HOTSPOTS).binding().set(false);
		});
		context.waitTicks(2);
		BufferedImage idle = checkImage(context, config, smooth, tag + "-dimmed-card-idle", false, true);
		// The card of the slider that shows, hovered at its left padding; its colour there must change.
		int[] card = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			Slider shown = (Slider) screen.page().widgets().stream().filter(widget -> widget instanceof Slider slider && slider.fullyShowing()).findFirst()
					.orElseThrow(() -> new AssertionError("FAILED: no slider shows whole"));
			Option option = screen.page().page().rows().stream().filter(row -> row instanceof ConfigLayout.OptionRow o && screen.page().control(o.option()) == shown)
					.map(row -> ((ConfigLayout.OptionRow) row).option()).findFirst().orElseThrow();
			Widget cardWidget = screen.page().card(option);
			int[] at = window(client, cardWidget.x() + 8, cardWidget.y() + cardWidget.height() - 4);
			return new int[] {at[0], at[1], cardWidget.x() + 3, cardWidget.y() + cardWidget.height() / 2};
		});
		context.getInput().setCursorPos(card[0], card[1]);
		context.waitTicks(3);
		BufferedImage hovered = checkImage(context, config, smooth, tag + "-dimmed-card-hovered", false, true);
		int s = config.scale();
		check(idle.getRGB(card[2] * s, card[3] * s) != hovered.getRGB(card[2] * s, card[3] * s), tag + ": the dimmed slider's card looks hovered: "
				+ Integer.toHexString(idle.getRGB(card[2] * s, card[3] * s)) + " both times");
		ran("dimmed card hovered");
		context.getInput().setCursorPos(1, 1);
		context.runOnClient(client -> toggleOption(client, HOTSPOTS).binding().set(true));
		context.waitTicks(2);
		// The other pages' outlined controls: keybind buttons, swatches and buttons.
		controlsOn(context, config, smooth, tag, Category.GENERAL, "keybinds.open_settings");
		controlsOn(context, config, smooth, tag, Category.WAYPOINTS, "npc_search.island_color.hub");
		controlsOn(context, config, smooth, tag, Category.HIGHLIGHTS, "mob_highlighter.add_from_database");
		focusRing(context, config, smooth, tag);
		showFishingSliders(context);
		tooltipOverASlider(context, config, smooth, tag);
		aNotice(context, config, smooth, tag);
		anOpenDropdown(context, config, smooth, tag);
		theColourPicker(context, config, smooth, tag);
		theDatabasePicker(context, config, smooth, tag);
	}

	/** The Fishing page, scrolled so both its sliders show. */
	private static void showFishingSliders(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ConfigScreen screen = screen(client);
			screen.select(Category.FISHING);
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			ConfigScreen screen = screen(client);
			ConfigLayout.OptionRow first = optionRow(screen, FILL);
			ConfigLayout.OptionRow last = optionRow(screen, VOLUME);
			screen.scrollArea().ensureVisible(first.y() - 8, last.y() + last.height());
			screen.scrollArea().setScroll(screen.scrollArea().target());
		});
		context.waitTicks(3);
	}

	/**
	 * Takes the screenshot {@code name} and runs the page's checks on it: every shown slider (and its track's ends when
	 * {@code ends}), the outlines of the shown controls, and the content area's scrollbar.
	 */
	private static BufferedImage checkImage(ClientGameTestContext context, Config config, boolean smooth, String name, boolean ends) {
		return checkImage(context, config, smooth, name, ends, true);
	}

	/** As above; {@code sliderShows}: a slider must show (the Fishing page), else the sliders that show are checked. */
	private static BufferedImage checkImage(ClientGameTestContext context, Config config, boolean smooth, String name, boolean ends, boolean sliderShows) {
		BufferedImage shot = HairlineProbe.read(context.takeScreenshot(name));
		int scale = config.scale();
		List<int[]> sliders = context.computeOnClient(client -> {
			List<int[]> found = new ArrayList<>();
			for (Widget widget : screen(client).page().widgets()) {
				if (widget instanceof Slider slider && slider.fullyShowing() && slider.isEnabled()) {
					found.add(KnobProbe.of(slider));
				}
			}
			return found;
		});
		if (scale >= 2) {
			check(!sliderShows || !sliders.isEmpty(), name + ": a slider shows");
			for (int[] slider : sliders) {
				ran("AC-UI-28 knob");
				String problem = KnobProbe.knob(shot, scale, slider, true, name);
				check(problem.isEmpty(), problem);
				if (ends) {
					ran("AC-UI-28 track ends");
					String endProblem = KnobProbe.trackEnds(shot, scale, slider, name);
					check(endProblem.isEmpty(), endProblem);
				}
			}
		}
		outlines(context, shot, config, smooth, name);
		contentScrollbar(context, shot, config, name);
		return shot;
	}

	/** AC-UI-29 at GUI scales 3-4 and AC-UI-34 at 2-4: the outlines of every shown idle control of the page and the search box. */
	private static void outlines(ClientGameTestContext context, BufferedImage shot, Config config, boolean smooth, String name) {
		int scale = config.scale();
		if (scale < 2) {
			return;
		}
		List<Object[]> boxes = context.computeOnClient(client -> {
			List<Object[]> found = new ArrayList<>();
			List<Widget> candidates = new ArrayList<>(screen(client).page().widgets());
			candidates.add(screen(client).search());
			for (Widget widget : candidates) {
				boolean outlined = widget instanceof Button button && button.style() == Button.Style.NORMAL || widget instanceof TextField || widget instanceof KeybindButton
						|| widget instanceof ColorSwatch || widget instanceof Dropdown<?> dropdown && !dropdown.isOpen();
				if (outlined && widget.fullyShowing() && widget.isEnabled() && !widget.isDimmed() && widget.width() > 0) {
					found.add(new Object[] {new int[] {widget.x(), widget.y(), widget.width(), widget.height()},
							widget instanceof Dropdown<?> ? "Dropdown" : widget.getClass().getSimpleName()});
				}
			}
			return found;
		});
		for (Object[] found : boxes) {
			int[] box = (int[]) found[0];
			outline(shot, scale, smooth, box, Shapes.RADIUS_CONTROL, (String) found[1], name + " " + found[1] + " at " + box[0] + "," + box[1]);
		}
	}

	/** One hairline outline in the separator colour: its thickness at its top edge's middle, and at 3-4 its corners. */
	private static void outline(BufferedImage shot, int scale, boolean smooth, int[] box, int radius, String kind, String what) {
		ran("AC-UI-34 hairline");
		String line = HairlineProbe.check(shot, what, scale, (box[0] + box[2] / 2) * scale, box[1] * scale, 0, 1, Theme.SEPARATOR);
		check(line.isEmpty(), line);
		if (scale >= 3) {
			ran("AC-UI-29 corners " + kind);
			int[] corners = OutlineProbe.corners(shot, scale, smooth, box[0] * scale, box[1] * scale, box[2] * scale, box[3] * scale, radius * scale,
					Theme.SEPARATOR);
			check(corners[0] > 0 && corners[1] == 0, what + ": " + corners[1] + " of " + corners[0] + " outline corner pixels lack the outline colour");
		}
	}

	/** AC-UI-37: the content area's idle scrollbar, when the page scrolls. */
	private static void contentScrollbar(ClientGameTestContext context, BufferedImage shot, Config config, String name) {
		int[] g = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			if (screen.overlay() != null || screen.scrollArea().maxScroll() == 0) {
				return null;
			}
			ConfigLayout.Rect content = screen.frame().content();
			var thumb = screen.scrollArea().thumb();
			return new int[] {content.right(), content.y(), content.bottom(), content.y() + thumb.offset(), thumb.length()};
		});
		if (g == null) {
			return;
		}
		ran("AC-UI-37 scrollbar");
		String problem = ScrollbarProbe.check(shot, name + ": the content area's scrollbar", config.scale(), g[0], g[1], g[2], g[3], g[4], false);
		check(problem.isEmpty(), problem);
	}

	/**
	 * AC-UI-29's tooltip over a slider: a box placed by the gametest hook over a shown slider's fill and knob; the overlap
	 * is first found in the image without it (accent fill and knob pixels in its rectangle), then with it no accent pixel
	 * shows inside its rounded box (its corner squares, where what lies under may show, left out).
	 */
	private static void tooltipOverASlider(ClientGameTestContext context, Config config, boolean smooth, String tag) {
		int scale = config.scale();
		context.runOnClient(client -> {
			for (String id : List.of(FILL, VOLUME)) {
				IntSlider slider = sliderOption(client, id);
				slider.binding().set((slider.min() + slider.max()) / 2);
			}
		});
		context.waitTicks(2);
		int[] box = context.computeOnClient(client -> {
			// The first slider that shows whole (both may not fit at once at a large GUI scale).
			Slider slider = screen(client).page().widgets().stream().filter(widget -> widget instanceof Slider shown && shown.fullyShowing())
					.map(widget -> (Slider) widget).findFirst().orElseThrow(() -> new AssertionError("FAILED: no slider shows whole"));
			int centre = slider.knobX() + Slider.KNOB_SIZE / 2;
			// Its top edge 2 GUI px above the track's middle, so its top corners lie on the track.
			return new int[] {centre - 30, slider.y() + slider.height() / 2 - 2, 40, 16};
		});
		int accent = context.computeOnClient(client -> Theme.current().accent()) & 0xFFFFFF;
		BufferedImage before = HairlineProbe.read(context.takeScreenshot(tag + "-tooltip-over-a-slider-before"));
		int fill = 0;
		int knob = 0;
		for (int y = box[1] * scale; y < (box[1] + box[3]) * scale; y++) {
			for (int x = box[0] * scale; x < (box[0] + box[2]) * scale; x++) {
				int rgb = before.getRGB(x, y) & 0xFFFFFF;
				fill += rgb == accent ? 1 : 0;
				knob += rgb == (Theme.TEXT_SECONDARY & 0xFFFFFF) ? 1 : 0;
			}
		}
		check(fill > 0 && knob > 0, tag + ": the tooltip's place overlaps the slider's fill (" + fill + " px) and knob (" + knob + " px)");
		context.runOnClient(client -> screen(client).placeTooltipForTests("Tip", box[0], box[1], box[2], box[3]));
		context.waitTicks(2);
		BufferedImage over = HairlineProbe.read(context.takeScreenshot(tag + "-tooltip-over-a-slider"));
		context.runOnClient(client -> screen(client).placeTooltipForTests(null, 0, 0, 0, 0));
		context.waitTicks(2);
		ran("AC-UI-29 tooltip over a slider");
		int r = Shapes.RADIUS_CONTROL * scale;
		int w = box[2] * scale;
		int h = box[3] * scale;
		int shown = 0;
		int covered = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				// Every pixel the tooltip's own rounded fill covers; in its corners what lies under may show outside the curve.
				boolean corner = (x < r || x >= w - r) && (y < r || y >= h - r);
				if (corner && !OutlineProbe.covers(smooth, w, h, r, r, x, y)) {
					continue;
				}
				covered++;
				int rgb = over.getRGB(box[0] * scale + x, box[1] * scale + y);
				if (KnobProbe.near(rgb, accent) || KnobProbe.near(rgb, Theme.TEXT_SECONDARY)) {
					shown++;
				}
			}
		}
		check(covered > 0 && shown == 0, tag + ": " + shown + " fill or knob pixels show inside the tooltip over the slider");
		if (scale >= 2) {
			outline(over, scale, smooth, box, Shapes.RADIUS_CONTROL, "tooltip", tag + ": the tooltip over a slider");
		}
	}

	/** A notice over the settings: its outline (AC-UI-29, AC-UI-34). */
	private static void aNotice(ClientGameTestContext context, Config config, boolean smooth, String tag) {
		context.runOnClient(client -> Notices.post(new Notice("A notice", List.of("Over the settings"))));
		context.waitTicks(3);
		BufferedImage shot = HairlineProbe.read(context.takeScreenshot(tag + "-notice"));
		int[] box = context.computeOnClient(client -> {
			List<NoticeLayout.Placed> placed = Notices.placed(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
			check(placed.size() == 1, "one notice shows: " + placed.size());
			NoticeLayout.Placed notice = placed.get(0);
			return new int[] {notice.x(), notice.y(), notice.width(), notice.height()};
		});
		context.runOnClient(client -> Notices.clear());
		context.waitTicks(2);
		if (config.scale() >= 2) {
			outline(shot, config.scale(), smooth, box, Shapes.RADIUS_CONTROL, "notice", tag + ": the notice");
		}
	}

	/** An open dropdown list: its outline (AC-UI-29, AC-UI-34) and, when it scrolls, its scrollbar (AC-UI-37). */
	private static void anOpenDropdown(ClientGameTestContext context, Config config, boolean smooth, String tag) {
		openWith(context, Category.GENERAL, "interface.notice_position");
		BufferedImage shot = HairlineProbe.read(context.takeScreenshot(tag + "-dropdown-open"));
		int[] box = context.computeOnClient(client -> {
			Overlay list = screen(client).overlay();
			check(list != null, "the dropdown opened");
			int[] bounds = list.bounds();
			return new int[] {bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1]};
		});
		if (config.scale() >= 2) {
			outline(shot, config.scale(), smooth, box, Shapes.RADIUS_CONTROL, "dropdown list", tag + ": the open dropdown list");
		}
		close(context);
	}

	/** The colour picker: its panel's outline (AC-UI-34). */
	private static void theColourPicker(ClientGameTestContext context, Config config, boolean smooth, String tag) {
		openWith(context, Category.WAYPOINTS, "npc_search.island_color.hub");
		BufferedImage shot = HairlineProbe.read(context.takeScreenshot(tag + "-colour-picker"));
		int[] box = context.computeOnClient(client -> {
			Overlay picker = screen(client).overlay();
			check(picker instanceof com.k8bas.skyblockutility.ui.widget.ColorPickerOverlay, "the colour picker opened: " + picker);
			int[] bounds = picker.bounds();
			return new int[] {bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1]};
		});
		if (config.scale() >= 2) {
			ran("AC-UI-34 hairline");
			String line = HairlineProbe.check(shot, tag + ": the colour picker", config.scale(), (box[0] + box[2] / 2) * config.scale(),
					box[1] * config.scale(), 0, 1, Theme.SEPARATOR);
			check(line.isEmpty(), line);
		}
		close(context);
	}

	/** The database picker: its panel's outline (AC-UI-34) and its list's scrollbar (AC-UI-37), a folder opened so it scrolls. */
	private static void theDatabasePicker(ClientGameTestContext context, Config config, boolean smooth, String tag) {
		openWith(context, Category.HIGHLIGHTS, "mob_highlighter.add_from_database");
		// Its folders opened one by one until its list scrolls, so its scrollbar shows.
		for (int folder = 0; folder < 8 && !context.computeOnClient(client -> ((DatabasePickerOverlay) screen(client).overlay()).list().rows().overflows()); folder++) {
			int row = folder;
			context.runOnClient(client -> {
				VirtualList list = ((DatabasePickerOverlay) screen(client).overlay()).list();
				list.press(list.x() + 10, list.y() + list.rows().rowTop(row) + 4, 0);
				list.release(list.x() + 10, list.y() + list.rows().rowTop(row) + 4);
			});
			context.waitTicks(2);
		}
		BufferedImage shot = HairlineProbe.read(context.takeScreenshot(tag + "-database-picker"));
		int[] g = context.computeOnClient(client -> {
			check(screen(client).overlay() instanceof DatabasePickerOverlay, "the database picker opened");
			DatabasePickerOverlay picker = (DatabasePickerOverlay) screen(client).overlay();
			int[] panel = picker.panel();
			int[] bounds = picker.bounds();
			VirtualList list = picker.list();
			var thumb = list.rows().thumb(list.height());
			return new int[] {panel[0], panel[1], panel[2], bounds[3] - bounds[1], list.x() + list.width(), list.y(), list.y() + list.height(),
					list.y() + thumb.offset(), thumb.length(), list.rows().overflows() ? 1 : 0};
		});
		int scale = config.scale();
		if (scale >= 2) {
			ran("AC-UI-34 hairline");
			String line = HairlineProbe.check(shot, tag + ": the database picker", scale, (g[0] + g[2] / 2) * scale, g[1] * scale, 0, 1, Theme.SEPARATOR);
			check(line.isEmpty(), line);
		}
		if (g[9] == 1) {
			ran("AC-UI-37 scrollbar");
			String problem = ScrollbarProbe.check(shot, tag + ": the database picker's scrollbar", scale, g[4], g[5], g[6], g[7], g[8], false);
			check(problem.isEmpty(), problem);
		}
		close(context);
	}

	/**
	 * AC-UI-30 [C] (EC-UI-21): the Fishing page with its sliders in the middle, Smooth corners ON and then OFF: the two screenshots
	 * differ only inside the corner squares of rounded rectangles and outlines that were drawn; discs, pills, hairlines,
	 * icons and shadows are the same.
	 */
	private static void onOffDiffer(ClientGameTestContext context, Config config) {
		showFishingSliders(context);
		context.runOnClient(client -> {
			for (String id : List.of(FILL, VOLUME)) {
				IntSlider slider = sliderOption(client, id);
				slider.binding().set((slider.min() + slider.max()) / 2);
			}
			ConfigManager.general().smoothCorners = true;
		});
		context.waitTicks(3);
		compare(context, config, "page");
		// With an overlay: the colour picker's wheel, check mark and shadow; a dropdown list's.
		openWith(context, Category.WAYPOINTS, "npc_search.island_color.hub");
		compare(context, config, "colour-picker");
		close(context);
		openWith(context, Category.GENERAL, "interface.notice_position");
		compare(context, config, "dropdown");
		close(context);
	}

	/** One AC-UI-30 comparison of what shows now, ON against OFF. */
	private static void compare(ClientGameTestContext context, Config config, String what) {
		String label = config.tag() + " " + what;
		BufferedImage on = HairlineProbe.read(context.takeScreenshot("t3.37-" + config.tag() + "-compare-" + what + "-on"));
		context.runOnClient(client -> {
			ConfigManager.general().smoothCorners = false;
			Shapes.recordCornersForTests(true);
		});
		context.waitTicks(3);
		check(!context.computeOnClient(client -> ConfigManager.general().smoothCorners()), label + ": Smooth corners reads OFF");
		BufferedImage off = HairlineProbe.read(context.takeScreenshot("t3.37-" + config.tag() + "-compare-" + what + "-off"));
		List<int[]> squares = context.computeOnClient(client -> {
			List<int[]> recorded = Shapes.recordedCornersForTests();
			Shapes.recordCornersForTests(false);
			ConfigManager.general().smoothCorners = true;
			return recorded;
		});
		context.waitTicks(3);
		check(!squares.isEmpty(), label + ": rounded corners were drawn");
		// The Smooth corners switch itself, when its page shows: it shows the setting, so it differs by design.
		int[] own = context.computeOnClient(client -> {
			ConfigScreen screen = screen(client);
			return screen.page().page().rows().stream().filter(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals("interface.smooth_corners"))
					.map(r -> screen.page().control(((ConfigLayout.OptionRow) r).option())).filter(w -> w != null && w.showing())
					.map(w -> new int[] {w.x(), w.y(), w.width(), w.height()}).findFirst().orElse(null);
		});
		int scale = config.scale();
		int differ = 0;
		int outside = 0;
		String first = "";
		for (int y = 0; y < Math.min(on.getHeight(), off.getHeight()); y++) {
			for (int x = 0; x < Math.min(on.getWidth(), off.getWidth()); x++) {
				if (on.getRGB(x, y) != off.getRGB(x, y)) {
					differ++;
					boolean theSwitch = own != null && x >= own[0] * scale - 1 && y >= own[1] * scale - 1 && x <= (own[0] + own[2]) * scale
							&& y <= (own[1] + own[3]) * scale;
					if (!theSwitch && !inASquare(squares, x, y)) {
						outside++;
						if (first.isEmpty()) {
							first = x + "," + y + ": " + Integer.toHexString(on.getRGB(x, y)) + " ON, " + Integer.toHexString(off.getRGB(x, y)) + " OFF";
						}
					}
				}
			}
		}
		ran("AC-UI-30 ON and OFF");
		LOGGER.info("{}: Smooth corners ON and OFF differ in {} pixels, {} of them outside the {} corner squares{}", label, differ, outside,
				squares.size(), first.isEmpty() ? "" : " (first at " + first + ")");
		check(differ > 0 && outside == 0, label + ": ON and OFF differ outside the corners of rounded rectangles: " + outside + " of " + differ
				+ " pixels, first at " + first);
	}

	private static boolean inASquare(List<int[]> squares, int x, int y) {
		for (int[] square : squares) {
			if (x >= square[0] && y >= square[1] && x < square[0] + square[2] && y < square[1] + square[2]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * AC-UI-48 [C] (EC-UI-18): with the colour picker and then an open dropdown list, the GUI scale goes 2, 3, 4 and automatic:
	 * no error is logged, and the frame after each change draws from masks and icons made for the new scale and the wheel's
	 * texture made for its size there; the caches hold at most 64 masks, 16 icons and 1 wheel texture.
	 */
	private static void scaleSweep(ClientGameTestContext context) {
		open(context, new Config(1920, 1080, 2));
		String start = "k8bas ui review sweep start " + System.nanoTime();
		String end = "k8bas ui review sweep end " + System.nanoTime();
		LOGGER.info(start);
		for (String option : List.of("npc_search.island_color.hub", "interface.notice_position")) {
			context.runOnClient(client -> client.options.guiScale().set(2));
			context.waitTicks(3);
			openWith(context, option.startsWith("npc_search") ? Category.WAYPOINTS : Category.GENERAL, option);
			for (int scale : new int[] {2, 3, 4, 0}) {
				context.runOnClient(client -> client.options.guiScale().set(scale));
				context.waitTicks(2);
				// A dropdown list closes when its box leaves the view in the new layout (by design): opened again there.
				if (context.computeOnClient(client -> screen(client).overlay()) == null) {
					check(!option.startsWith("npc_search"), option + " at GUI scale " + scale + ": the colour picker stays open");
					LOGGER.info("scale sweep: {} at GUI scale {}: the list closed as its box left the view; opened again", option, scale);
					openWith(context, Category.GENERAL, option);
				}
				int[] state = context.computeOnClient(client -> {
					int[] masks = com.k8bas.skyblockutility.ui.render.CornerTextures.cachedForTests();
					int[] icons = com.k8bas.skyblockutility.ui.render.IconTextures.cachedForTests();
					int[] wheel = com.k8bas.skyblockutility.ui.widget.WheelTexture.cachedForTests();
					int wheelWidth = screen(client).overlay() == null ? 0 : screen(client).overlay().widgets().stream()
							.filter(widget -> widget.getClass().getSimpleName().equals("ColorWheel")).mapToInt(Widget::width).findFirst().orElse(0);
					return new int[] {client.getWindow().getGuiScale(), masks[0], masks[1], icons[0], icons[1], wheel[0], wheel[1], wheelWidth, masks[2], icons[2]};
				});
				String what = option + " at GUI scale " + (scale == 0 ? "auto (" + state[0] + ")" : scale) + ": masks for scale " + state[1] + " ("
						+ state[2] + ", " + state[8] + " another scale's), icons for " + state[3] + " (" + state[4] + ", " + state[9] + " another scale's), wheel textures "
						+ state[5] + " of " + state[6] + " px";
				LOGGER.info("scale sweep: {}", what);
				ran("AC-UI-48 sweep");
				check(state[1] == state[0] && state[2] > 0 && state[2] <= 64 && state[8] == 0, what + ": the masks are the new scale's only, at most 64");
				check(state[4] > 0 && state[4] <= 16 && state[3] == state[0] && state[9] == 0, what + ": the icons are the new scale's only, at most 16");
				if (state[7] > 0) {
					check(state[5] == 1 && state[6] == state[7] * state[0], what + ": one wheel texture, made for its " + state[7] + " GUI px at this scale");
				} else {
					check(state[5] == 0, what + ": no wheel texture kept while the colour picker is closed");
				}
			}
			if (context.computeOnClient(client -> screen(client).overlay()) != null) {
				close(context);
			}
		}
		LOGGER.info(end);
		List<String> errors = ConfigScreenGameTest.errorsBetween(context, start, end);
		check(errors.isEmpty(), "no error logged during the scale sweep: " + errors);
		context.setScreen(() -> null);
	}

	/** A page's outlined controls, with {@code optionId}'s row brought into view: AC-UI-29 and AC-UI-34 on them (checkImage). */
	private static void controlsOn(ClientGameTestContext context, Config config, boolean smooth, String tag, Category category, String optionId) {
		context.runOnClient(client -> screen(client).select(category));
		context.waitTicks(2);
		context.runOnClient(client -> {
			ConfigLayout.OptionRow row = optionRow(screen(client), optionId);
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
			screen(client).scrollArea().setScroll(screen(client).scrollArea().target());
		});
		context.waitTicks(3);
		checkImage(context, config, smooth, tag + "-controls-" + optionId, false, false);
	}

	/**
	 * A focus ring, Tab's third stop (the sidebar, the search box, then the General page's first control): 1 GUI px thick
	 * (AC-UI-34) at GUI scales 2-4, and at 3-4 its corners in the accent colour (AC-UI-29). A click on the header hides it.
	 */
	private static void focusRing(ClientGameTestContext context, Config config, boolean smooth, String tag) {
		int scale = config.scale();
		context.runOnClient(client -> {
			screen(client).select(Category.GENERAL);
			screen(client).scrollTo(0);
		});
		context.waitTicks(3);
		for (int i = 0; i < 3; i++) {
			context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
			context.waitTicks(1);
		}
		context.waitTicks(2);
		int[] ring = context.computeOnClient(client -> {
			Widget focus = screen(client).navFocus();
			check(focus != null && screen(client).page().widgets().contains(focus), "Tab reached a control of the page: " + focus);
			int[] out = new int[4];
			focus.focusRing(out);
			return new int[] {out[0], out[1], out[2], out[3], focus.focusRadius()};
		});
		int accent = context.computeOnClient(client -> Theme.current().accent());
		BufferedImage shot = HairlineProbe.read(context.takeScreenshot(tag + "-focus-ring"));
		if (scale >= 2) {
			ran("AC-UI-34 focus ring");
			int x = (ring[0] + ring[2] / 2) * scale;
			int thick = 0;
			while (KnobProbe.near(shot.getRGB(x, ring[1] * scale + thick), accent)) {
				thick++;
			}
			check(thick == scale, tag + ": the focus ring is " + thick + " px thick, 1 GUI px is " + scale);
		}
		if (scale >= 3) {
			ran("AC-UI-29 corners focus ring");
			int[] corners = OutlineProbe.corners(shot, smooth, ring[0] * scale, ring[1] * scale, ring[2] * scale, ring[3] * scale, ring[4] * scale, scale,
					accent);
			check(corners[0] > 0 && corners[1] == 0, tag + ": " + corners[1] + " of " + corners[0] + " focus ring corner pixels lack the accent");
		}
		int[] header = context.computeOnClient(client -> {
			ConfigLayout.Rect rect = screen(client).frame().header();
			return window(client, rect.x() + 4, rect.y() + 4);
		});
		context.getInput().setCursorPos(header[0], header[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
	}

	/** Opens the overlay of the control {@code optionId} on page {@code category}, brought into view. */
	private static void openWith(ClientGameTestContext context, Category category, String optionId) {
		context.runOnClient(client -> screen(client).select(category));
		context.waitTicks(2);
		context.runOnClient(client -> {
			ConfigLayout.OptionRow row = optionRow(screen(client), optionId);
			screen(client).scrollArea().ensureVisible(row.y(), row.y() + row.height());
			screen(client).scrollArea().setScroll(screen(client).scrollArea().target());
		});
		context.waitTicks(2);
		int[] at = context.computeOnClient(client -> {
			Widget control = screen(client).page().control(optionRow(screen(client), optionId).option());
			return window(client, control.x() + control.width() / 2, control.y() + control.height() / 2);
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(3);
		check(context.computeOnClient(client -> screen(client).overlay()) != null, optionId + ": its overlay opened");
	}

	private static void close(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(2);
		check(context.computeOnClient(client -> screen(client).overlay()) == null, "Esc closed the overlay");
		showFishingSliders(context);
	}

	private static ConfigLayout.OptionRow optionRow(ConfigScreen screen, String optionId) {
		return screen.page().page().rows().stream().filter(r -> r instanceof ConfigLayout.OptionRow o && o.option().id().equals(optionId))
				.map(r -> (ConfigLayout.OptionRow) r).findFirst().orElseThrow(() -> new AssertionError("FAILED: no " + optionId + " on the page"));
	}

	private static Option option(Minecraft client, String id) {
		return com.k8bas.skyblockutility.settings.OptionCatalog.live().stream().flatMap(card -> card.all().stream())
				.filter(option -> option.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("FAILED: no option " + id));
	}

	private static IntSlider sliderOption(Minecraft client, String id) {
		return (IntSlider) option(client, id);
	}

	private static Toggle toggleOption(Minecraft client, String id) {
		return (Toggle) option(client, id);
	}

	/** The middle of GUI pixel (x, y) in window pixels. */
	private static int[] window(Minecraft client, int x, int y) {
		var window = client.getWindow();
		return new int[] {(int) Math.round((x + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth()),
				(int) Math.round((y + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight())};
	}

	private static ConfigScreen screen(Minecraft client) {
		if (!(client.gui.screen() instanceof ConfigScreen screen)) {
			throw new AssertionError("FAILED: the settings screen is not open: " + client.gui.screen());
		}
		return screen;
	}

	private static void ran(String check) {
		RAN.merge(check, 1, Integer::sum);
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
