package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.TagGroupManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin {
    @Shadow
    @Final
    private IIngredientManager ingredientManager;

    @Inject(method = "getElements", at = @At("RETURN"), cancellable = true)
    // 只替换 JEI 已完成搜索和隐藏过滤后的主物品列表。
    private void jeiTagGroups$aggregateElements(CallbackInfoReturnable<List<IElement<?>>> callback) {
        callback.setReturnValue(TagGroupManager.aggregate(callback.getReturnValue(), ingredientManager));
    }
}
