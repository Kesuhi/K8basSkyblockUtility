package com.k8bas.skyblockutility.mixin;

import com.k8bas.skyblockutility.module.bobberfix.BobberFix;
import com.k8bas.skyblockutility.module.bobberfix.BobberFixModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The bobber fix's two optional hooks (T3.5, REQ-BOB-01, REQ-BOB-02). Both change only what the client does with
 * data it already applied: the entity data and every packet stay as they are (REQ-BOB-04, REQ-XC-RULES-02).
 * <ul>
 *   <li>{@code bobber.link}: when the hooked-entity data names Hypixel's bite-timer stand, the own hook does not
 *       take it as {@code hookedIn}, so it is not pinned to the stand every tick and keeps floating.</li>
 *   <li>{@code bobber.lava}: the own hook's two "in water?" checks in {@code tick()} also accept lava, so it floats
 *       on the lava surface as vanilla floats it on water.</li>
 * </ul>
 */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.projectile.FishingHook")
public abstract class FishingHookMixin {
	@WrapOperation(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;"))
	private Entity k8bas$skipTimerStand(Level level, int id, Operation<Entity> original) {
		Entity linked = original.call(level, id);
		FishingHook hook = (FishingHook) (Object) this;
		boolean ignore = linked != null && BobberFix.ignoresLink(BobberFixModule.onSkyBlock(), BobberFixModule.enabled(), own(hook), hook.getId(),
				true, linked.getId(), linked instanceof ArmorStand);
		return ignore ? null : linked;
	}

	@WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean k8bas$floatOnLava(FluidState state, TagKey<Fluid> tag, Operation<Boolean> original) {
		boolean water = original.call(state, tag);
		if (water || tag != FluidTags.WATER || !state.is(FluidTags.LAVA)) {
			return water;
		}
		FishingHook hook = (FishingHook) (Object) this;
		return BobberFix.floats(BobberFix.Fluid.LAVA, BobberFixModule.onSkyBlock(), BobberFixModule.enabled(), own(hook));
	}

	/** The local player's own hook (REQ-BOB-06): its owner, from the spawn packet, is the client's player. */
	private static boolean own(FishingHook hook) {
		var player = Minecraft.getInstance().player;
		return player != null && hook.getPlayerOwner() == player;
	}
}
