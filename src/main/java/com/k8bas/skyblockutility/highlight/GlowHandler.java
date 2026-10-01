package com.k8bas.skyblockutility.highlight;

import net.azureaaron.renderchest.api.CustomGlowCallback;
import net.azureaaron.renderchest.api.GlowConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The only place the mod's glow reaches the renderer (AD-1). Render Chest draws what this returns
 * as a depth-tested outline: blocks hide it, and an entity whose body is not drawn (invisible) has
 * none. Nothing here changes whether an entity is visible, culled or glowing (REQ-GLOW-05).
 */
public final class GlowHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger("k8bas_skyblock_utility/highlight");

	private GlowHandler() {
	}

	public static void register() {
		CustomGlowCallback.EVENT.register(GlowHandler::glowColour);
		// The callback is shared with other mods and the first non-zero colour wins (REQ-GLOW-07).
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> LOGGER.info("Render Chest glow providers in call order: {}",
				providers(CustomGlowCallback.EVENT)));
	}

	private static int glowColour(Entity entity, EntityRenderState state) {
		return colourFor(entity.isCurrentlyGlowing(), HighlightManager.getOutlineColorFromAny(entity));
	}

	/**
	 * @param serverGlow whether the server already makes the entity glow
	 * @param cached     this tick's colour from HighlightManager (opaque ARGB), or 0 for none
	 */
	static int colourFor(boolean serverGlow, int cached) {
		// Render Chest recolours glow the entity already has, and that outline shows through walls.
		// Server glow is left exactly as the server sets it (REQ-GLOW-04).
		if (serverGlow) {
			return GlowConstants.NO_GLOW;
		}
		// Colours are opaque, so black (0xFF000000) is a colour, not NO_GLOW (AC-GLOW-17).
		return cached;
	}

	/** The listener classes of a Fabric event in call order, for the log; "unknown" if unreadable. */
	static String providers(Event<?> event) {
		try {
			Field handlers = event.getClass().getDeclaredField("handlers");
			handlers.setAccessible(true);
			Object[] listeners = (Object[]) handlers.get(event);
			return Arrays.stream(listeners)
					.map(listener -> listener.getClass().getName().replaceAll("\\$\\$Lambda.*", ""))
					.collect(Collectors.joining(", ", "[", "]"));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "unknown (" + e.getClass().getSimpleName() + ")";
		}
	}
}
