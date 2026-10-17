package com.k8bas.skyblockutility.ui.widget;

import com.k8bas.skyblockutility.ui.render.UiClock;
import com.k8bas.skyblockutility.ui.render.UiTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-UI-36 (REQ-UI-35, T3.26): the scroll easing. A whole-px target and a shown offset that eases toward it with a time
 * constant of about 50 ms, from the UI clock, the same at any frame rate, never past the target and at it from 400 ms on;
 * EC-UI-18 (a GUI scale change jumps), EC-UI-23 (a hitch) and EC-UI-25 (a drag stops a glide).
 */
class SmoothScrollTest {
	private final long[] now = {10_000};
	private final boolean[] motion = {true};
	private final UiTime time = UiTime.of(() -> now[0], () -> motion[0]);
	private UiTime previous;

	@BeforeEach
	void enter() {
		previous = UiClock.enter(time);
	}

	@AfterEach
	void leave() {
		UiClock.leave(previous);
	}

	/** REQ-UI-49's part of a glide left after {@code ms}: the exponential less its value at 400 ms, rescaled to start at 1. */
	private static float glideLeft(long ms) {
		double end = Math.exp(-400 / 50.0);
		return (float) ((Math.exp(-ms / 50.0) - end) / (1 - end));
	}

	private SmoothScroll scroll(int max) {
		SmoothScroll scroll = new SmoothScroll(time);
		scroll.setMax(max);
		return scroll;
	}

	/**
	 * AC-UI-52 (REQ-UI-49, T3.42): a glide ends exactly on its target, with no last step. Over 10 000 GUI px the shown offset
	 * rises monotonically, is below the target at 399 ms and at it from 400 ms on, and its last 4 ms step is no larger than
	 * the one before (a plain exponential cut off at 400 ms jumps the 0.03 % still left, here about 3.6 GUI px).
	 */
	@Test
	void aLongGlideEndsOnItsTargetWithoutALastStep() {
		SmoothScroll scroll = scroll(20_000);
		long start = now[0];
		scroll.glideTo(10_000);
		float previous = 0F;
		for (int t = 0; t <= 500; t++) {
			now[0] = start + t;
			float shown = scroll.shown();
			assertTrue(shown >= previous, t + " ms: " + shown + " after " + previous + ", monotonic");
			assertTrue(shown <= 10_000F, t + " ms: never past the target");
			previous = shown;
		}
		now[0] = start + 399;
		assertTrue(scroll.shown() < 10_000F, "below the target at 399 ms: " + scroll.shown());
		for (int t : new int[] {400, 401, 450, 1000}) {
			now[0] = start + t;
			assertEquals(10_000F, scroll.shown(), t + " ms: at the target");
		}
		// The frames at 392, 396 and 400 ms.
		float[] at = new float[3];
		for (int i = 0; i < 3; i++) {
			now[0] = start + 392 + 4 * i;
			at[i] = scroll.shown();
		}
		float last = at[2] - at[1];
		float before = at[1] - at[0];
		assertTrue(last <= before, "the last step " + last + " is no larger than the one before, " + before);
	}

	@Test
	void theShownOffsetDoesNotDependOnTheFrameRate() {
		List<Float> at100 = new ArrayList<>();
		Random random = new Random(26);
		for (int fps : new int[] {30, 60, 144, 240, 0}) {
			now[0] = 10_000;
			// Through a scroll area, as a frame uses it: bounds, content and the scroll read each frame.
			ScrollArea area = new ScrollArea();
			area.setBounds(0, 0, 300, 200);
			area.setContentHeight(1200);
			area.ensureVisible(420, 440);
			long t = 0;
			while (t < 100) {
				t += fps == 0 ? 1 + random.nextInt(50) : Math.max(1, Math.round(1000.0 / fps));
				now[0] = 10_000 + Math.min(t, 100);
				area.setBounds(0, 0, 300, 200);
				area.setContentHeight(1200);
				area.scroll();
				area.drawnScroll(2);
			}
			now[0] = 10_100;
			assertEquals(240, area.target());
			at100.add(area.drawnScroll(1000));
		}
		for (float shown : at100) {
			assertEquals(at100.get(0), shown, 0.01F, "after 100 ms at every frame rate: " + at100);
		}
		assertTrue(at100.get(0) > 200F && at100.get(0) < 240F, "about 1 - e^-2 of the way: " + at100.get(0));
	}

