package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.TagGroupManager;
import mezz.jei.gui.overlay.ingredients.IngredientGrid;
import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IngredientGrid.class, remap = false)
public abstract class IngredientGridMixin {
    @Shadow
    @Final
    private IngredientListRenderer ingredientListRenderer;

    @Inject(method = "draw", at = @At(value = "INVOKE", target = "Lmezz/jei/gui/overlay/ingredients/IngredientListRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;)V", shift = At.Shift.AFTER))
    // 在物品绘制完成后追加展开组的连续外轮廓。
    private void jeiTagGroups$drawExpandedBorders(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callback) {
        TagGroupManager.drawExpandedGroupBorders(graphics, ingredientListRenderer.getSlots());
    }
}
