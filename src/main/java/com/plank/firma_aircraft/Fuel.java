package com.plank.firma_aircraft;

import immersive_aircraft.entity.EngineVehicle;
import immersive_aircraft.entity.inventory.VehicleInventoryDescription;
import immersive_aircraft.entity.inventory.slots.SlotDescription;
import net.dries007.tfc.util.LampFuel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@SuppressWarnings("removal")
public interface Fuel {
    /**
     * 检查是否为TFC大桶
     */
    static boolean isTFCBarrel(ItemStack stack) {
        return stack.is(ItemTags.create(new ResourceLocation("tfc", "barrels")));
    }

    /**
     * 从TFC大桶中获取流体
     */
    static FluidStack getFluidFromBarrel(ItemStack barrelStack) {
        if (barrelStack.isEmpty()) {
            System.out.println("1");
            return FluidStack.EMPTY;
        }

        CompoundTag tag = barrelStack.getTag();
        if (tag == null) {
            return FluidStack.EMPTY;
        }

        if (!tag.contains("BlockEntityTag")) {
            return FluidStack.EMPTY;
        }

        CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");
        // 修正：tank在inventory中，而不是直接位于BlockEntityTag
        if (!blockEntityTag.contains("inventory")) {
            return FluidStack.EMPTY;
        }

        CompoundTag inventoryTag = blockEntityTag.getCompound("inventory");

        // 根据实际NBT，流体数据在tank中
        if (!inventoryTag.contains("tank")) {
            return FluidStack.EMPTY;
        }

        CompoundTag tankTag = inventoryTag.getCompound("tank");
        if (tankTag.isEmpty()) {
            return FluidStack.EMPTY;
        }

        // 从tank中读取流体
        int amount = tankTag.getInt("Amount");
        String fluidName = tankTag.getString("FluidName");

        if (amount <= 0 || fluidName.isEmpty()) {
            return FluidStack.EMPTY;
        }

        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(fluidName));
        if (fluid == null) {
            return FluidStack.EMPTY;
        }

        return new FluidStack(fluid, amount);
    }

    /**
     * 检查桶内是否有LampFuel流体
     */
    default boolean hasLampFuelInBarrel(ItemStack barrelStack) {
        FluidStack fluid = getFluidFromBarrel(barrelStack);
        if (fluid.isEmpty()) {
            return false;
        }
        return Fuel.getFuel(fluid.getFluid()) != null && fluid.getAmount() > 0;
    }

    /**
     * 检查是否有桶LampFuel流体不足
     */
    default boolean isAnyBarrelLowOnLampFuel(EngineVehicle vehicle) {
        List<SlotDescription> fuelSlots = vehicle.getInventoryDescription().getSlots(VehicleInventoryDescription.BOILER);

        for (SlotDescription slot : fuelSlots) {
            ItemStack stack = vehicle.getInventory().getItem(slot.index());
            if (isTFCBarrel(stack)) {
                if (isBarrelLowOnLampFuel(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查单个桶是否LampFuel流体不足
     */
    private boolean isBarrelLowOnLampFuel(ItemStack barrelStack) {
        FluidStack fluid = Fuel.getFluidFromBarrel(barrelStack);
        LampFuel fuel = Fuel.getFuel(fluid.getFluid());
        if (fluid.isEmpty() || fuel == null) {
            return true;
        }

        // 获取燃烧率
        int burnRate = fuel.getBurnRate();
        if (burnRate <= 0) {
            return false;
        }

        float remainingTicks = fluid.getAmount() * (burnRate / 20.0f);

        // 低燃料阈值：剩余燃烧时间低于100刻
        return remainingTicks <= 600.0f;
    }

    static LampFuel getFuel(Fluid fluid) {
        // 遍历所有LampFuel，找到第一个匹配的并返回其燃烧率
        for (LampFuel fuel : LampFuel.MANAGER.getValues()) {
            if (fuel.getFluidIngredient().test(fluid)) {
                return fuel;
            }
        }
        return null;
    }
    // 更新桶内流体（关键：只更新NBT，不修改物品数量）
    static void updateBarrelFluid(ItemStack barrelStack, FluidStack fluidStack) {
        CompoundTag tag = barrelStack.getOrCreateTag();
        CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");
        CompoundTag inventoryTag = blockEntityTag.getCompound("inventory");
        CompoundTag tankTag = inventoryTag.getCompound("tank");

        if (fluidStack.getAmount() == 0) {
            // 清空tank
            inventoryTag.remove("tank");
        } else {
            tankTag.putInt("Amount", fluidStack.getAmount());
            tankTag.putString("FluidName", fluidStack.getFluid().getFluidType().toString());
        }

        inventoryTag.put("tank", tankTag);
    }
}
