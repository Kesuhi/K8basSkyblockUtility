package com.k8bas.skyblockutility.gametest.mixin;

import com.k8bas.skyblockutility.gametest.hook.HookTarget;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Two test hooks that go in (T3.0f): a late @WrapOperation and a @ModifyReturnValue. */
@Pseudo
@Mixin(targets = "com.k8bas.skyblockutility.gametest.hook.HookTarget")
public abstract class HookTargetMixin {
	@WrapOperation(method = "value()I", at = @At(value = "INVOKE", target = "Lcom/k8bas/skyblockutility/gametest/hook/HookTarget;base()I"))
	private int k8bas$plusOne(HookTarget self, Operation<Integer> original) {
		return original.call(self) + 1;
	}

	@ModifyReturnValue(method = "value()I", at = @At("RETURN"))
	private int k8bas$doubled(int original) {
		return original * 2;
	}
}
