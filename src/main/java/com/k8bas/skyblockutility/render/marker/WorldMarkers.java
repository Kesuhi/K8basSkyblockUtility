package com.k8bas.skyblockutility.render.marker;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The world marker toolkit (T3.0b): collects the markers of every active provider each frame and
 * submits them during COLLECT_SUBMITS (the 26.x render-state pipeline), with camera-relative
 * translation applied by hand. Labels go into Fabric's AFTER_TERRAIN phase, so translucent terrain
 * (water, glass, ice) drawn earlier cannot tint them (REQ-MARK-03).
 * <ul>
 *   <li>A see-through label (fixed anchors only) is drawn pulled in to 10 blocks from the camera, so
 *       its on-screen size stays constant and it stays inside the far plane (EC-MARK-02).</li>
 *   <li>A depth-tested label is drawn at its true position and grown with the distance instead, so it
 *       has the same on-screen size and every block in front of it hides it (REQ-MARK-02).</li>
 * </ul>
 * Markers outside the view are not submitted (REQ-MARK-07), labels hide with F1 (EC-MARK-06), and
 * every provider is reset on world change, server switch and disconnect; markers on an entity that
 * is gone or in another world are skipped (REQ-MARK-08). No chunk or block data is read (EC-MARK-02).
 */
public final class WorldMarkers {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/markers");
	/** The background quad is submitted in a lower order than the text so the text draws on top. */
	private static final int BACKGROUND_ORDER = 0;
	private static final int TEXT_ORDER = 1;
	private static final float SCALE = 0.025F;
	/** Beyond this distance a label keeps its on-screen size. */
	private static final double CONSTANT_SIZE_DISTANCE = 10.0;
	/** Added around a label's box for the view test (in blocks at the natural size), so it never pops at the screen edge. */
	private static final double CULL_MARGIN = 0.5;
	/** No glyph of the vanilla font is wider than this, so the view test never drops a visible label. */
	private static final int MAX_GLYPH_WIDTH = 9;
	/** "<n>m" components for whole metres up to this distance are made once. */
	private static final int CACHED_DISTANCES = 1024;
	private static final Component[] DISTANCE_LINES = new Component[CACHED_DISTANCES + 1];

	private static final List<MarkerProvider> PROVIDERS = new CopyOnWriteArrayList<>();
	private static final Set<MarkerProvider> FAILED = ConcurrentHashMap.newKeySet();
	private static volatile boolean hideLabelsWithHud = true;
	private static volatile boolean recordFrames;
	private static volatile Frame lastFrame = Frame.EMPTY;
	private static volatile Frame firstFrameAfterReset = Frame.EMPTY;
	private static int framesSinceReset;

	/**
	 * What a frame did, for gametests: labels submitted, labels outside the view, and (only while
	 * recording) the distance texts drawn.
	 */
	public record Frame(int submitted, int culled, List<String> distanceTexts) {
		static final Frame EMPTY = new Frame(0, 0, List.of());
	}

	private WorldMarkers() {
	}

	public static void add(MarkerProvider provider) {
		PROVIDERS.add(provider);
	}

