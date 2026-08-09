package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.RecipeGroupManager;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLogic;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.ILookupState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = RecipeGuiLogic.class, remap = false)
public abstract class RecipeGuiLogicMixin {
    @Shadow
    @Final
    private IRecipeManager recipeManager;

    @Shadow
    private IRecipeCategory<?> cachedRecipeCategory;

    @Shadow
    private IRecipeLayoutList cachedRecipeLayoutsWithButtons;

    @Shadow
    private ILookupState state;

    @Unique
    private long jeiTagGroups$recipeGroupRevision = -1L;

    @Inject(method = "<init>", at = @At("RETURN"))
    // 保存 JEI 管理器，供单类别配方状态读取输入和输出条件。
    private void jeiTagGroups$captureRecipeManager(CallbackInfo callback) {
        RecipeGroupManager.setRecipeManager(recipeManager);
    }

    @Inject(method = "getVisibleRecipeLayoutsWithButtons", at = @At("HEAD"))
    // 配方组展开状态变化后让 JEI 重新创建布局和分页数据。
    private void jeiTagGroups$invalidateRecipeLayouts(CallbackInfoReturnable<?> callback) {
        long revision = RecipeGroupManager.revision();
        if (jeiTagGroups$recipeGroupRevision != revision) {
            int recipesPerPage = Math.max(1, state.getRecipesPerPage());
            int previousPage = state.getRecipeIndex() / recipesPerPage;
            jeiTagGroups$recipeGroupRevision = revision;
            cachedRecipeLayoutsWithButtons = null;
            cachedRecipeCategory = null;
            boolean pageAdjusted = RecipeGroupManager.adjustRecipePage(state);
            if (!pageAdjusted) {
                RecipeGroupManager.restoreRecipePage(state, previousPage);
            }
        }
    }

    @Inject(method = "getVisibleRecipeLayoutsWithButtons", at = @At("RETURN"))
    // 记录 JEI 当前页已经生成的布局，供边框绘制和鼠标悬停识别。
    private void jeiTagGroups$registerRecipeLayouts(CallbackInfoReturnable<List<IRecipeLayoutWithButtons<?>>> callback) {
        RecipeGroupManager.registerRecipeLayouts(callback.getReturnValue());
    }
}
