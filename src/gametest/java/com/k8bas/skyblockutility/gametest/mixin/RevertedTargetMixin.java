package com.k8bas.skyblockutility.gametest.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** A test hook that goes in but is lost when {@link ForeignBadMixin} fails on the same class (T3.0f). */
@Pseudo
@Mixin(targets = "com.k8bas.skyblockutility.gametest.hook.RevertedTarget")
public abstract class RevertedTargetMixin {
	@ModifyReturnValue(method = "value()I", at = @At("RETURN"))
	private int k8bas$tripled(int original) {
		return original * 3;
	}
}
