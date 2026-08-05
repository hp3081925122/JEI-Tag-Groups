package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.RecipeGroupManager;
import com.jei_tag_groups.client.input.RecipeGroupKeyMappings;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.recipes.RecipeCategoryTab;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeCategoryTab.class, remap = false)
public abstract class RecipeCategoryTabMixin {
    @Shadow
    @Final
    private IRecipeCategory<?> category;

    @Inject(method = "getTooltip", at = @At("RETURN"))
    // 当前分类存在折叠配方时，在顶部分类图标提示动态按键名称。
    private void jeiTagGroups$addRecipeGroupKeyHint(CallbackInfoReturnable<JeiTooltip> callback) {
        if (RecipeGroupManager.hasCollapsibleGroupForCategory(category)) {
            callback.getReturnValue().addKeyUsageComponent(
                "jei_tag_groups.tooltip.toggle_recipe_groups",
                RecipeGroupKeyMappings.TOGGLE_RECIPE_GROUPS.getTranslatedKeyMessage().copy()
            );
        }
    }
}
