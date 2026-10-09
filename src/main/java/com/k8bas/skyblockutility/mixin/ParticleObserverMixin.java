package com.k8bas.skyblockutility.mixin;

import com.k8bas.skyblockutility.module.hotspot.HotspotModule;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hotspot radius's read-only particle observer (T3.6, REQ-HOT-04, AC-HOT-07): it reads each server particle
 * packet right after the handler moved to the client thread, so once per packet, and never cancels or changes it
 * (P5). Hook {@code hotspot.particles}; when it is skipped, rings use the island's fallback radius (REQ-HOT-13).
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.multiplayer.ClientPacketListener")
public abstract class ParticleObserverMixin {
	@Inject(method = "handleParticleEvent(Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;"
					+ "Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void k8bas$observeParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		HotspotModule.onParticles(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ());
	}
}
