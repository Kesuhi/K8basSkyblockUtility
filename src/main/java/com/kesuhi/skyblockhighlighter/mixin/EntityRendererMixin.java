package com.kesuhi.skyblockhighlighter.mixin;

import com.kesuhi.skyblockhighlighter.highlight.HighlightManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityRenderer#extractRenderState is the shared base method (every entity renderer
 * extends this) that vanilla uses to set EntityRenderState#outlineColor for the Glowing
 * effect / spectator highlight. Setting it here reuses that exact render path — the thin
 * traced-silhouette outline drawn through walls, unlit — for our own rule matches instead
 * of writing a new outline shader.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void skyblockhighlighter$extractRenderState(T entity, S state, float partialTick, CallbackInfo ci) {
		int color = HighlightManager.getOutlineColor(entity);
		if (color != 0) {
			state.outlineColor = color;
		}
	}
}
