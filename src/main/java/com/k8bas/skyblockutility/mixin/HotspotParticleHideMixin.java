package com.k8bas.skyblockutility.mixin;

import com.k8bas.skyblockutility.module.hotspot.HotspotModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * "Hide Hypixel's hotspot particles" (T3.6c, REQ-HOT-14, R41 (b)): in the client's handler of a server particle packet,
 * the particles it adds to the level are not made when they belong to a tracked hotspot's ring. The packet is handled
 * and read by the observer (ParticleObserverMixin) as always and is never cancelled or changed; only what this client
 * draws differs. Hook {@code hotspot.hide}; when it is skipped, the option shows as unavailable and the particles draw.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.multiplayer.ClientPacketListener")
public abstract class HotspotParticleHideMixin {
	@WrapOperation(method = "handleParticleEvent(Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V"))
	private void k8bas$hideRingParticle(ClientLevel level, ParticleOptions options, boolean overrideLimiter, boolean alwaysShow, double x, double y,
			double z, double xd, double yd, double zd, Operation<Void> original) {
		if (!HotspotModule.hidesParticle(options, x, y, z)) {
			original.call(level, options, overrideLimiter, alwaysShow, x, y, z, xd, yd, zd);
		}
	}
}
