package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.RecipeGroupManager;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = RecipeGuiLayouts.class, remap = false)
public abstract class RecipeGuiLayoutsMixin {
    @Shadow
    @Final
    private List<IRecipeLayoutWithButtons<?>> recipeLayoutsWithButtons;

    @Inject(method = "draw", at = @At("HEAD"))
    // 记录 JEI 当前页的布局，避免点击旧页面的配方组。
    private void jeiTagGroups$setActiveRecipeLayouts(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfoReturnable<?> callback) {
        RecipeGroupManager.setActiveRecipeLayouts(recipeLayoutsWithButtons, mouseX, mouseY);
    }

    @Inject(method = "draw", at = @At("RETURN"))
    // 在原生配方绘制完成后追加折叠组边框。
    private void jeiTagGroups$drawRecipeGroupBorders(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfoReturnable<?> callback) {
        RecipeGroupManager.drawRecipeGroupBorders(graphics);
    }
}
