package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.TagGroupManager;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
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
    private void markLayoutDirty() {
    }

    @Unique
    private long jeiTagGroups$lastRevision = -1L;

    @Inject(method = "drawForeground", at = @At("HEAD"))
    // 配置或展开状态变化后，在下一帧重建网格并定位到新列表中的对应页面。
    private void jeiTagGroups$refreshLayout(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
        long revision = TagGroupManager.revision();
        if (revision != jeiTagGroups$lastRevision) {
            jeiTagGroups$lastRevision = revision;
            List<IElement<?>> elements = ingredientSource.getElements();
            Integer preferredIndex = TagGroupManager.preferredItemIndex(elements);
            markLayoutDirty();
            if (preferredIndex != null && preferredIndex >= 0 && preferredIndex < elements.size()) {
                ((IngredientGridWithNavigation) (Object) this).updateLayoutKeepingPageAnchorVisible(elements.get(preferredIndex));
            }
        }
    }
}
