package com.plank.firma_aircraft.mixins;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;

@Mixin(RecipeManager.class)
public class SimpleRecipeRemovalMixin {

    /**
     * 在apply方法中注入，过滤掉immersive_aircraft的配方JSON
     * 这是最早可以干预配方加载的时机
     */
    @Inject(method = "apply*", at = @At("HEAD"))
    private void filterRecipeJson(Map<ResourceLocation, JsonElement> recipesJson,
                                  ResourceManager resourceManager,
                                  ProfilerFiller profiler,
                                  CallbackInfo ci) {

        // 在配方JSON被解析之前就删除immersive_aircraft的条目
        Iterator<Map.Entry<ResourceLocation, JsonElement>> iterator = recipesJson.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = iterator.next();
            ResourceLocation recipeId = entry.getKey();

            if (recipeId.getNamespace().equals("immersive_aircraft") || recipeId.getNamespace().equals("warfare_wings")) {
                iterator.remove();
            }
        }
    }
}