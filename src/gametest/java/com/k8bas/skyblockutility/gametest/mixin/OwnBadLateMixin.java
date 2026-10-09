package com.k8bas.skyblockutility.gametest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A test hook of ours whose late @WrapOperation fails (T3.0f): the handler takes the wrong receiver type, so Mixin
 * loads OwnLateTarget with its original bytes, without ForeignGoodMixin's change too.
 */
@Pseudo
@Mixin(targets = "com.k8bas.skyblockutility.gametest.hook.OwnLateTarget")
public abstract class OwnBadLateMixin {
	@WrapOperation(method = "value()I", at = @At(value = "INVOKE", target = "Lcom/k8bas/skyblockutility/gametest/hook/OwnLateTarget;base()I"))
	private int k8bas$ownBroken(String wrongReceiver, Operation<Integer> original) {
		return 0;
	}
}
