package com.jei_tag_groups.client;

// 由 JEI 物品网格控制器实现，用于在分组变化后保持当前位置重新布局。
public interface IngredientGridLayoutRefresher {
    void jeiTagGroups$updateLayoutKeepingFirstIndex();
}
