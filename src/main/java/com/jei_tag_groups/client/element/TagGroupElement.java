package com.jei_tag_groups.client.element;

import com.jei_tag_groups.client.config.TagGroupConfig;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.util.FocusUtil;
import mezz.jei.library.gui.ingredients.TagContentTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

import static com.jei_tag_groups.client.config.TagGroupManager.toggle;

public final class TagGroupElement implements IElement<ItemStack> {
    private final ITypedIngredient<ItemStack> typedIngredient;
    private final TagGroupConfig.GroupKey groupKey;
    private final int borderColor;
    private final List<ItemStack> memberStacks;
    private final String displayNameKey;
    private final List<String> tooltipKeys;

    public TagGroupElement(ITypedIngredient<ItemStack> typedIngredient, TagGroupConfig.TagGroupDefinition definition, List<ItemStack> memberStacks) {
        this.typedIngredient = typedIngredient;
        this.groupKey = definition.groupKey();
        this.borderColor = definition.borderColor();
        this.memberStacks = List.copyOf(memberStacks);
        this.displayNameKey = definition.displayNameKey();
        this.tooltipKeys = List.copyOf(definition.tooltipKeys());
    }

    @Override
    // 使用代表物品作为 JEI 原生查询和渲染入口。
    public ITypedIngredient<ItemStack> getTypedIngredient() {
        return typedIngredient;
    }

    @Override
    public Optional<mezz.jei.gui.bookmarks.IBookmark> getBookmark() {
        return Optional.empty();
    }

    @Override
    public IDrawable createRenderOverlay() {
        return new TagGroupBorderDrawable(borderColor);
    }

    @Override
    public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
        recipesGui.show(focusUtil.createFocuses(typedIngredient, roles));
    }

    @Override
    // 在物品标识和成员数量之间显示左键操作提示，并保留成员图标预览。
    public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper, IIngredientRenderer<ItemStack> renderer, IIngredientHelper<ItemStack> ingredientHelper) {
        if (displayNameKey != null) {
            tooltip.add(Component.translatable(displayNameKey));
        }
        for (String tooltipKey : tooltipKeys) {
            tooltip.add(Component.translatable(tooltipKey));
        }
        String targetKey = switch (groupKey.type()) {
            case TAG -> "tag";
            case ITEM -> "item";
            case ITEM_NAME -> "item_name";
        };
        tooltip.add(Component.translatable("jei_tag_groups.tooltip." + targetKey, groupKey.value()));
        tooltip.add(Component.translatable("jei_tag_groups.tooltip.toggle_item_groups"));
        tooltip.add(Component.translatable("jei_tag_groups.tooltip.count", memberStacks.size()));
        tooltip.add(new TagContentTooltipComponent<>(renderer, memberStacks));
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    // 折叠入口没有需要随 JEI 帧更新的状态。
    public void tick() {
    }

    @Override
    // 只拦截真实左键，模拟输入和其他 JEI 操作继续走原有流程。
    public boolean handleClick(UserInput input, IInternalKeyMappings keyMappings) {
        if (!input.isSimulate() && input.is(keyMappings.getLeftClick())) {
            toggle(groupKey);
            return true;
        }
        return false;
    }
}
