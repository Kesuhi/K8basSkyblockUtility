package com.k8bas.skyblockutility.mixin;

import com.k8bas.skyblockutility.module.drillfix.DrillFixModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The drill re-equip fix's one hook (T3.7, REQ-DRILL-01, REQ-DRILL-04, AC-DRILL-02): the first-person hand's "replace
 * the shown stack without lowering and raising" decision. It may only turn vanilla's (or another mod's) false into
 * true for a refreshed copy of the same drill, never the other way. Hook {@code drill.refresh}.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer")
public abstract class DrillReequipMixin {
	@ModifyReturnValue(method = "shouldInstantlyReplaceVisibleItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
			at = @At("RETURN"))
	private boolean k8bas$keepDrillInHand(boolean original, ItemStack shown, ItemStack next) {
		return DrillFixModule.instant(original, shown, next);
	}
}
