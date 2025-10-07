package com.plank.firma_aircraft.mixins;

import com.plank.firma_aircraft.Fuel;
import com.plank.firma_aircraft.Tags;
import immersive_aircraft.config.Config;
import immersive_aircraft.entity.EngineVehicle;
import immersive_aircraft.entity.inventory.VehicleInventoryDescription;
import immersive_aircraft.entity.inventory.slots.SlotDescription;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(EngineVehicle.class)
public class AbstractFurnaceBlockEntityMixin implements Fuel {

    @Unique
    private static final EntityDataAccessor<Float> UTILIZATION = SynchedEntityData.defineId(EngineVehicle.class, EntityDataSerializers.FLOAT);
    @Unique
    private static final EntityDataAccessor<Boolean> LOW_ON_FUEL = SynchedEntityData.defineId(EngineVehicle.class, EntityDataSerializers.BOOLEAN);

    /**
     * 修改燃料消耗逻辑，直接消耗桶内的流体
     */
    @Inject(method = "consumeFuel", at = @At("HEAD"), cancellable = true)
    private void consumeBarrelFluid(float consumption, CallbackInfoReturnable<Float> cir) {
        EngineVehicle vehicle = (EngineVehicle) (Object) this;

        // 获取所有燃料槽
        List<SlotDescription> fuelSlots = vehicle.getInventoryDescription().getSlots(VehicleInventoryDescription.BOILER);

        for (SlotDescription slot : fuelSlots) {
            ItemStack stack = vehicle.getInventory().getItem(slot.index());

            if (stack.isEmpty()) continue;

            // 检查是否为燃料桶
            if (stack.is(Tags.FUEL_BARRELS)) {
                float remainingConsumption = consumeFluidFromBarrel(stack, consumption);
                if (remainingConsumption < consumption) {
                    // 成功消耗了部分燃料
                    consumption = remainingConsumption;
                    if (consumption <= 0) {
                        cir.setReturnValue(0.0f);
                        cir.cancel();
                    }
                }
            }
        }
    }

    /**
     * 修改燃料利用率计算，基于桶内流体
     */
    @Inject(method = "getFuelUtilization", at = @At("HEAD"), cancellable = true)
    private void calculateBarrelFuelUtilization(CallbackInfoReturnable<Float> cir) {
        EngineVehicle vehicle = (EngineVehicle) (Object) this;

        // 检查配置和创意模式
        if (Config.getInstance().fuelConsumption == 0) {
            cir.setReturnValue(1.0f);
            cir.cancel();
        }
        if (!Config.getInstance().burnFuelInCreative && vehicle.isPilotCreative()) {
            cir.setReturnValue(1.0f);
            cir.cancel();
        }

        List<SlotDescription> fuelSlots = vehicle.getInventoryDescription().getSlots(VehicleInventoryDescription.BOILER);
        if (fuelSlots.isEmpty()) {
            cir.setReturnValue(1.0f);
            cir.cancel();
        }

        int barrelsWithFuel = 0;
        int totalBarrels = 0;

        for (SlotDescription slot : fuelSlots) {
            ItemStack stack = vehicle.getInventory().getItem(slot.index());
            if (stack.is(Tags.FUEL_BARRELS)) {
                totalBarrels++;
                if (hasFluidInBarrel(stack)) {
                    barrelsWithFuel++;
                }
            }
        }

        if (totalBarrels == 0) {
            cir.setReturnValue(0.0f); // 没有燃料桶
            return;
        }

        float utilization = (float) barrelsWithFuel / totalBarrels;
        // 检查是否有低燃料警告
        boolean lowFuel = isAnyBarrelLowOnFluid(vehicle);

        utilization *= (lowFuel ? 0.75f : 1.0f);

        // 同步到客户端
        if (!vehicle.level().isClientSide) {
            vehicle.getEntityData().set(UTILIZATION, utilization);
            vehicle.getEntityData().set(LOW_ON_FUEL, lowFuel);
        }

        cir.setReturnValue(utilization);
    }
}