	public static void remove(MarkerProvider provider) {
		PROVIDERS.remove(provider);
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(WorldMarkers::submit);
		// IslandTracker does not publish a change it does not see (say, a world change off SkyBlock),
		// so the world and connection events are watched here as well.
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> resetAll());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(WorldMarkers::resetAll));
		IslandTracker.onChange(snapshot -> resetAll());
	}

	/** For gametests, whose screenshots hide the HUD: false keeps labels visible while it is hidden. */
	public static void setHideLabelsWithHud(boolean hide) {
		hideLabelsWithHud = hide;
	}

	/** For gametests: records the distance texts of each frame in {@link #lastFrame()}. */
	public static void setRecordFrames(boolean record) {
		recordFrames = record;
	}

	public static Frame lastFrame() {
		return lastFrame;
	}

	/** For gametests: the first frame after the last world change, server switch or disconnect (AC-MARK-07). */
	public static Frame firstFrameAfterReset() {
		return firstFrameAfterReset;
	}

	private static void resetAll() {
		reset(PROVIDERS);
		framesSinceReset = 0;
	}

	static void reset(List<MarkerProvider> providers) {
		for (MarkerProvider provider : providers) {
			try {
				provider.reset();
			} catch (RuntimeException e) {
				failedOnce(provider, e);
			}
		}
	}

	/** The markers of every active provider, in provider order. A provider that throws is skipped. */
	static List<Marker> collect(List<MarkerProvider> providers) {
		List<Marker> markers = new ArrayList<>();
		for (MarkerProvider provider : providers) {
			try {
				if (provider.isActive()) {
					provider.collect(markers::add);
				}
			} catch (RuntimeException e) {
				failedOnce(provider, e);
			}
		}
		return markers;
	}

	private static void failedOnce(MarkerProvider provider, RuntimeException e) {
		if (FAILED.add(provider)) {
			LOGGER.warn("Marker provider {} failed; its markers are skipped", provider.getClass().getName(), e);
		}
	}

	/** How far to pull a see-through label towards the camera so that beyond 10 blocks it keeps its
	 *  10-block size instead of shrinking into the distance (0 up to 10 blocks, never NaN). */
	static double pullFactor(double distance) {
		return distance < CONSTANT_SIZE_DISTANCE || distance == 0 ? 0.0 : -(distance - CONSTANT_SIZE_DISTANCE) / distance;
	}

	/** The label scale at a distance: see-through labels are pulled in instead; depth-tested ones grow. */
	static float labelScale(double distance, boolean seeThrough) {
		return seeThrough ? SCALE : (float) (SCALE * Math.max(1.0, distance / CONSTANT_SIZE_DISTANCE));
	}

	/** Whole metres from the player's position (not the camera) to the anchor, rounded half-up (REQ-MARK-05). */
	static String distanceText(Vec3 player, Vec3 anchor) {
		return distanceMetres(player, anchor) + "m";
	}

	private static long distanceMetres(Vec3 player, Vec3 anchor) {
		return Math.round(player.distanceTo(anchor));
	}

	private static Component distanceLine(long metres) {
		if (metres < 0 || metres > CACHED_DISTANCES) {
			return Component.literal(metres + "m");
		}
		Component line = DISTANCE_LINES[(int) metres];
		if (line == null) {
			line = Component.literal(metres + "m");
			DISTANCE_LINES[(int) metres] = line;
		}
		return line;
	}

	private static void submit(LevelRenderContext context) {
		Frame frame = submitMarkers(context);
		lastFrame = frame;
		if (++framesSinceReset == 1) {
			firstFrameAfterReset = frame;
		}
	}

	private static Frame submitMarkers(LevelRenderContext context) {
		List<Marker> markers = collect(PROVIDERS);
		if (markers.isEmpty()) {
			return Frame.EMPTY;
		}
		Minecraft client = Minecraft.getInstance();
		CameraRenderState camera = context.levelState().cameraRenderState;
		boolean labelsHidden = hideLabelsWithHud && client.gui.hud.isHidden();
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 player = client.player == null ? null : client.player.getPosition(partialTick);
		boolean record = recordFrames;
		List<String> distanceTexts = record ? new ArrayList<>() : List.of();
		int submitted = 0;
		int culled = 0;

		PoseStack matrices = context.poseStack();
		matrices.pushPose();
		matrices.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z);
		for (Marker marker : markers) {
			MarkerLabel label = marker.label();
			if (label == null || labelsHidden || !inThisWorld(marker.anchor(), client)) {
				continue;
			}
			Vec3 pos = marker.anchor().position(partialTick);
			Component distanceLine = label.distanceLine() && player != null ? distanceLine(distanceMetres(player, pos)) : null;
			if (submitLabel(matrices, context.submitNodeCollector(), camera, client.font, pos, label, distanceLine)) {
				submitted++;
				if (record && distanceLine != null) {
					distanceTexts.add(distanceLine.getString());
				}
			} else {
				culled++;
			}
		}
		matrices.popPose();
		return new Frame(submitted, culled, record ? List.copyOf(distanceTexts) : List.of());
	}

	/** False for a marker on an entity that is gone or belongs to another world (REQ-MARK-08). */
	private static boolean inThisWorld(MarkerAnchor anchor, Minecraft client) {
		if (anchor instanceof MarkerAnchor.OfEntity onEntity) {
			Entity entity = onEntity.entity();
			return !entity.isRemoved() && entity.level() == client.level;
		}
		return true;
	}

	/** Submits one label; false if it is outside the view and was skipped. */
	private static boolean submitLabel(PoseStack matrices, SubmitNodeCollector submits, CameraRenderState camera, Font font,
			Vec3 pos, MarkerLabel label, Component distanceLine) {
		double dx = pos.x - camera.pos.x;
		double dy = pos.y - camera.pos.y;
		double dz = pos.z - camera.pos.z;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		boolean seeThrough = label.seeThrough();
		double pull = seeThrough ? pullFactor(distance) : 0.0;
		float scale = labelScale(distance, seeThrough);
		// Where the label is drawn: pulled in for a see-through label, at the anchor otherwise.
		double x = pos.x + dx * pull;
		double y = pos.y + dy * pull;
		double z = pos.z + dz * pull;

		// The view test runs on an estimate that is never smaller than the label, before any text work.
		List<MarkerLabel.Line> lines = label.lines();
		int lineCount = lines.size() + (distanceLine == null ? 0 : 1);
		int maxChars = distanceLine == null ? 0 : distanceLine.getString().length();
		for (MarkerLabel.Line line : lines) {
			maxChars = Math.max(maxChars, line.text().getString().length());
		}
		double reach = Math.max(maxChars * MAX_GLYPH_WIDTH * scale / 2, lineCount * (font.lineHeight + 1) * scale)
				+ CULL_MARGIN * scale / SCALE;
		if (!camera.cullFrustum.isVisible(new AABB(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach))) {
			return false;
		}

		matrices.pushPose();
		// Two translations, as in 1.0.1, so a see-through label lands on exactly the same pixels.
		matrices.translate(pos.x, pos.y, pos.z);
		if (pull != 0.0) {
			matrices.translate(dx * pull, dy * pull, dz * pull);
		}
		// Billboard towards the camera.
		matrices.mulPose(camera.orientation);
		matrices.scale(scale, -scale, 1F);
		for (int i = 0; i < lines.size(); i++) {
			MarkerLabel.Line line = lines.get(i);
			submitLine(matrices, submits, font, line.text(), i, ARGB.opaque(line.color()), label);
		}
		if (distanceLine != null) {
			submitLine(matrices, submits, font, distanceLine, lines.size(), ARGB.opaque(label.distanceColor()), label);
		}
		matrices.popPose();
		return true;
	}

	private static void submitLine(PoseStack matrices, SubmitNodeCollector submits, Font font, Component text, int lineIndex,
			int textColor, MarkerLabel label) {
		int width = font.width(text);
		float lineHeight = font.lineHeight;
		boolean seeThrough = label.seeThrough();

		matrices.pushPose();
		matrices.translate(-width / 2F, lineIndex * (lineHeight + 1), 0F);
		int backgroundColor = label.backgroundColor();
		if (backgroundColor != 0) {
			RenderType backgroundType = seeThrough ? RenderTypes.textBackgroundSeeThrough() : RenderTypes.textBackground();
			submits.order(BACKGROUND_ORDER).submitCustom(SubmitRenderPhases.AFTER_TERRAIN, new CustomFeatureRenderer.Submit(
					matrices.last().copy(), backgroundType, (pose, background) -> {
						Matrix4f matrix = pose.pose();
						background.addVertex(matrix, -1F, -1F, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
						background.addVertex(matrix, -1F, lineHeight, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
						background.addVertex(matrix, width, lineHeight, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
						background.addVertex(matrix, width, -1F, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
					}));
		}
		matrices.translate(0F, 0F, 0.01F);

		submits.order(TEXT_ORDER).submitCustom(SubmitRenderPhases.AFTER_TERRAIN, new TextFeatureRenderer.Submit(
				new Matrix4f(matrices.last().pose()), 0F, 0F, text.getVisualOrderText(), false,
				seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET,
				LightCoordsUtil.FULL_BRIGHT, textColor, 0, 0));
		matrices.popPose();
	}
}
