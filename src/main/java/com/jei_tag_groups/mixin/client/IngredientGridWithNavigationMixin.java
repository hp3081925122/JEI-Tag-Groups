package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.TagGroupManager;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = IngredientGridWithNavigation.class, remap = false)
public abstract class IngredientGridWithNavigationMixin {
    @Shadow
    @Final
    private IIngredientGridSource ingredientSource;

    @Shadow
    @Final
    private IngredientGrid ingredientGrid;

    @Shadow
    private int firstItemIndex;

    @Unique
    private long jeiTagGroups$lastRevision = -1L;

    @Inject(method = "draw", at = @At("HEAD"))
    // 配置或展开状态变化后，在下一帧重建网格并定位到新列表中的对应页面。
    private void jeiTagGroups$refreshLayout(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
        long revision = TagGroupManager.revision();
        if (revision != jeiTagGroups$lastRevision) {
            jeiTagGroups$lastRevision = revision;
            List<IElement<?>> elements = ingredientSource.getElements();
            Integer preferredIndex = TagGroupManager.preferredItemIndex(elements);
            if (preferredIndex != null && ingredientGrid.size() > 0) {
                firstItemIndex = preferredIndex / ingredientGrid.size() * ingredientGrid.size();
            }
            ((IngredientGridWithNavigation) (Object) this).updateLayout(false);
        }
    }
}
