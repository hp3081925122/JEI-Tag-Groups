package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.RecipeGroupManager;
import com.mojang.logging.LogUtils;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;

@Mixin(value = RecipesGui.class, remap = false)
public abstract class RecipesGuiMixin {
    @Unique
    private static final Logger jeiTagGroups$logger = LogUtils.getLogger();

    @Shadow
    @Final
    private IRecipeGuiLogic logic;

    @Shadow
    private void updateLayout() {
    }

    @Unique
    private long jeiTagGroups$recipeGroupRevision = -1L;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    // 配置、轮播或展开状态变化后重建 JEI 配方页，但保留当前页码。
    private void jeiTagGroups$refreshRecipePage(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo callback) {
        long revision = RecipeGroupManager.revision();
        if (jeiTagGroups$recipeGroupRevision != revision) {
            jeiTagGroups$recipeGroupRevision = revision;
            updateLayout();
            jeiTagGroups$logger.debug("Refreshing JEI recipe layouts without resetting page: revision={}", revision);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    // 折叠配方入口统一支持左键展开或折叠，并避免 JEI 同时处理同一次点击。
    private void jeiTagGroups$toggleRecipeGroup(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> callback) {
        if (RecipeGroupManager.toggleRecipeGroupAt(event.x(), event.y(), event.button())) {
            callback.setReturnValue(true);
        }
    }
}
