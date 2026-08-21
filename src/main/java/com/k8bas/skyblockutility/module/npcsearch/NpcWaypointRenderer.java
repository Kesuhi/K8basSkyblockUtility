package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders a floating, see-through-walls name label (plus live distance) at each active fixed
 * NPC's world position — the "waypoint" for NPC Search's fixed entries. Modeled directly on the
 * technique Firmament (a real mod already built against this exact Minecraft version, which has
 * its own NPC waypoint feature) uses: PoseStack/MultiBufferSource world rendering hasn't moved to
 * the newer split render-state API the way 2D GUI rendering has, camera-relative translation
 * still has to be applied by hand, and Font.DisplayMode.SEE_THROUGH is what gives vanilla text
 * its "visible through walls" look — confirmed against the real jar rather than assumed.
 */
public final class NpcWaypointRenderer {
	private static volatile List<NpcRule> activeWaypoints = List.of();

	private NpcWaypointRenderer() {
	}

	/** Called by NpcSearchModule whenever its rule set changes — every fixed NpcRule, not
	 *  pre-filtered. Enabled/island filtering happens fresh every frame in the render loop below
	 *  (cheap: string compares over a couple dozen entries, not an entity scan), so a waypoint
	 *  appears/disappears immediately when the player changes island without needing a separate
	 *  "island changed" event. Pass an empty list while the module itself is disabled.
	 *
	 *  Also (re)computes each waypoint's cachedPos here — once per rebuild instead of once per
	 *  render frame, since a fixed rule's position never changes without a rebuild happening
	 *  anyway (there's no coordinate-editing UI). */
	public static void setActiveWaypoints(List<NpcRule> waypoints) {
		for (NpcRule waypoint : waypoints) {
			waypoint.cachedPos = new Vec3(waypoint.x, waypoint.y + 1.5, waypoint.z);
		}
		activeWaypoints = waypoints;
	}

	public static void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
			List<NpcRule> waypoints = activeWaypoints;
			if (waypoints.isEmpty()) {
				return;
			}

			PoseStack matrices = context.poseStack();
			MultiBufferSource.BufferSource buffers = context.bufferSource();
			CameraRenderState camera = context.levelState().cameraRenderState;

			String currentIsland = IslandTracker.getCurrentIsland();
			matrices.pushPose();
			matrices.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z);
			for (NpcRule waypoint : waypoints) {
				if (!waypoint.enabled) {
					continue;
				}
				if (waypoint.island != null && !waypoint.island.equals(currentIsland)) {
					continue;
				}
				renderWaypoint(matrices, buffers, camera, waypoint);
			}
			matrices.popPose();
			buffers.endBatch();
		});
	}

	private static void renderWaypoint(PoseStack matrices, MultiBufferSource.BufferSource buffers,
			CameraRenderState camera, NpcRule waypoint) {
		Vec3 pos = waypoint.cachedPos;
		double dx = pos.x - camera.pos.x;
		double dy = pos.y - camera.pos.y;
		double dz = pos.z - camera.pos.z;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

		matrices.pushPose();
		matrices.translate(pos.x, pos.y, pos.z);
		// Billboard towards the camera, and pull the label closer than 10 blocks so it doesn't
		// visually shrink into the distance the way real world geometry would. Inlined as scalar
		// math instead of two intermediate Vec3s (subtract + scale) — this runs per waypoint
		// per frame.
		double pull = distance < 10 || distance == 0 ? 0.0 : -(distance - 10.0) / distance;
		matrices.translate(dx * pull, dy * pull, dz * pull);
		matrices.mulPose(camera.orientation);
		matrices.scale(0.025F, -0.025F, 1F);

		int textColor = ARGB.opaque(waypoint.color);
		drawLabelLine(matrices, buffers, Component.literal(waypoint.label), 0, textColor);
		drawLabelLine(matrices, buffers, Component.literal(Math.round(distance) + "m"), 1, textColor);

		matrices.popPose();
	}

	private static void drawLabelLine(PoseStack matrices, MultiBufferSource.BufferSource buffers,
			Component text, int lineIndex, int textColor) {
		Font font = Minecraft.getInstance().font;
		int width = font.width(text);
		float lineHeight = font.lineHeight;

		matrices.pushPose();
		matrices.translate(-width / 2F, lineIndex * (lineHeight + 1), 0F);

		VertexConsumer background = buffers.getBuffer(RenderTypes.textBackgroundSeeThrough());
		Matrix4f pose = matrices.last().pose();
		int backgroundColor = 0x70202020;
		background.addVertex(pose, -1F, -1F, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
		background.addVertex(pose, -1F, lineHeight, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
		background.addVertex(pose, width, lineHeight, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
		background.addVertex(pose, width, -1F, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
		matrices.translate(0F, 0F, 0.01F);

		font.drawInBatch(text, 0F, 0F, textColor, false, matrices.last().pose(), buffers,
				Font.DisplayMode.SEE_THROUGH, 0, LightCoordsUtil.FULL_BRIGHT);
		matrices.popPose();
	}
}
