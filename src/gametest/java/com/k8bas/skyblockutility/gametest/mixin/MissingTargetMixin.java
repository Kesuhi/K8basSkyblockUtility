package com.k8bas.skyblockutility.gametest.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** A test hook whose target class does not exist (T3.0f, AC-BOB-07): the game still starts. */
@Pseudo
@Mixin(targets = "com.k8bas.skyblockutility.gametest.hook.NoSuchClass")
public abstract class MissingTargetMixin {
	@ModifyReturnValue(method = "value()I", at = @At("RETURN"))
	private int k8bas$missing(int original) {
		return original + 1000;
	}
}
