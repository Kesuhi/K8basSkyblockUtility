package com.k8bas.skyblockutility.module.npcsearch;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
 * NPC's world position — the "waypoint" for NPC Search's fixed entries. The label is submitted
 * to the level's submit node collector during COLLECT_SUBMITS (the 26.x render-state pipeline;
 * 26.2 removes the immediate-mode buffer path), with camera-relative translation applied by hand.
 * Font.DisplayMode.SEE_THROUGH is what gives vanilla text its "visible through walls" look.
 * Only fixed coordinates are drawn through walls (P4).
 */
public final class NpcWaypointRenderer {
	/** The background quad is submitted in a lower order than the text so the text draws on top. */
	private static final int BACKGROUND_ORDER = 0;
	private static final int TEXT_ORDER = 1;
	private static final int BACKGROUND_COLOR = 0x70202020;

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
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			List<NpcRule> waypoints = activeWaypoints;
			if (waypoints.isEmpty()) {
				return;
			}

			PoseStack matrices = context.poseStack();
			SubmitNodeCollector submits = context.submitNodeCollector();
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
				submitWaypoint(matrices, submits, camera, waypoint);
			}
			matrices.popPose();
		});
	}

	/** How far to pull a label towards the camera so that beyond 10 blocks it keeps its 10-block
	 *  size instead of shrinking into the distance (0 up to 10 blocks, never NaN). */
	static double pullFactor(double distance) {
		return distance < 10 || distance == 0 ? 0.0 : -(distance - 10.0) / distance;
	}

	private static void submitWaypoint(PoseStack matrices, SubmitNodeCollector submits, CameraRenderState camera, NpcRule waypoint) {
		Vec3 pos = waypoint.cachedPos;
		double dx = pos.x - camera.pos.x;
		double dy = pos.y - camera.pos.y;
		double dz = pos.z - camera.pos.z;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

		matrices.pushPose();
		matrices.translate(pos.x, pos.y, pos.z);
		// Billboard towards the camera. Inlined as scalar math instead of two intermediate Vec3s
		// (subtract + scale) — this runs per waypoint per frame.
		double pull = pullFactor(distance);
		matrices.translate(dx * pull, dy * pull, dz * pull);
		matrices.mulPose(camera.orientation);
		matrices.scale(0.025F, -0.025F, 1F);

		int textColor = ARGB.opaque(waypoint.color);
		submitLabelLine(matrices, submits, Component.literal(waypoint.label), 0, textColor);
		submitLabelLine(matrices, submits, Component.literal(Math.round(distance) + "m"), 1, textColor);

		matrices.popPose();
	}

	private static void submitLabelLine(PoseStack matrices, SubmitNodeCollector submits, Component text, int lineIndex, int textColor) {
		Font font = Minecraft.getInstance().font;
		int width = font.width(text);
		float lineHeight = font.lineHeight;

		matrices.pushPose();
		matrices.translate(-width / 2F, lineIndex * (lineHeight + 1), 0F);

		submits.order(BACKGROUND_ORDER).submitCustomGeometry(matrices, RenderTypes.textBackgroundSeeThrough(), (pose, background) -> {
			Matrix4f matrix = pose.pose();
			background.addVertex(matrix, -1F, -1F, 0F).setColor(BACKGROUND_COLOR).setLight(LightCoordsUtil.FULL_BRIGHT);
			background.addVertex(matrix, -1F, lineHeight, 0F).setColor(BACKGROUND_COLOR).setLight(LightCoordsUtil.FULL_BRIGHT);
			background.addVertex(matrix, width, lineHeight, 0F).setColor(BACKGROUND_COLOR).setLight(LightCoordsUtil.FULL_BRIGHT);
			background.addVertex(matrix, width, -1F, 0F).setColor(BACKGROUND_COLOR).setLight(LightCoordsUtil.FULL_BRIGHT);
		});
		matrices.translate(0F, 0F, 0.01F);

		submits.order(TEXT_ORDER).submitText(matrices, 0F, 0F, text.getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH,
				LightCoordsUtil.FULL_BRIGHT, textColor, 0, 0);
		matrices.popPose();
	}
}