	@Test
	void itNeverPassesTheTargetAndIsThereFrom400Ms() {
		for (int[] glide : new int[][] {{0, 240}, {240, 60}}) {
			int target = glide[1];
			boolean down = glide[1] > glide[0];
			now[0] = 10_000;
			SmoothScroll scroll = scroll(1000);
			scroll.jumpTo(glide[0]);
			scroll.glideTo(target);
			float last = scroll.shown();
			for (int t = 0; t <= 1000; t++) {
				now[0] = 10_000 + t;
				float shown = scroll.shown();
				assertTrue(down ? shown >= last && shown <= target : shown <= last && shown >= target, t + " ms: " + shown);
				for (int scale = 1; scale <= 4; scale++) {
					float drawn = scroll.drawn(scale);
					assertTrue(down ? drawn <= target : drawn >= target, t + " ms, scale " + scale + ": drawn " + drawn);
				}
				if (t >= SmoothScroll.SETTLE_MS) {
					assertEquals(target, shown, t + " ms");
					assertEquals(target, scroll.drawn(2), t + " ms, drawn");
				}
				last = shown;
			}
		}
	}

	@Test
	void aNotchDuringTheGlideContinuesFromTheShownOffset() {
		SmoothScroll scroll = scroll(1000);
		scroll.glideBy(240);
		now[0] += 50;
		float before = scroll.shown();
		scroll.glideBy(24);
		assertEquals(264, scroll.target());
		assertEquals(before, scroll.shown(), 1e-4F, "no jump");
		now[0] += 10;
		assertTrue(scroll.shown() > before, "and on toward the new target");
	}

	@Test
	void aThumbDragSetsBothAndShrinkingTheContentClampsBoth() {
		SmoothScroll scroll = scroll(1000);
		scroll.glideTo(600);
		now[0] += 30;
		scroll.jumpTo(100);
		assertEquals(100, scroll.target());
		assertEquals(100F, scroll.shown(), "a thumb drag sets the shown offset too");
		scroll.glideTo(800);
		now[0] += 30;
		scroll.setMax(300);
		assertEquals(300, scroll.target(), "the target is clamped");
		assertEquals(300F, scroll.shown(), "and the shown offset with it");
		scroll.glideTo(-50);
		assertEquals(0, scroll.target(), "never above the top");
	}

	@Test
	void withAnimationsOffTheShownOffsetIsTheTargetAtOnce() {
		motion[0] = false;
		SmoothScroll scroll = scroll(1000);
		scroll.glideTo(240);
		assertEquals(240F, scroll.shown());
		assertEquals(240, scroll.laidOut());
		motion[0] = true;
		assertEquals(240F, scroll.shown(), "switched on again, it does not glide back");
	}

	/** EC-UI-23: a 500 ms hitch lands on the target; at 1,000 frames per second it never passes it. */
	@Test
	void aHitchLandsOnTheTarget() {
		SmoothScroll scroll = scroll(1000);
		scroll.glideTo(240);
		now[0] += 500;
		assertEquals(240F, scroll.shown());
		scroll.glideTo(60);
		for (int t = 0; t < 400; t++) {
			now[0]++;
			assertTrue(scroll.shown() >= 60F, "at " + t + " ms");
		}
	}

	/** EC-UI-18: a GUI scale change mid-glide jumps to the target, with no glide from the old scale's position. */
	@Test
	void aScaleChangeJumpsToTheTarget() {
		SmoothScroll scroll = scroll(1000);
		scroll.setScale(2);
		scroll.glideTo(240);
		now[0] += 30;
		scroll.setScale(2);
		assertTrue(scroll.shown() < 240F, "the same scale: still gliding");
		scroll.setScale(3);
		assertEquals(240F, scroll.shown(), "a new scale: at the target");
	}

