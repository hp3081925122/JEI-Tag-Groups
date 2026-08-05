package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.RecipeGroupManager;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.gui.recipes.lookups.IngredientLookupState;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IngredientLookupState.class, remap = false)
public abstract class IngredientLookupStateMixin {
    @Shadow
    @Final
    private IRecipeManager recipeManager;

    @Shadow
    @Final
    private IFocusGroup focuses;

    @Inject(method = "getFocusedRecipes", at = @At("RETURN"), cancellable = true)
    // 在 JEI 计算配方数量前替换为折叠后的配方集合。
    private void jeiTagGroups$filterFocusedRecipes(CallbackInfoReturnable<IFocusedRecipes<?>> callback) {
        callback.setReturnValue(RecipeGroupManager.filterFocusedRecipes(callback.getReturnValue(), recipeManager, focuses));
    }
}
