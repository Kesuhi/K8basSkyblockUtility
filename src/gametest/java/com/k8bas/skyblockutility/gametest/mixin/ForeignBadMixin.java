package com.k8bas.skyblockutility.gametest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stands in for another mod's broken mixin (T3.0f): its @WrapOperation handler takes the wrong receiver type, so
 * MixinExtras' late injection throws and Mixin loads RevertedTarget with its original bytes. It has no hook.
 */
@Pseudo
@Mixin(targets = "com.k8bas.skyblockutility.gametest.hook.RevertedTarget")
public abstract class ForeignBadMixin {
	@WrapOperation(method = "value()I", at = @At(value = "INVOKE", target = "Lcom/k8bas/skyblockutility/gametest/hook/RevertedTarget;base()I"))
	private int k8bas$broken(String wrongReceiver, Operation<Integer> original) {
		return 0;
	}
}