	/** REQ-UI-34/35: drawn on the physical-pixel grid, at the target within half a physical px; the layout whole GUI px. */
	@Test
	void itIsDrawnOnThePhysicalGrid() {
		SmoothScroll scroll = scroll(1000);
		scroll.glideTo(240);
		int halves = 0;
		for (int t = 0; t <= 400; t += 10) {
			now[0] = 10_000 + t;
			float drawn = scroll.drawn(2);
			assertEquals(Math.round(drawn * 2), drawn * 2, 1e-4F, t + " ms: " + drawn);
			assertTrue(Math.abs(drawn - scroll.laidOut()) < 1F, t + " ms: drawn " + drawn + ", laid out " + scroll.laidOut());
			halves += Math.abs(drawn - Math.round(drawn)) > 0.25F ? 1 : 0;
			if (Math.abs(scroll.shown() - 240) < 0.25F) {
				assertEquals(240F, drawn, "within half a physical px it is at the target");
			}
		}
		assertTrue(halves > 0, "some frames lie on a half GUI px");
	}

	/** AC-UI-36: bringing a control into view glides there: strictly between at 50 ms, at the target from 400 ms on. */
	@Test
	void bringingAControlIntoViewGlides() {
		ScrollArea area = new ScrollArea();
		area.setBounds(0, 0, 300, 200);
		area.setContentHeight(1000);
		area.ensureVisible(500, 520);
		assertEquals(320, area.target(), "the target shows the control's bottom");
		assertEquals(0, area.scroll(), "the layout starts where it was");
		now[0] += 50;
		assertTrue(area.scroll() > 0 && area.scroll() < 320, "50 ms: " + area.scroll());
		now[0] += 350;
		assertEquals(320, area.scroll(), "400 ms");
	}

	/** REQ-UI-35: an open dropdown list glides by whole rows; clicks meet the rows where they are laid out. */
	@Test
	void aDropdownListGlides() {
		DropdownModel model = new DropdownModel(40, 8, time);
		model.open(0);
		model.scrollBy(3);
		assertEquals(3, model.scroll(), "the target is three rows down");
		assertEquals(0, model.laidOutPx(14), "the glide starts where the list was");
		now[0] += 50;
		int laidOut = model.laidOutPx(14);
		assertTrue(laidOut > 0 && laidOut < 3 * 14, "50 ms: " + laidOut);
		for (int k = 0; k < 10; k++) {
			double middle = k * 14 - laidOut + 7;
			if (middle >= 0 && middle < 8 * 14) {
				assertEquals(k, model.rowAt(middle, 14), "a click at the middle of row " + k + " as laid out meets it");
			}
		}
		// The easing written out: three rows of 14 px, 50 ms in; drawn on the half-px grid, laid out at whole px.
		float shownPx = 42F * (1F - glideLeft(50));
		assertEquals(Math.round(shownPx) - Math.round(shownPx * 2) / 2F, model.drawShift(14, 2), 1e-4F, "drawn where the glide is: " + shownPx);
		float drawn = model.drawnPx(14, 2);
		assertEquals(Math.round(drawn * 2), drawn * 2, 1e-4F, "drawn on the physical-pixel grid: " + drawn);
		now[0] += 350;
		assertEquals(3 * 14, model.laidOutPx(14), "400 ms");
		assertEquals(3 * 14F, model.drawnPx(14, 2));
		model.open(20);
		assertEquals(model.scroll() * 14, model.laidOutPx(14), "opening shows its entry at once");
	}

