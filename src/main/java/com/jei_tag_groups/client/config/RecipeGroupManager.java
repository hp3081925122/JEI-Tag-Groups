package com.jei_tag_groups.client.config;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

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
    private static List<IRecipeLayoutWithButtons<?>> activeLayouts = List.of();
    private static IRecipeManager recipeManager;
    private static final Set<TagGroupConfig.RecipeGroupDefinition> expandedGroups = new LinkedHashSet<>();
    private static int carouselTicks;
    private static int carouselOffset;
    private static long revision;
    private static IRecipeLayoutDrawable<?> hoveredLayout;
    private static IRecipeCategory<?> pendingCategory;
    private static Object pendingRecipe;
    private static List<TagGroupConfig.RecipeGroupDefinition> pendingGroups = List.of();
    private static boolean pendingExpand;

    private RecipeGroupManager() {
    }

    // 保存配方折叠配置并清空当前页面缓存。
    public static synchronized void setDefinitions(List<TagGroupConfig.RecipeGroupDefinition> newDefinitions) {
        definitions = List.copyOf(newDefinitions);
        filteredFocusedRecipes.clear();
        groupsByLayout.clear();
        collapsibleGroupsByCategory.clear();
        activeLayouts = List.of();
        expandedGroups.clear();
        hoveredLayout = null;
        pendingCategory = null;
        pendingRecipe = null;
        pendingGroups = List.of();
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
            expandedGroups.clear();
            hoveredLayout = null;
            pendingCategory = null;
            pendingRecipe = null;
            pendingGroups = List.of();
        }
    }

    public static long revision() {
        return revision;
    }

    // 定时切换折叠组当前显示的代表配方，并让 JEI 重新创建当前页布局。
    public static synchronized void tickCarousel() {
        if (definitions.isEmpty() || collapsibleGroupsByCategory.values().stream().allMatch(Set::isEmpty)) {
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
        LOGGER.debug("Recipe group carousel advanced: offset={}, revision={}", carouselOffset, revision);
    }

    // 按当前折叠状态返回供 JEI 分页使用的配方集合。
    public static synchronized <T> IFocusedRecipes<T> filterFocusedRecipes(IFocusedRecipes<T> source, IRecipeManager manager, IFocusGroup focuses) {
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
                if (expandedGroups.contains(definition) || carouselIndex == index) {
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
        LOGGER.debug("Filtered JEI recipe groups: category={}, sourceSize={}, resultSize={}, expandedGroups={}, carouselOffset={}",
            category.getClass().getName(), recipes.size(), result.size(), expandedGroups.size(), carouselOffset);
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
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null) {
            return false;
        }
        IRecipeSlotsView slots = layout.get().getRecipeSlotsView();
        return slots.getSlotViews(role).stream()
            .flatMap(slot -> slot.getItemStacks())
            .anyMatch(stack -> stack.getItem() == item);
    }

    // 在 JEI 完成布局创建后记录当前可见配方与其匹配组的对应关系。
    public static synchronized void registerRecipeLayouts(List<IRecipeLayoutWithButtons<?>> layouts) {
        groupsByLayout.clear();
        if (definitions.isEmpty()) {
            return;
        }

        for (IRecipeLayoutWithButtons<?> layoutWithButtons : layouts) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.getRecipeLayout();
            List<TagGroupConfig.RecipeGroupDefinition> matchingGroups = new ArrayList<>();
            addMatchingGroups(layout, matchingGroups);
            if (!matchingGroups.isEmpty()) {
                groupsByLayout.put(layout, List.copyOf(matchingGroups));
            }
        }
        LOGGER.debug("Registered JEI recipe group layouts: layoutCount={}, groupedLayoutCount={}", layouts.size(), groupsByLayout.size());
    }

    // 记录当前页面实际绘制的布局和鼠标位置，按键只切换鼠标悬停的配方组。
    public static synchronized void setActiveRecipeLayouts(List<IRecipeLayoutWithButtons<?>> layouts, int mouseX, int mouseY) {
        activeLayouts = List.copyOf(layouts);
        IRecipeLayoutDrawable<?> previousHoveredLayout = hoveredLayout;
        hoveredLayout = null;
        for (IRecipeLayoutWithButtons<?> layoutWithButtons : activeLayouts) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.getRecipeLayout();
            if (layout.isMouseOver(mouseX, mouseY)) {
                hoveredLayout = layout;
                break;
            }
        }
        if (previousHoveredLayout != hoveredLayout) {
            LOGGER.debug("Recipe group hover changed: hovered={}, mouse=({}, {})",
                hoveredLayout == null ? "none" : groupsByLayout.getOrDefault(hoveredLayout, List.of()), mouseX, mouseY);
        }
    }

    // 根据点击坐标确认当前配方卡片确实属于折叠组后执行左键切换。
    public static synchronized boolean toggleRecipeGroupAt(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        IRecipeLayoutDrawable<?> clickedLayout = null;
        for (IRecipeLayoutWithButtons<?> layoutWithButtons : activeLayouts) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.getRecipeLayout();
            if (layout.isMouseOver(mouseX, mouseY)) {
                clickedLayout = layout;
                break;
            }
        }
        if (clickedLayout == null || groupsByLayout.getOrDefault(clickedLayout, List.of()).isEmpty()) {
            return false;
        }
        hoveredLayout = clickedLayout;
        toggleHoveredRecipeGroup();
        return true;
    }

    // 切换鼠标悬停配方对应的折叠组，并保持 JEI 当前页码不变。
    public static synchronized void toggleHoveredRecipeGroup() {
        if (hoveredLayout == null) {
            LOGGER.debug("Recipe group toggle ignored: no hovered recipe layout");
            return;
        }

        List<TagGroupConfig.RecipeGroupDefinition> matchingGroups = groupsByLayout.get(hoveredLayout);
        if (matchingGroups == null || matchingGroups.isEmpty()) {
            LOGGER.debug("Recipe group toggle ignored: hovered layout has no collapsible group");
            return;
        }

        boolean expand = matchingGroups.stream().anyMatch(definition -> !expandedGroups.contains(definition));
        if (expand) {
            expandedGroups.addAll(matchingGroups);
        } else {
            expandedGroups.removeAll(matchingGroups);
        }
        pendingCategory = hoveredLayout.getRecipeCategory();
        pendingRecipe = hoveredLayout.getRecipe();
        pendingGroups = List.copyOf(matchingGroups);
        pendingExpand = expand;
        filteredFocusedRecipes.clear();
        groupsByLayout.clear();
        activeLayouts = List.of();
        hoveredLayout = null;
        carouselTicks = 0;
        revision++;
        LOGGER.debug("Recipe group toggle applied: expand={}, groupCount={}, revision={}", expand, matchingGroups.size(), revision);
    }

    // 在 JEI 使用新配方列表创建布局前，将页码调整到展开配方或折叠代表配方所在页。
    public static synchronized boolean adjustRecipePage(ILookupState state) {
        if (pendingCategory == null) {
            return false;
        }

        IFocusedRecipes<?> focusedRecipes = state.getFocusedRecipes();
        if (focusedRecipes.getRecipeCategory() != pendingCategory) {
            LOGGER.debug("Recipe page adjustment skipped: category changed before refresh");
            pendingCategory = null;
            pendingRecipe = null;
            pendingGroups = List.of();
            return false;
        }

        List<?> recipes = focusedRecipes.getRecipes();
        int targetIndex = -1;
        if (pendingExpand) {
            for (int index = 0; index < recipes.size(); index++) {
                Object recipe = recipes.get(index);
                if (recipe == pendingRecipe || (recipe != null && recipe.equals(pendingRecipe))) {
                    targetIndex = index;
                    break;
                }
            }
        } else {
            IRecipeManager activeRecipeManager = recipeManager;
            IRecipeCategory<?> category = focusedRecipes.getRecipeCategory();
            for (int index = 0; index < recipes.size() && targetIndex < 0; index++) {
                Object recipe = recipes.get(index);
                Optional<IRecipeLayoutDrawable<?>> layout = Optional.empty();
                if (activeRecipeManager != null) {
                    try {
                        @SuppressWarnings({"rawtypes", "unchecked"})
                        Optional<IRecipeLayoutDrawable<?>> createdLayout = (Optional) activeRecipeManager.createRecipeLayoutDrawable(
                            (IRecipeCategory) category,
                            recipe,
                            state.getFocuses()
                        );
                        layout = createdLayout;
                    } catch (RuntimeException exception) {
                        LOGGER.debug("Failed to create layout while locating collapsed recipe", exception);
                    }
                }
                for (TagGroupConfig.RecipeGroupDefinition definition : pendingGroups) {
                    try {
                        @SuppressWarnings({"rawtypes", "unchecked"})
                        boolean matches = matches(definition, (IRecipeCategory) category, recipe, (Optional) layout);
                        if (matches) {
                            targetIndex = index;
                            break;
                        }
                    } catch (RuntimeException exception) {
                        LOGGER.debug("Failed to match collapsed recipe group", exception);
                    }
                }
            }
        }

        int recipesPerPage = Math.max(1, state.getRecipesPerPage());
        if (targetIndex < 0) {
            targetIndex = recipes.isEmpty() ? 0 : Math.min(state.getRecipeIndex(), recipes.size() - 1);
        }
        int targetPage = targetIndex / recipesPerPage;
        int previousPage = state.getRecipeIndex() / recipesPerPage;
        state.goToFirstPage();
        for (int page = 0; page < targetPage; page++) {
            state.nextPage();
        }
        LOGGER.debug("Adjusted JEI recipe page: expand={}, recipeCount={}, recipesPerPage={}, previousPage={}, targetPage={}, targetIndex={}",
            pendingExpand, recipes.size(), recipesPerPage, previousPage, targetPage, targetIndex);
        pendingCategory = null;
        pendingRecipe = null;
        pendingGroups = List.of();
        return true;
    }

    // 轮播或配置刷新后恢复原页，并在新配方数量减少时限制到最后一页。
    public static synchronized void restoreRecipePage(ILookupState state, int requestedPage) {
        int recipesPerPage = Math.max(1, state.getRecipesPerPage());
        int recipeCount = state.getFocusedRecipes().getRecipes().size();
        int pageCount = recipeCount <= 1 ? 1 : (recipeCount + recipesPerPage - 1) / recipesPerPage;
        int targetPage = Math.max(0, Math.min(requestedPage, pageCount - 1));
        state.goToFirstPage();
        for (int page = 0; page < targetPage; page++) {
            state.nextPage();
        }
        LOGGER.debug("Restored JEI recipe page after refresh: requestedPage={}, targetPage={}, recipeCount={}, recipesPerPage={}",
            requestedPage, targetPage, recipeCount, recipesPerPage);
    }

    // 判断指定配方类别是否存在可以被按键展开的折叠组。
    public static synchronized boolean hasCollapsibleGroupForCategory(IRecipeCategory<?> category) {
        Set<TagGroupConfig.RecipeGroupDefinition> groups = collapsibleGroupsByCategory.get(category);
        return groups != null && !groups.isEmpty();
    }

    // 为当前页的折叠配方绘制一圈独立边框，保留 JEI 原生卡片边框。
    public static synchronized void drawRecipeGroupBorders(GuiGraphics graphics) {
        for (IRecipeLayoutWithButtons<?> layoutWithButtons : activeLayouts) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.getRecipeLayout();
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
        Set<TagGroupConfig.RecipeGroupDefinition> collapsibleGroups = collapsibleGroupsByCategory.get(category);
        if (collapsibleGroups == null || collapsibleGroups.isEmpty()) {
            return;
        }
        for (TagGroupConfig.RecipeGroupDefinition definition : definitions) {
            if (collapsibleGroups.contains(definition) && matches(definition, category, recipe, currentLayout)) {
                matchingGroups.add(definition);
            }
        }
    }

    private record RecipeCandidate<T>(T recipe, Optional<IRecipeLayoutDrawable<T>> layout) {
    }
}
