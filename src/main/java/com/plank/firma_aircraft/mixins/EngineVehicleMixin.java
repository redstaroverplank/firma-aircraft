package com.plank.firma_aircraft.mixins;

import com.plank.firma_aircraft.Fuel;
import immersive_aircraft.config.Config;
import immersive_aircraft.entity.EngineVehicle;
import immersive_aircraft.entity.inventory.VehicleInventoryDescription;
import immersive_aircraft.entity.inventory.slots.SlotDescription;
import net.dries007.tfc.util.LampFuel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(EngineVehicle.class)
public class EngineVehicleMixin implements Fuel {

    @Final
    @Shadow
    private static EntityDataAccessor<Float> UTILIZATION;

    @Final
    @Shadow
    private static EntityDataAccessor<Boolean> LOW_ON_FUEL;

    /**
     * 修改燃料补充逻辑，在补充时消耗1mb流体
     */
    @Inject(method = "refuel*", at = @At("HEAD"), cancellable = true, remap = false)
    private void refuelWithFluid(CallbackInfo ci) {
        EngineVehicle vehicle = (EngineVehicle) (Object) this;

        // 只在服务端执行
        if (vehicle.level().isClientSide) {
            return;
        }

        // 获取所有燃料槽
        List<SlotDescription> fuelSlots = vehicle.getInventoryDescription().getSlots(VehicleInventoryDescription.BOILER);

        for (int i = 0; i < fuelSlots.size(); i++) {
            SlotDescription slot = fuelSlots.get(i);
            ItemStack stack = vehicle.getInventory().getItem(slot.index());

            if (stack.isEmpty()) continue;

            // 检查是否为TFC大桶
            if (Fuel.isTFCBarrel(stack)) {

                // 消耗流体并补充燃料
                refuelFromBarrel(stack, i);
            }
        }
        ci.cancel();
    }

    /**
     * 从大桶中消耗流体并补充燃料
     */
    @Unique
    private void refuelFromBarrel(ItemStack barrelStack, int fuelIndex) {
        // 获取桶内的流体
        FluidStack fluidStack = Fuel.getFluidFromBarrel(barrelStack);
        LampFuel fuel = Fuel.getFuel(fluidStack.getFluid());

        if (fluidStack.isEmpty()) {
            return; // 没有流体，不补充
        }

        // 检查流体是否为灯燃料
        if (fuel == null) {
            return; // 不是燃料流体，不补充
        }

        // 获取当前燃料值
        int currentFuel = getFuelValue(fuelIndex);

        // 获取燃烧率
        int burnRate = fuel.getBurnRate();
        if (burnRate <= 0) {
            setFuelValue(fuelIndex, 1000);
            return;
        }

        // 如果当前燃料值低于100刻，则补充燃料
        if (currentFuel < 1000) {
            // 关键：只更新桶内流体，不消耗桶物品
            fluidStack.shrink(1);
            Fuel.updateBarrelFluid(barrelStack, fluidStack);

            // 补充燃料值
            setFuelValue(fuelIndex, currentFuel + burnRate / 20);
        }
    }

    /**
     * 获取燃料值 - 需要访问原EngineVehicle的fuel数组
     */
    @Unique
    private int getFuelValue(int index) {
        try {
            // 使用反射访问私有fuel数组
            Field fuelField = EngineVehicle.class.getDeclaredField("fuel");
            fuelField.setAccessible(true);
            int[] fuel = (int[]) fuelField.get(this);

            if (index >= 0 && index < fuel.length) {
                return fuel[index];
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * 设置燃料值 - 需要访问原EngineVehicle的fuel数组
     */
    @Unique
    private void setFuelValue(int index, int value) {
        try {
            // 使用反射访问私有fuel数组
            Field fuelField = EngineVehicle.class.getDeclaredField("fuel");
            fuelField.setAccessible(true);
            int[] fuel = (int[]) fuelField.get(this);

            if (index >= 0 && index < fuel.length) {
                fuel[index] = value;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 修改燃料利用率计算，基于LampFuel系统
     */
    @Inject(method = "getFuelUtilization", at = @At("HEAD"), cancellable = true, remap = false)
    private void calculateLampFuelUtilization(CallbackInfoReturnable<Float> cir) {
        EngineVehicle vehicle = (EngineVehicle) (Object) this;

        // 检查配置和创意模式
        if (Config.getInstance().fuelConsumption == 0) {
            cir.setReturnValue(1.0f);
            return;
        }
        if (!Config.getInstance().burnFuelInCreative && vehicle.isPilotCreative()) {
            cir.setReturnValue(1.0f);
            return;
        }

        List<SlotDescription> fuelSlots = vehicle.getInventoryDescription().getSlots(VehicleInventoryDescription.BOILER);
        if (fuelSlots.isEmpty()) {
            cir.setReturnValue(1.0f);
            return;
        }

        int barrelsWithFuel = 0;
        int totalBarrels = 0;

        for (SlotDescription slot : fuelSlots) {
            ItemStack stack = vehicle.getInventory().getItem(slot.index());
            if (Fuel.isTFCBarrel(stack)) {
                totalBarrels++;
                if (hasLampFuelInBarrel(stack)) {
                    barrelsWithFuel++;
                }
            }
        }

        if (totalBarrels == 0) {
            cir.setReturnValue(0.0f);
            return;
        }

        float utilization = (float) barrelsWithFuel / totalBarrels;
        boolean lowFuel = isAnyBarrelLowOnLampFuel(vehicle);

        utilization *= (lowFuel ? 0.75f : 1.0f);

        // 同步到客户端
        if (!vehicle.level().isClientSide) {
            vehicle.getEntityData().set(UTILIZATION, utilization);
            vehicle.getEntityData().set(LOW_ON_FUEL, lowFuel);
        }

        cir.setReturnValue(utilization);
    }
}
