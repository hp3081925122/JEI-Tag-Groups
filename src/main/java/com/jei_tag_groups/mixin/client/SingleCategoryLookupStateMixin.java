package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.RecipeGroupManager;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.SingleCategoryLookupState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SingleCategoryLookupState.class, remap = false)
public abstract class SingleCategoryLookupStateMixin {
    @Shadow
    @Final
    private IFocusGroup focusGroup;

    @Inject(method = "getFocusedRecipes", at = @At("RETURN"), cancellable = true)
    // 单类别配方入口也使用相同的折叠条件和分页数量。
    private void jeiTagGroups$filterFocusedRecipes(CallbackInfoReturnable<IFocusedRecipes<?>> callback) {
        callback.setReturnValue(RecipeGroupManager.filterFocusedRecipes(callback.getReturnValue(), null, focusGroup));
    }
}
