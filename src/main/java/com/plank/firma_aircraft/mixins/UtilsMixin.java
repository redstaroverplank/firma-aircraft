package com.plank.firma_aircraft.mixins;

import com.plank.firma_aircraft.Fuel;
import immersive_aircraft.util.Utils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Utils.class)
public class UtilsMixin {


    /**
     * 修改燃料时间获取，从LampFuel系统获取燃料值
     */
    @Inject(method = "getFuelTime", at = @At("HEAD"), cancellable = true, remap = false)
    private static void getLampFuelTime(ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        if (fuel.isEmpty()) return;
        if (Fuel.isTFCBarrel(fuel)) {
            cir.setReturnValue(1);
        }
        cir.cancel();
    }
}