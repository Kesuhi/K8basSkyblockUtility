package com.k8bas.skyblockutility.render.marker;

import com.k8bas.skyblockutility.location.IslandTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhase;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
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
 * translation applied by hand. Translucent terrain (water, glass, ice) must not tint or hide a label
 * (REQ-MARK-03), also with Improved Transparency (the Fabulous preset), which draws translucent terrain
 * into its own target and composites it over the main one by depth at the end of the level:
 * <ul>
 *   <li>A see-through label (fixed anchors only) goes into Fabric's ALWAYS_ON_TOP phase, which runs
 *       after that composite (and after clouds and weather). It is drawn pulled in to 10 blocks from
 *       the camera, so its on-screen size stays constant and it stays inside the far plane (EC-MARK-02).</li>
 *   <li>A depth-tested label goes into AFTER_TERRAIN, after translucent terrain. It is drawn at its
 *       true position and grown with the distance instead, so it has the same on-screen size and every
 *       block in front of it hides it (REQ-MARK-02).</li>
 * </ul>
 * Beacon beams (T3.0n) go through vanilla's beacon renderer, so they are depth-tested and animated
 * like a beacon, up to the build height. Rings are translucent, depth-tested quads in AFTER_TERRAIN,
 * drawn into the translucent terrain's target so they blend over water instead of under it.
 * Markers outside the view are not submitted (REQ-MARK-07), labels hide with F1 while beams and rings
 * stay (EC-MARK-06), and every provider is reset on world change, server switch and disconnect;
 * markers on an entity that is gone or in another world are skipped (REQ-MARK-08). No chunk or block
 * data is read (EC-MARK-02).
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
	/** How far a ring floats above its anchor, and its outline's width, in blocks. */
	private static final double RING_LIFT = 0.02;
	private static final double RING_OUTLINE_WIDTH = 0.08;
	/**
	 * Rings: vanilla's debug quads (translucent, depth-tested, no depth write), drawn into the target
	 * translucent terrain uses. With Improved Transparency that is its own target, which holds the opaque
	 * depth plus the water's, so a ring on water is drawn after the water inside that layer and blends
	 * over it, while opaque terrain still hides it. Without Improved Transparency the supplier gives null
	 * and the main target is used, as for vanilla's debug quads.
	 */
	private static final RenderType RING = RenderType.create("k8bas_marker_ring",
			RenderSetup.builder(RenderPipelines.DEBUG_QUADS).sortOnUpload()
					.setOutputTarget(new OutputTarget("k8bas_translucent_terrain", () -> Minecraft.getInstance().levelRenderer.translucentTarget()))
					.createRenderSetup());

	private static final List<MarkerProvider> PROVIDERS = new CopyOnWriteArrayList<>();
	private static final Set<MarkerProvider> FAILED = ConcurrentHashMap.newKeySet();
	private static volatile boolean hideLabelsWithHud = true;
	private static volatile boolean recordFrames;
	private static volatile Frame lastFrame = Frame.EMPTY;
	private static volatile Frame firstFrameAfterReset = Frame.EMPTY;
	private static int framesSinceReset;
	private static volatile Stats stats = new Stats(0, 0);

	/**
	 * What a frame did, for gametests: markers with at least one part submitted, markers whose parts
	 * were all outside the view, the beams and rings submitted, the CPU time of the marker pass, and
	 * (only while recording) the distance texts drawn.
	 */
	public record Frame(int submitted, int culled, int beams, int rings, long nanos, List<String> distanceTexts) {
		static final Frame EMPTY = new Frame(0, 0, 0, 0, 0, List.of());
	}

	/** Frames counted while recording, and the marker pass's total CPU time over them (AC-MARK-06). */
	public record Stats(long frames, long nanos) {
		public double averageMillis() {
			return frames == 0 ? 0 : nanos / 1e6 / frames;
		}
	}

	/** The vertical extent of a beam: from block y {@code start}, {@code height} blocks up. */
	record BeamSpan(int start, int height) {
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

	/** For gametests: records the distance texts of each frame in {@link #lastFrame()} and counts {@link #stats()} from zero. */
	public static void setRecordFrames(boolean record) {
		stats = new Stats(0, 0);
		recordFrames = record;
	}

	public static Stats stats() {
		return stats;
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

	/**
	 * Where a label is drawn: a see-through one after everything in the level, including the Improved
	 * Transparency composite, so no translucent terrain covers it; a depth-tested one after translucent
	 * terrain, still depth-tested against the opaque terrain.
	 */
	static SubmitRenderPhase<SubmitNode> labelPhase(boolean seeThrough) {
		return seeThrough ? SubmitRenderPhases.ALWAYS_ON_TOP : SubmitRenderPhases.AFTER_TERRAIN;
	}

	/** The label scale at a distance: see-through labels are pulled in instead; depth-tested ones grow. */
	static float labelScale(double distance, boolean seeThrough) {
		return seeThrough ? SCALE : (float) (SCALE * Math.max(1.0, distance / CONSTANT_SIZE_DISTANCE));
	}

	/**
	 * A beam from the marker block up to the build height, clipped there (REQ-MARK-04, EC-MARK-11): it
	 * starts at the world's floor for a marker below it, and there is none above the top block.
	 *
	 * @param maxY the top block's y (inclusive); the beam ends at its top face
	 * @return the span, or null for no beam
	 */
	static BeamSpan beamSpan(double markerY, int minY, int maxY) {
		int start = (int) Math.floor(markerY);
		if (start > maxY) {
			return null;
		}
		start = Math.max(start, minY);
		return new BeamSpan(start, maxY + 1 - start);
	}

	/** The beam widens with horizontal distance like a vanilla beacon's, so it stays visible far away;
	 *  not while the player looks through a spyglass, as in vanilla (REQ-NPCWP-04). */
	static float beamRadiusScale(double horizontalDistance, boolean scoping) {
		return scoping ? 1.0F : (float) Math.max(1.0, horizontalDistance / 96.0);
	}

	/** Where a label is drawn, and what its distance line measures to: its rise above the anchor. */
	public static Vec3 labelPosition(Vec3 anchor, MarkerLabel label) {
		return label.rise() == 0 ? anchor : anchor.add(0, label.rise(), 0);
	}

	/** Whole metres from the player's position (not the camera) to the label, rounded half-up (REQ-MARK-05). */
	public static String distanceText(Vec3 player, Vec3 anchor) {
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
		long start = System.nanoTime();
		Frame frame = submitMarkers(context, start);
		lastFrame = frame;
		if (++framesSinceReset == 1) {
			firstFrameAfterReset = frame;
		}
		if (recordFrames) {
			Stats before = stats;
			stats = new Stats(before.frames() + 1, before.nanos() + frame.nanos());
		}
	}

	private static Frame submitMarkers(LevelRenderContext context, long start) {
		List<Marker> markers = collect(PROVIDERS);
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (markers.isEmpty() || level == null) {
			return new Frame(0, 0, 0, 0, System.nanoTime() - start, List.of());
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		SubmitNodeCollector submits = context.submitNodeCollector();
		boolean labelsHidden = hideLabelsWithHud && client.gui.hud.isHidden();
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		// The vanilla beacon animation: one turn every 40 ticks.
		float animationTime = Math.floorMod(level.getGameTime(), 40) + partialTick;
		Vec3 player = client.player == null ? null : client.player.getPosition(partialTick);
		boolean scoping = client.player != null && client.player.isScoping();
		boolean record = recordFrames;
		List<String> distanceTexts = record ? new ArrayList<>() : List.of();
		int submitted = 0;
		int culled = 0;
		int beams = 0;
		int rings = 0;

		PoseStack matrices = context.poseStack();
		matrices.pushPose();
		matrices.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z);
		for (Marker marker : markers) {
			if (!inThisWorld(marker.anchor(), client)) {
				continue;
			}
			Vec3 pos = marker.anchor().position(partialTick);
			boolean drawn = false;
			boolean outOfView = false;
			MarkerLabel label = marker.label();
			if (label != null && !labelsHidden) {
				Vec3 labelPos = labelPosition(pos, label);
				Component distanceLine = label.distanceLine() && player != null ? distanceLine(distanceMetres(player, labelPos)) : null;
				if (submitLabel(matrices, submits, camera, client.font, labelPos, label, distanceLine)) {
					drawn = true;
					if (record && distanceLine != null) {
						distanceTexts.add(distanceLine.getString());
					}
				} else {
					outOfView = true;
				}
			}
			// Beams and rings stay with F1 (EC-MARK-06).
			if (marker.beam() != null) {
				BeamSpan span = beamSpan(pos.y, level.getMinY(), level.getMaxY());
				if (span != null) {
					if (submitBeam(matrices, submits, camera, pos, span, marker.beam(), animationTime, scoping)) {
						beams++;
						drawn = true;
					} else {
						outOfView = true;
					}
				}
			}
			if (marker.ring() != null) {
				if (submitRing(matrices, submits, camera, pos, marker.ring())) {
					rings++;
					drawn = true;
				} else {
					outOfView = true;
				}
			}
			if (drawn) {
				submitted++;
			} else if (outOfView) {
				culled++;
			}
		}
		matrices.popPose();
		return new Frame(submitted, culled, beams, rings, System.nanoTime() - start, record ? List.copyOf(distanceTexts) : List.of());
	}

	/**
	 * Submits a beam through vanilla's beacon renderer: its core in the solid phase and its glow with
	 * the translucent geometry, both depth-tested (REQ-MARK-02). False if it is outside the view.
	 */
	private static boolean submitBeam(PoseStack matrices, SubmitNodeCollector submits, CameraRenderState camera, Vec3 pos, BeamSpan span,
			MarkerBeam beam, float animationTime, boolean scoping) {
		int blockX = Mth.floor(pos.x);
		int blockZ = Mth.floor(pos.z);
		double centreX = blockX + 0.5;
		double centreZ = blockZ + 0.5;
		double hx = centreX - camera.pos.x;
		double hz = centreZ - camera.pos.z;
		float radiusScale = beamRadiusScale(Math.sqrt(hx * hx + hz * hz), scoping);
		double halfWidth = BeaconRenderer.BEAM_GLOW_RADIUS * radiusScale + CULL_MARGIN;
		if (!camera.cullFrustum.isVisible(new AABB(centreX - halfWidth, span.start(), centreZ - halfWidth,
				centreX + halfWidth, span.start() + span.height(), centreZ + halfWidth))) {
			return false;
		}
		matrices.pushPose();
		// The renderer centres the beam on the block itself (it translates by 0.5, 0, 0.5).
		matrices.translate(blockX, span.start(), blockZ);
		BeaconRenderer.submitBeaconBeam(matrices, submits, BeaconRenderer.BEAM_LOCATION, 1.0F, animationTime, 0, span.height(), beam.argb(),
				BeaconRenderer.SOLID_BEAM_RADIUS * radiusScale, BeaconRenderer.BEAM_GLOW_RADIUS * radiusScale);
		matrices.popPose();
		return true;
	}

	/**
	 * Submits a ring in the AFTER_TERRAIN phase with translucent, depth-tested quads ({@link #RING}), so
	 * terrain hides it and water drawn earlier cannot tint it, with Improved Transparency on or off. It
	 * sits just above the anchor, so it does not flicker on the surface it marks. The outline is opaque;
	 * the disc has the ring's alpha. False if out of view. The quads blend, cull nothing and write no
	 * depth, so a ring never hides a label behind it.
	 */
	private static boolean submitRing(PoseStack matrices, SubmitNodeCollector submits, CameraRenderState camera, Vec3 pos, MarkerRing ring) {
		double radius = ring.radius();
		double y = pos.y + RING_LIFT;
		if (!camera.cullFrustum.isVisible(new AABB(pos.x - radius, y - 0.1, pos.z - radius, pos.x + radius, y + 0.1, pos.z + radius))) {
			return false;
		}
		int segments = ringSegments(radius);
		float outer = (float) radius;
		float inner = (float) Math.max(0, radius - RING_OUTLINE_WIDTH);
		boolean disc = ring.style() != MarkerRing.Style.OUTLINE;
		boolean outline = ring.style() != MarkerRing.Style.DISC;
		int discColor = ring.argb();
		int outlineColor = ARGB.opaque(ring.argb());
		matrices.pushPose();
		matrices.translate(pos.x, y, pos.z);
		submits.order(BACKGROUND_ORDER).submitCustom(SubmitRenderPhases.AFTER_TERRAIN, new CustomFeatureRenderer.Submit(
				matrices.last().copy(), RING, (pose, quads) -> {
					Matrix4f matrix = pose.pose();
					for (int i = 0; i < segments; i++) {
						double a0 = Math.PI * 2 * i / segments;
						double a1 = Math.PI * 2 * (i + 1) / segments;
						float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
						float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
						if (disc) {
							// A triangle from the centre, as a quad with the centre twice.
							float r = outline ? inner : outer;
							quads.addVertex(matrix, 0F, 0F, 0F).setColor(discColor);
							quads.addVertex(matrix, c0 * r, 0F, s0 * r).setColor(discColor);
							quads.addVertex(matrix, c1 * r, 0F, s1 * r).setColor(discColor);
							quads.addVertex(matrix, 0F, 0F, 0F).setColor(discColor);
						}
						if (outline) {
							quads.addVertex(matrix, c0 * inner, 0F, s0 * inner).setColor(outlineColor);
							quads.addVertex(matrix, c0 * outer, 0F, s0 * outer).setColor(outlineColor);
							quads.addVertex(matrix, c1 * outer, 0F, s1 * outer).setColor(outlineColor);
							quads.addVertex(matrix, c1 * inner, 0F, s1 * inner).setColor(outlineColor);
						}
					}
				}));
		matrices.popPose();
		return true;
	}

	/** Enough segments that the ring looks round: about one per quarter block of circumference, 16 to 128. */
	static int ringSegments(double radius) {
		return (int) Math.max(16, Math.min(128, Math.ceil(2 * Math.PI * radius / 0.25)));
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
		SubmitRenderPhase<SubmitNode> phase = labelPhase(seeThrough);
		if (backgroundColor != 0) {
			RenderType backgroundType = seeThrough ? RenderTypes.textBackgroundSeeThrough() : RenderTypes.textBackground();
			submits.order(BACKGROUND_ORDER).submitCustom(phase, new CustomFeatureRenderer.Submit(
					matrices.last().copy(), backgroundType, (pose, background) -> {
						Matrix4f matrix = pose.pose();
						background.addVertex(matrix, -1F, -1F, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
						background.addVertex(matrix, -1F, lineHeight, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
						background.addVertex(matrix, width, lineHeight, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
						background.addVertex(matrix, width, -1F, 0F).setColor(backgroundColor).setLight(LightCoordsUtil.FULL_BRIGHT);
					}));
		}
		matrices.translate(0F, 0F, 0.01F);

		submits.order(TEXT_ORDER).submitCustom(phase, new TextFeatureRenderer.Submit(
				new Matrix4f(matrices.last().pose()), 0F, 0F, text.getVisualOrderText(), false,
				seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET,
				LightCoordsUtil.FULL_BRIGHT, textColor, 0, 0));
		matrices.popPose();
	}
}
