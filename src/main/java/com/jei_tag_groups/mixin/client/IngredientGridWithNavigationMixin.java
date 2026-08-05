package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.config.TagGroupManager;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IngredientGridWithNavigation.class, remap = false)
public abstract class IngredientGridWithNavigationMixin {
    @Unique
    private long jeiTagGroups$lastRevision = -1L;

    @Inject(method = "draw", at = @At("HEAD"))
    // 配置或展开状态变化后，在下一帧重建网格并重置页码。
    private void jeiTagGroups$refreshLayout(Minecraft minecraft, GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
        long revision = TagGroupManager.revision();
        if (revision != jeiTagGroups$lastRevision) {
            jeiTagGroups$lastRevision = revision;
            ((IngredientGridWithNavigation) (Object) this).updateLayout(true);
        }
    }
}