	/** REQ-UI-35: the thumb and the track set the scroll at once, also mid-glide; EC-UI-25: the wheel is ignored during a thumb drag. */
	@Test
	void theThumbAndTheTrackSetTheScrollAtOnce() {
		ScrollArea area = new ScrollArea();
		area.setBounds(0, 0, 300, 200);
		area.setContentHeight(2000);
		assertTrue(area.scroll(150, 100, -5, false), "five notches");
		now[0] += 30;
		int laidOut = area.scroll();
		assertTrue(laidOut > 0 && laidOut < 120, "gliding: " + laidOut);
		assertTrue(area.press(298, 2, 0), "a press on the thumb at the top");
		assertEquals(area.target(), area.scroll(), "the glide stopped under the thumb");
		area.drag(298, 102);
		assertEquals(area.target(), area.scroll(), "a thumb drag sets both");
		int dragged = area.scroll();
		assertTrue(dragged > laidOut, "and moved the scroll: " + dragged);
		assertTrue(area.scroll(150, 100, -3, false), "the wheel during the drag is taken");
		assertEquals(dragged, area.target(), "and ignored");
		area.release(298, 102);
		assertTrue(area.press(298, 195, 0), "a press on the track");
		assertEquals(area.target(), area.scroll(), "jumps there");
		assertTrue(area.scroll() > dragged);
		area.release(298, 195);
		// At the bottom now: a notch up.
		area.scroll(150, 100, 1, false);
		assertTrue(area.target() < area.scroll(), "after the release the wheel glides again");

		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, index -> { });
		list.setBounds(0, 0, 160, 200);
		list.scroll(50, 100, -5, false);
		now[0] += 30;
		assertTrue(list.press(158, 2, 0), "a press on the list's thumb");
		list.drag(158, 120);
		int listDragged = list.rows().scroll();
		now[0] += 400;
		assertEquals(listDragged, list.rows().scroll(), "a thumb drag sets both");
		list.scroll(50, 100, -3, false);
		assertEquals(listDragged, list.rows().scroll(), "the wheel during the drag is ignored");
		list.release(158, 120);
	}

	/** EC-UI-18 on a widget: a GUI scale change mid-glide puts the list on its target before it is laid out. */
	@Test
	void aScaleChangeJumpsTheListToItsTarget() {
		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, index -> { });
		list.setBounds(0, 0, 160, 200);
		list.setScale(2);
		list.scroll(50, 100, -3, false);
		now[0] += 30;
		assertTrue(list.rows().scroll() < 3 * 3 * 18, "gliding");
		// The easing written out: 162 px of glide, 30 ms in; drawn on the half-px grid, laid out at whole px.
		float shown = 162F * (1F - glideLeft(30));
		float expected = Math.round(shown) - Math.round(shown * 2) / 2F;
		assertEquals(expected, list.drawShift(2), 1e-4F, "drawn where the glide is, " + shown + ", on the grid");
		list.setScale(3);
		assertEquals(3 * 3 * 18, list.rows().scroll(), "at the target at once");
		assertEquals(0F, list.drawShift(3));
	}

	/** REQ-UI-35: a list brings a row into view by gliding, down and back up. */
	@Test
	void aListGlidesToARow() {
		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, index -> { });
		list.setBounds(0, 0, 160, 200);
		list.ensureVisible(40);
		int target = 41 * 18 - 200;
		assertEquals(0, list.rows().scroll(), "it starts where it was");
		now[0] += 50;
		int at50 = list.rows().scroll();
		assertTrue(at50 > 0 && at50 < target, "50 ms: " + at50);
		now[0] += 350;
		assertEquals(target, list.rows().scroll(), "400 ms: row 40's bottom at the list's bottom");
		list.ensureVisible(2);
		now[0] += 50;
		int up = list.rows().scroll();
		assertTrue(up > 2 * 18 && up < target, "back up, gliding: " + up);
		now[0] += 350;
		assertEquals(2 * 18, list.rows().scroll(), "row 2 at the top");
	}

	/** EC-UI-25: a press that starts during a glide stops it where it is laid out, so the pressed row stays under the pointer. */
	@Test
	void aPressDuringAGlideStopsIt() {
		List<Integer> clicked = new ArrayList<>();
		VirtualList list = new VirtualList(18, () -> 5000, (graphics, font, index, x, y, w, h, hovered) -> { }, clicked::add);
		list.setBounds(0, 0, 160, 200);
		assertTrue(list.scroll(50, 100, -10, false), "ten notches down");
		now[0] += 40;
		int laidOut = list.rows().scroll();
		assertTrue(laidOut > 0 && laidOut < 30 * 18, "mid-glide: " + laidOut);
		int under = list.rows().rowAt(100);
		// A moment after that frame: the glide moved on, but the press meets what was laid out (and drawn) then.
		now[0] += 8;
		list.press(50, 100, 0);
		assertEquals(List.of(under), clicked, "the row under the pointer");
		now[0] += 400;
		assertEquals(laidOut, list.rows().scroll(), "the glide stopped where it was");
	}
}
