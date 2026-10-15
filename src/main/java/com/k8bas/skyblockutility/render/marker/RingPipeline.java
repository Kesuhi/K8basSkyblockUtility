package com.k8bas.skyblockutility.render.marker;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * The ring's own render pipeline: vanilla's debug quads (translucent, depth-tested, no depth write, no culling) under
 * this mod's id. Iris replaces every pipeline it knows with a shaderpack program, but it has none for vanilla's
 * {@code debug_quads} (IrisShaders/Iris#3297), so with a shaderpack on, a ring drawn with it is painted over by the
 * pack's final pass. A pipeline of our own can be given to Iris through its public API (v0) as a basic program
 * ({@link #registerWithIris()}); vanilla's own pipeline is left alone, as another mod may assign it too.
 */
public final class RingPipeline {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/markers");
	static final Identifier LOCATION = Identifier.fromNamespaceAndPath("k8bas_skyblock_utility", "pipeline/marker_ring");

	/** The copy, or vanilla's debug quads if it cannot be copied (then only Iris with a shaderpack loses the ring). */
	public static final RenderPipeline PIPELINE = create();

	private RingPipeline() {
	}

	private static RenderPipeline create() {
		try {
			return copy(RenderPipelines.DEBUG_QUADS, LOCATION);
		} catch (RuntimeException e) {
			LOGGER.warn("Cannot copy the debug quads pipeline for rings; rings use it directly, and Iris shaderpacks do not draw them", e);
			return RenderPipelines.DEBUG_QUADS;
		}
	}

	/** Every setting of [source] under [location]. Throws for a shader define the builder cannot take (a text value). */
	static RenderPipeline copy(RenderPipeline source, Identifier location) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(location)
				.withVertexShader(source.getVertexShader())
				.withFragmentShader(source.getFragmentShader())
				.withPolygonMode(source.getPolygonMode())
				.withCull(source.isCull())
				.withDepthStencilState(Optional.ofNullable(source.getDepthStencilState()))
				.withPrimitiveTopology(source.getPrimitiveTopology());
		ShaderDefines defines = source.getShaderDefines();
		for (String flag : defines.flags()) {
			builder.withShaderDefine(flag);
		}
		for (Map.Entry<String, String> define : defines.values().entrySet()) {
			defineNumber(builder, define.getKey(), define.getValue());
		}
		source.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
		VertexFormat[] formats = source.getVertexFormatBindings();
		for (int i = 0; i < formats.length; i++) {
			if (formats[i] != null) {
				builder.withVertexBinding(i, formats[i]);
			}
		}
		ColorTargetState[] targets = source.getColorTargetStates();
		for (int i = 0; i < targets.length; i++) {
			if (targets[i] != null) {
				builder.withColorTargetState(i, targets[i]);
			} else {
				builder.withUnusedColorTargetState(i);
			}
		}
		return builder.build();
	}

	private static void defineNumber(RenderPipeline.Builder builder, String name, String value) {
		try {
			builder.withShaderDefine(name, Integer.parseInt(value));
		} catch (NumberFormatException notInt) {
			try {
				builder.withShaderDefine(name, Float.parseFloat(value));
			} catch (NumberFormatException notFloat) {
				throw new IllegalStateException("shader define " + name + "=" + value + " is not a number");
			}
		}
	}

	/**
	 * With Iris installed, assigns the ring pipeline to Iris's basic program (untextured, coloured geometry), so a
	 * shaderpack draws rings. Reflective, so the mod needs no Iris at build time or at run time; any failure leaves
	 * rings as they are without a shaderpack and is logged once.
	 */
	public static void registerWithIris() {
		if (!FabricLoader.getInstance().isModLoaded("iris") || PIPELINE == RenderPipelines.DEBUG_QUADS) {
			return;
		}
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
			Object instance = api.getMethod("getInstance").invoke(null);
			Object basic = program.getField("BASIC").get(null);
			api.getMethod("assignPipeline", RenderPipeline.class, program).invoke(instance, PIPELINE, basic);
			LOGGER.info("Rings use Iris's basic program with a shaderpack");
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			LOGGER.warn("Cannot give the ring pipeline to Iris; with a shaderpack on, rings may not show", e);
		}
	}
}
