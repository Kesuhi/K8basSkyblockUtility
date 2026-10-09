package com.k8bas.skyblockutility.gametest.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Stands in for another mod's working mixin on OwnLateTarget (T3.0f); it has no hook and is lost with the class. */
@Pseudo
@Mixin(targets = "com.k8bas.skyblockutility.gametest.hook.OwnLateTarget")
public abstract class ForeignGoodMixin {
	@ModifyReturnValue(method = "value()I", at = @At("RETURN"))
	private int k8bas$plusTen(int original) {
		return original + 10;
	}
}
