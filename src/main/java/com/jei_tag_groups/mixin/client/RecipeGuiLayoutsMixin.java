package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.RecipeGroupManager;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import mezz.jei.common.input.interaction.ReleaseInsideBounds;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(value = RecipeGuiLayouts.class, remap = false)
public abstract class RecipeGuiLayoutsMixin {
    @Shadow
    @Final
    private List<IRecipeLayoutWithButtons<?>> recipeLayoutsWithButtons;

    @Shadow
    public abstract boolean isInsideViewport(double mouseX, double mouseY);

    @Inject(method = "draw", at = @At("HEAD"))
    // 记录 JEI 当前页的布局，避免点击旧页面的配方组。
    private void jeiTagGroups$setActiveRecipeLayouts(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfoReturnable<?> callback) {
        RecipeGroupManager.setActiveRecipeLayouts(recipeLayoutsWithButtons, mouseX, mouseY);
    }

    @Inject(method = "draw", at = @At("RETURN"))
    // 在原生配方绘制完成后追加折叠组边框。
    private void jeiTagGroups$drawRecipeGroupBorders(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfoReturnable<?> callback) {
        RecipeGroupManager.drawRecipeGroupBorders(graphics);
    }

    @Inject(method = "beginInput", at = @At("HEAD"), cancellable = true)
    // 左键按在折叠配方卡片上时接管本次点击，松开时仍在卡片和可视区域内才展开或折叠，避免 JEI 同时处理同一次点击。
    private void jeiTagGroups$beginRecipeGroupToggle(Screen screen, UserInput input, IInternalKeyMappings keyBindings, CallbackInfoReturnable<Optional<IInputInteraction>> callback) {
        if (!input.isMouseInput() || !input.is(keyBindings.getLeftClick())) {
            return;
        }
        double mouseX = input.getMouseX();
        double mouseY = input.getMouseY();
        if (!isInsideViewport(mouseX, mouseY)) {
            return;
        }
        IRecipeLayoutDrawable<?> layout = RecipeGroupManager.findCollapsibleLayoutAt(mouseX, mouseY);
        if (layout == null) {
            return;
        }
        InputAction action = InputAction.run(() -> RecipeGroupManager.toggleRecipeGroup(layout))
            .within(layout::isMouseOver);
        callback.setReturnValue(Optional.of(new ReleaseInsideBounds(action, this::isInsideViewport)));
    }
}
