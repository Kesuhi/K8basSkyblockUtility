package com.k8bas.skyblockutility.module.mobhighlighter.mixin;

import com.k8bas.skyblockutility.highlight.HighlightManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EntityRendererMixin sets EntityRenderState#outlineColor directly, which is enough for
 * vanilla's own render path — but other mods that skip rendering/render-state-extraction for
 * off-screen or occluded entities (entity-culling style optimizations) typically decide that by
 * calling this same shouldEntityAppearGlowing() check, the way vanilla's own Glowing effect and
 * spectator highlight do. Without also hooking this, our outline only appeared with direct line
 * of sight instead of through walls: matched entities never got exempted from that culling.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void k8bas$shouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (HighlightManager.getOutlineColorFromAny(entity) != 0) {
			cir.setReturnValue(true);
		}
	}
}
