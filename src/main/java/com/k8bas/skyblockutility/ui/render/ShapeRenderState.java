package com.k8bas.skyblockutility.ui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

/**
 * One GUI element of a rounded shape (REQ-UI-27, T3.15): its quads in screen pixels under the shape's own pose, one
 * colour, and the bounds of the whole shape ({@link ShapePlan}). {@code uvs} is null for an untextured element and
 * the scissor null without one. The vertices go in the corner order of the game's coloured rectangle and blit, with
 * texture coordinates only for a textured element. Built and read later on the render thread, so the pose and arrays
 * are the element's own copies.
 */
public record ShapeRenderState(
	RenderPipeline pipeline,
	TextureSetup textureSetup,
	Matrix3x2fc pose,
	int[] quads,
	float[] uvs,
	int color,
	ScreenRectangle scissorArea,
	ScreenRectangle bounds
) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer consumer) {
		for (int i = 0; i < quads.length; i += 4) {
			int x0 = quads[i];
			int y0 = quads[i + 1];
			int x1 = quads[i + 2];
			int y1 = quads[i + 3];
			if (uvs == null) {
				consumer.addVertexWith2DPose(pose, x0, y0).setColor(color);
				consumer.addVertexWith2DPose(pose, x0, y1).setColor(color);
				consumer.addVertexWith2DPose(pose, x1, y1).setColor(color);
				consumer.addVertexWith2DPose(pose, x1, y0).setColor(color);
			} else {
				float u0 = uvs[i];
				float v0 = uvs[i + 1];
				float u1 = uvs[i + 2];
				float v1 = uvs[i + 3];
				consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
				consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
				consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
				consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
			}
		}
	}
}
