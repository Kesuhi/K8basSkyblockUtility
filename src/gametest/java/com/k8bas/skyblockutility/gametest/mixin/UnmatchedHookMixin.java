package com.k8bas.skyblockutility.gametest.mixin;

import com.k8bas.skyblockutility.gametest.hook.HookTarget;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** A test hook whose call site does not exist, as after a game update (T3.0f): Mixin skips it silently. */
@Pseudo
@Mixin(targets = "com.k8bas.skyblockutility.gametest.hook.HookTarget")
public abstract class UnmatchedHookMixin {
	@WrapOperation(method = "value()I", at = @At(value = "INVOKE", target = "Lcom/k8bas/skyblockutility/gametest/hook/HookTarget;missing()I"))
	private int k8bas$unmatched(HookTarget self, Operation<Integer> original) {
		return original.call(self) + 1000;
	}
}
