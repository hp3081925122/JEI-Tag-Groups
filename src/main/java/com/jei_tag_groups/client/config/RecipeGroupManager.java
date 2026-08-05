package com.jei_tag_groups.client.config;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import com.jei_tag_groups.client.input.RecipeGroupKeyMappings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RecipeGroupManager {
    private static final int CAROUSEL_INTERVAL_TICKS = 40;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static volatile List<TagGroupConfig.RecipeGroupDefinition> definitions = List.of();
    private static final Map<IFocusedRecipes<?>, IFocusedRecipes<?>> filteredFocusedRecipes = new IdentityHashMap<>();
    private static final Map<IRecipeLayoutDrawable<?>, List<TagGroupConfig.RecipeGroupDefinition>> groupsByLayout = new IdentityHashMap<>();
    private static final Map<IRecipeCategory<?>, Set<TagGroupConfig.RecipeGroupDefinition>> collapsibleGroupsByCategory = new IdentityHashMap<>();
    private static List<RecipeLayoutWithButtons<?>> activeLayouts = List.of();
    private static IRecipeManager recipeManager;
    private static boolean expandKeyDown;
    private static int carouselTicks;
    private static int carouselOffset;
    private static long revision;

    private RecipeGroupManager() {
    }

    // 保存配方折叠配置并清空当前页面缓存。
    public static synchronized void setDefinitions(List<TagGroupConfig.RecipeGroupDefinition> newDefinitions) {
        definitions = List.copyOf(newDefinitions);
        filteredFocusedRecipes.clear();
        groupsByLayout.clear();
        collapsibleGroupsByCategory.clear();
        activeLayouts = List.of();
        carouselTicks = 0;
        carouselOffset = 0;
        revision++;
    }

    // 记录当前 JEI 配方管理器，供单类别配方页面创建临时布局以读取输入和输出。
    public static synchronized void setRecipeManager(IRecipeManager newRecipeManager) {
        if (recipeManager != newRecipeManager) {
            recipeManager = newRecipeManager;
            filteredFocusedRecipes.clear();
            collapsibleGroupsByCategory.clear();
        }
    }

    public static long revision() {
        return revision;
    }

    // 根据当前按键状态刷新折叠缓存，让按键修改后下一帧立即重建配方页。
    public static synchronized void updateExpandKeyState() {
        boolean currentKeyDown = RecipeGroupKeyMappings.TOGGLE_RECIPE_GROUPS.isDown();
        if (expandKeyDown == currentKeyDown) {
            return;
        }
        expandKeyDown = currentKeyDown;
        filteredFocusedRecipes.clear();
        groupsByLayout.clear();
        activeLayouts = List.of();
        revision++;
    }

    // 定时切换折叠组当前显示的代表配方，并让 JEI 重新创建当前页布局。
    public static synchronized void tickCarousel() {
        updateExpandKeyState();
        if (definitions.isEmpty() || expandKeyDown || collapsibleGroupsByCategory.values().stream().allMatch(Set::isEmpty)) {
            return;
        }
        carouselTicks++;
        if (carouselTicks < CAROUSEL_INTERVAL_TICKS) {
            return;
        }
        carouselTicks = 0;
        carouselOffset++;
        filteredFocusedRecipes.clear();
        groupsByLayout.clear();
        activeLayouts = List.of();
        revision++;
    }

    // 按当前折叠状态返回供 JEI 分页使用的配方集合。
    public static synchronized <T> IFocusedRecipes<T> filterFocusedRecipes(IFocusedRecipes<T> source, IRecipeManager manager, IFocusGroup focuses) {
        updateExpandKeyState();
        if (definitions.isEmpty()) {
            return source;
        }

        IRecipeManager activeRecipeManager = manager == null ? recipeManager : manager;
        if (activeRecipeManager == null) {
            LOGGER.warn("Recipe group filtering skipped because the JEI recipe manager is unavailable");
            return source;
        }

        IFocusedRecipes<?> cached = filteredFocusedRecipes.get(source);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            IFocusedRecipes<T> typedCached = (IFocusedRecipes<T>) cached;
            return typedCached;
        }

        IFocusedRecipes<T> filtered = createFilteredFocusedRecipes(source, activeRecipeManager, focuses);
        filteredFocusedRecipes.put(source, filtered);
        return filtered;
    }

    // 创建临时 JEI 布局读取自定义配方的输入和输出，再按组的首个成员折叠列表。
    private static <T> IFocusedRecipes<T> createFilteredFocusedRecipes(IFocusedRecipes<T> source, IRecipeManager activeRecipeManager, IFocusGroup focuses) {
        IRecipeCategory<T> category = source.getRecipeCategory();
        List<T> recipes = source.getRecipes();
        boolean needsLayout = definitions.stream().anyMatch(definition -> definition.inputItemId() != null || definition.outputItemId() != null);
        List<RecipeCandidate<T>> candidates = new ArrayList<>(recipes.size());
        for (T recipe : recipes) {
            Optional<IRecipeLayoutDrawable<T>> layout = Optional.empty();
            if (needsLayout) {
                try {
                    layout = activeRecipeManager.createRecipeLayoutDrawable(category, recipe, focuses);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Failed to inspect recipe for recipe group matching", exception);
                }
            }
            candidates.add(new RecipeCandidate<>(recipe, layout));
        }

        Map<TagGroupConfig.RecipeGroupDefinition, List<Integer>> matchingIndexes = new LinkedHashMap<>();
        for (TagGroupConfig.RecipeGroupDefinition definition : definitions) {
            List<Integer> indexes = new ArrayList<>();
            for (int index = 0; index < candidates.size(); index++) {
                RecipeCandidate<T> candidate = candidates.get(index);
                if (matches(definition, category, candidate.recipe(), candidate.layout())) {
                    indexes.add(index);
                }
            }
            if (indexes.size() > 1) {
                matchingIndexes.put(definition, indexes);
            }
        }

        collapsibleGroupsByCategory.put(category, Set.copyOf(matchingIndexes.keySet()));

        if (matchingIndexes.isEmpty()) {
            return source;
        }

        Map<Integer, List<TagGroupConfig.RecipeGroupDefinition>> groupsByIndex = new HashMap<>();
        for (Map.Entry<TagGroupConfig.RecipeGroupDefinition, List<Integer>> entry : matchingIndexes.entrySet()) {
            for (Integer index : entry.getValue()) {
                groupsByIndex.computeIfAbsent(index, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }

        Set<Integer> includedIndexes = new LinkedHashSet<>();
        for (int index = 0; index < candidates.size(); index++) {
            List<TagGroupConfig.RecipeGroupDefinition> groups = groupsByIndex.get(index);
            if (groups == null || groups.isEmpty()) {
                includedIndexes.add(index);
                continue;
            }

            boolean include = false;
            for (TagGroupConfig.RecipeGroupDefinition definition : groups) {
                List<Integer> indexes = matchingIndexes.get(definition);
                int carouselIndex = indexes.get(Math.floorMod(carouselOffset, indexes.size()));
                if (expandKeyDown || carouselIndex == index) {
                    include = true;
                    break;
                }
            }
            if (include) {
                includedIndexes.add(index);
            }
        }

        List<T> result = new ArrayList<>(includedIndexes.size());
        for (Integer index : includedIndexes) {
            result.add(candidates.get(index).recipe());
        }
        return new StaticFocusedRecipes<>(category, List.copyOf(result));
    }

    // 判断配方 ID、ID 包含文本、产出物品和输入物品等配置条件是否全部满足。
    private static <T> boolean matches(TagGroupConfig.RecipeGroupDefinition definition, IRecipeCategory<T> category, T recipe, Optional<IRecipeLayoutDrawable<T>> layout) {
        ResourceLocation recipeId;
        try {
            recipeId = category.getRegistryName(recipe);
        } catch (RuntimeException exception) {
            recipeId = null;
        }
        if (definition.recipeId() != null && !definition.recipeId().equals(recipeId)) {
            return false;
        }
        if (definition.recipeIdContains() != null && (recipeId == null || !recipeId.toString().contains(definition.recipeIdContains()))) {
            return false;
        }

        if (definition.outputItemId() != null && !containsItem(layout, RecipeIngredientRole.OUTPUT, definition.outputItemId())) {
            return false;
        }
        return definition.inputItemId() == null || containsItem(layout, RecipeIngredientRole.INPUT, definition.inputItemId());
    }

    // 检查指定角色的任意 JEI 物品槽是否包含目标物品。
    private static boolean containsItem(Optional<? extends IRecipeLayoutDrawable<?>> layout, RecipeIngredientRole role, ResourceLocation itemId) {
        if (layout.isEmpty()) {
            return false;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            return false;
        }
        IRecipeSlotsView slots = layout.get().getRecipeSlotsView();
        return slots.getSlotViews(role).stream()
            .flatMap(slot -> slot.getItemStacks())
            .anyMatch(stack -> stack.getItem() == item);
    }

    // 在 JEI 完成布局创建后记录当前可见配方与其匹配组的对应关系。
    public static synchronized void registerRecipeLayouts(IRecipeLayoutList layoutList) {
        groupsByLayout.clear();
        if (definitions.isEmpty()) {
            return;
        }

        List<RecipeLayoutWithButtons<?>> layouts = layoutList.subList(0, layoutList.size());
        for (RecipeLayoutWithButtons<?> layoutWithButtons : layouts) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.recipeLayout();
            List<TagGroupConfig.RecipeGroupDefinition> matchingGroups = new ArrayList<>();
            addMatchingGroups(layout, matchingGroups);
            if (!matchingGroups.isEmpty()) {
                groupsByLayout.put(layout, List.copyOf(matchingGroups));
            }
        }
    }

    // 记录当前页面实际绘制的布局，点击和边框都只处理这一页。
    public static synchronized void setActiveRecipeLayouts(List<RecipeLayoutWithButtons<?>> layouts) {
        activeLayouts = List.copyOf(layouts);
    }

    // 判断指定配方类别是否存在可以被按键展开的折叠组。
    public static synchronized boolean hasCollapsibleGroupForCategory(IRecipeCategory<?> category) {
        Set<TagGroupConfig.RecipeGroupDefinition> groups = collapsibleGroupsByCategory.get(category);
        return groups != null && !groups.isEmpty();
    }

    // 为当前页的折叠配方绘制一圈独立边框，保留 JEI 原生卡片边框。
    public static synchronized void drawRecipeGroupBorders(GuiGraphics graphics) {
        for (RecipeLayoutWithButtons<?> layoutWithButtons : activeLayouts) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.recipeLayout();
            List<TagGroupConfig.RecipeGroupDefinition> matchingGroups = groupsByLayout.get(layout);
            if (matchingGroups == null) {
                continue;
            }
            Rect2i area = layout.getRectWithBorder();
            for (TagGroupConfig.RecipeGroupDefinition definition : matchingGroups) {
                int x = area.getX() - 1;
                int y = area.getY() - 1;
                int width = area.getWidth() + 2;
                int height = area.getHeight() + 2;
                int color = definition.borderColor();
                graphics.fill(x, y, x + width, y + 1, color);
                graphics.fill(x, y + height - 1, x + width, y + height, color);
                graphics.fill(x, y + 1, x + 1, y + height - 1, color);
                graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
            }
        }
    }

    // 将当前布局加入所有匹配的配置组，供绘制层复用同一份判断结果。
    private static void addMatchingGroups(IRecipeLayoutDrawable<?> layout, List<TagGroupConfig.RecipeGroupDefinition> matchingGroups) {
        addMatchingGroupsTyped(layout, matchingGroups);
    }

    // 使用泛型安全地读取自定义 JEI 配方的类别、配方对象和物品槽。
    private static <T> void addMatchingGroupsTyped(IRecipeLayoutDrawable<T> layout, List<TagGroupConfig.RecipeGroupDefinition> matchingGroups) {
        IRecipeCategory<T> category = layout.getRecipeCategory();
        T recipe = layout.getRecipe();
        Optional<IRecipeLayoutDrawable<T>> currentLayout = Optional.of(layout);
        for (TagGroupConfig.RecipeGroupDefinition definition : definitions) {
            if (matches(definition, category, recipe, currentLayout)) {
                matchingGroups.add(definition);
            }
        }
    }

    private record RecipeCandidate<T>(T recipe, Optional<IRecipeLayoutDrawable<T>> layout) {
    }
}
