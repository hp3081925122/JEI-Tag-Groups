package com.jei_tag_groups.mixin.client;

import com.jei_tag_groups.client.IngredientGridLayoutRefresher;
import mezz.jei.gui.overlay.ingredients.IIngredientGrid;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigationController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = IngredientGridWithNavigationController.class, remap = false)
public abstract class IngredientGridWithNavigationControllerMixin implements IngredientGridLayoutRefresher {
    @Shadow
    @Final
    private IIngredientGrid ingredientGrid;

    @Shadow
    public abstract int getPageNumber();

    @Shadow
    private boolean usesScrollbar() {
        throw new AssertionError();
    }

    @Shadow
    private void updateLayoutStartingAt(int firstItemIndex) {
        throw new AssertionError();
    }

    @Override
    // 按当前页码或滚动行换算首个物品索引，从该位置重建网格，不跳回第一页。
    public void jeiTagGroups$updateLayoutKeepingFirstIndex() {
        int itemsPerStep = usesScrollbar() ? ingredientGrid.getColumnCount() : ingredientGrid.size();
        updateLayoutStartingAt(getPageNumber() * itemsPerStep);
    }
}
