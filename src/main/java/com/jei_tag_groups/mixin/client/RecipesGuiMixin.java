package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.RecipeGroupManager;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipesGui.class, remap = false)
public abstract class RecipesGuiMixin {
    @Shadow
    @Final
    private IRecipeGuiLogic logic;

    @Shadow
    private void updateLayout() {
    }

    @Unique
    private long jeiTagGroups$recipeGroupRevision = -1L;

    @Inject(method = "render", at = @At("HEAD"))
    // 配置或展开状态变化后重建 JEI 配方页并回到第一页。
    private void jeiTagGroups$refreshRecipePage(CallbackInfo callback) {
        RecipeGroupManager.updateExpandKeyState();
        long revision = RecipeGroupManager.revision();
        if (jeiTagGroups$recipeGroupRevision != revision) {
            jeiTagGroups$recipeGroupRevision = revision;
            logic.goToFirstPage();
            updateLayout();
        }
    }
}
