package com.k8bas.skyblockutility.ui.render;

import com.k8bas.skyblockutility.ui.screen.ConfigLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-UI-18 (T2.9): scaling about a centre and back, for drawing and for mapping the mouse. */
class ScaleAboutTest {
	@Test
	void theCentreStaysPut() {
		assertEquals(320.0, ScaleAbout.toScreen(320, 320, 0.9F));
		assertEquals(320.0, ScaleAbout.toLocal(320, 320, 0.9F));
	}

	@Test
	void toLocalUndoesToScreen() {
		for (float scale : new float[] {0.90F, 0.95F, 1F}) {
			for (int point = -2000; point <= 2000; point += 7) {
				double back = ScaleAbout.toLocal(ScaleAbout.toScreen(point, 427, scale), 427, scale);
				assertEquals(point, back, 1e-9, "scale " + scale + ", point " + point);
			}
		}
	}

	@Test
	void aScaleOfOneZeroNegativeOrNaNIsIdentity() {
		for (float scale : new float[] {1F, 0F, -0.5F, Float.NaN}) {
			assertEquals(10.0, ScaleAbout.toScreen(10, 300, scale), "scale " + scale);
			assertEquals(10.0, ScaleAbout.toLocal(10, 300, scale), "scale " + scale);
		}
	}

	/** AC-UI-02 during the open: the panel, scaled about the screen centre, never leaves its own laid-out rectangle. */
	@Test
	void theScaledPanelStaysInsideTheLaidOutPanel() {
		int[][] guiSizes = {{854, 480}, {427, 240}, {1920, 1080}, {960, 540}, {640, 360}, {480, 270}};
		for (int[] size : guiSizes) {
			ConfigLayout.Rect panel = ConfigLayout.frame(size[0], size[1]).panel();
			double cx = size[0] / 2.0;
			double cy = size[1] / 2.0;
			for (float scale = 0.90F; scale <= 1F; scale += 0.01F) {
				double left = ScaleAbout.toScreen(panel.x(), cx, scale);
				double right = ScaleAbout.toScreen(panel.right(), cx, scale);
				double top = ScaleAbout.toScreen(panel.y(), cy, scale);
				double bottom = ScaleAbout.toScreen(panel.bottom(), cy, scale);
				String where = size[0] + "x" + size[1] + " at " + scale;
				assertTrue(left >= panel.x() - 0.5 && right <= panel.right() + 0.5, where);
				assertTrue(top >= panel.y() - 0.5 && bottom <= panel.bottom() + 0.5, where);
				assertTrue(left >= ConfigLayout.MARGIN - 0.5 && top >= ConfigLayout.MARGIN - 0.5, "the 4 px margin: " + where);
			}
		}
	}
}
