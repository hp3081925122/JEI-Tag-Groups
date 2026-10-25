package com.jei_tag_groups.client.config;

import com.jei_tag_groups.client.element.TagGroupElement;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class TagGroupManager {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static volatile List<TagGroupConfig.TagGroupDefinition> definitions = List.of();
    private static AutoItemGroupConfig.Settings autoItemSettings = AutoItemGroupConfig.Settings.disabled();
    private static final Set<TagGroupConfig.GroupKey> expandedGroups = new java.util.HashSet<>();
    private static final Map<IElement<?>, List<ExpandedGroup>> expandedMemberGroups = new IdentityHashMap<>();
    private static final Map<List<IElement<?>>, Integer> preferredIndexes = new IdentityHashMap<>();
    private static final AtomicLong REVISION = new AtomicLong();
    private static List<IElement<?>> cachedSource;
    private static long cachedRevision = -1L;
    private static List<IElement<?>> cachedElements = List.of();
    private static TagGroupConfig.GroupKey pendingGroupKey;
    private static IRecipeManager smithingRecipeManager;
    private static Set<Item> smithingTrimTemplates;

    private TagGroupManager() {
    }

    // 资源重载时替换配置并清空玩家当前的展开状态。
    public static synchronized void setDefinitions(List<TagGroupConfig.TagGroupDefinition> newDefinitions, AutoItemGroupConfig.Settings newAutoItemSettings) {
        definitions = List.copyOf(newDefinitions);
        smithingTrimTemplates = null;
        autoItemSettings = newAutoItemSettings;
        expandedGroups.clear();
        expandedMemberGroups.clear();
        preferredIndexes.clear();
        pendingGroupKey = null;
        cachedSource = null;
        cachedElements = List.of();
        cachedRevision = -1L;
        REVISION.incrementAndGet();
    }

    public static synchronized void setSmithingRecipeManager(IRecipeManager manager) {
        smithingRecipeManager = manager;
        smithingTrimTemplates = null;
        cachedSource = null;
        cachedElements = List.of();
        cachedRevision = -1L;
        REVISION.incrementAndGet();
    }

    private static void loadSmithingTrimTemplates() {
        if (smithingTrimTemplates != null || smithingRecipeManager == null) {
            return;
        }
        smithingTrimTemplates = smithingRecipeManager.createRecipeLookup(RecipeTypes.SMITHING).includeHidden().get()
            .map(holder -> holder.value())
            .filter(recipe -> recipe instanceof SmithingTrimRecipe)
            .flatMap(recipe -> recipe.templateIngredient().stream())
            .flatMap(ingredient -> ingredient.items())
            .map(holder -> holder.value())
            .collect(Collectors.toUnmodifiableSet());
        LOGGER.info("Loaded armor trim template items from JEI smithing recipes: count={}", smithingTrimTemplates.size());
    }

    public static synchronized void toggle(TagGroupConfig.GroupKey groupKey) {
        boolean expanded = expandedGroups.add(groupKey);
        if (!expanded) {
            expandedGroups.remove(groupKey);
        }
        pendingGroupKey = groupKey;
        preferredIndexes.clear();
        expandedMemberGroups.clear();
        cachedSource = null;
        cachedElements = List.of();
        cachedRevision = -1L;
        REVISION.incrementAndGet();
        LOGGER.debug("Item group toggled: group={}, expanded={}, revision={}", groupKey, expanded, REVISION.get());
    }

    public static long revision() {
        return REVISION.get();
    }

    public static synchronized List<IElement<?>> aggregate(List<IElement<?>> source, IIngredientManager ingredientManager) {
        // JEI 搜索结果可能被多处重复读取，因此按原列表身份和状态版本缓存聚合结果。
        long revision = REVISION.get();
        if (source == cachedSource && cachedRevision == revision) {
            return cachedElements;
        }
        expandedMemberGroups.clear();
        if (definitions.isEmpty() && !autoItemSettings.enabled()) {
            cachedSource = source;
            cachedRevision = revision;
            cachedElements = source;
            return source;
        }

        if (definitions.stream().anyMatch(definition -> definition.groupKey().type() == TagGroupConfig.TargetType.SMITHING_TRIM_TEMPLATE)) {
            loadSmithingTrimTemplates();
        }
        List<SourceItem> sourceItems = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            IElement<?> element = source.get(index);
            Optional<ItemStack> itemStack = element.getTypedIngredient().getIngredient(VanillaTypes.ITEM_STACK);
            if (itemStack.isPresent() && !itemStack.get().isEmpty()) {
                sourceItems.add(new SourceItem(index, element, itemStack.get()));
            }
        }

        List<MatchingGroup> matchingGroups = new ArrayList<>();
        for (TagGroupConfig.TagGroupDefinition definition : definitions) {
            List<SourceItem> members = sourceItems.stream()
                .filter(sourceItem -> matchesItem(definition, sourceItem.stack()))
                .toList();
            if (members.size() > 1) {
                matchingGroups.add(new MatchingGroup(definition, members, expandedGroups.contains(definition.groupKey())));
            }
        }
        addAutomaticItemGroups(sourceItems, matchingGroups);
        if (matchingGroups.isEmpty()) {
            pendingGroupKey = null;
            cachedSource = source;
            cachedRevision = revision;
            cachedElements = source;
            return source;
        }

        // 记录当前展开组对应的原始成员，供 JEI 网格绘制连续的组级边框。
        for (MatchingGroup group : matchingGroups) {
            if (group.expanded()) {
                ExpandedGroup expandedGroup = new ExpandedGroup(group.definition().groupKey(), group.definition().borderColor());
                for (SourceItem member : group.members()) {
                    expandedMemberGroups.computeIfAbsent(member.element(), ignored -> new ArrayList<>()).add(expandedGroup);
                }
            }
        }

        Map<Integer, List<MatchingGroup>> groupsByFirstIndex = new java.util.HashMap<>();
        Map<TagGroupConfig.GroupKey, IElement<?>> groupElements = new java.util.HashMap<>();
        Map<IElement<?>, List<MatchingGroup>> groupsByMember = new IdentityHashMap<>();
        for (MatchingGroup group : matchingGroups) {
            groupsByFirstIndex.computeIfAbsent(group.members().get(0).index(), ignored -> new ArrayList<>()).add(group);
            for (SourceItem member : group.members()) {
                groupsByMember.computeIfAbsent(member.element(), ignored -> new ArrayList<>()).add(group);
            }
        }

        // 按首个成员位置插入入口，展开成员仍使用 JEI 原始顺序。
        List<IElement<?>> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            for (MatchingGroup group : groupsByFirstIndex.getOrDefault(index, List.of())) {
                IElement<?> groupElement = createElement(group, ingredientManager);
                groupElements.put(group.definition().groupKey(), groupElement);
                result.add(groupElement);
            }
            IElement<?> element = source.get(index);
            for (MatchingGroup group : groupsByFirstIndex.getOrDefault(index, List.of())) {
                if (group.expanded()) {
                    result.addAll(group.members().stream().map(SourceItem::element).toList());
                }
            }
            if (!groupsByMember.containsKey(element)) {
                result.add(element);
            }
        }

        List<IElement<?>> resultElements = List.copyOf(result);
        if (pendingGroupKey != null) {
            IElement<?> groupElement = groupElements.get(pendingGroupKey);
            if (groupElement != null) {
                preferredIndexes.put(resultElements, result.indexOf(groupElement));
            }
            pendingGroupKey = null;
        }

        cachedSource = source;
        cachedRevision = revision;
        cachedElements = resultElements;
        return cachedElements;
    }

    // 返回本次展开或折叠后目标物品组入口在新列表中的位置。
    public static synchronized Integer preferredItemIndex(List<IElement<?>> elements) {
        return preferredIndexes.get(elements);
    }

    public static synchronized void drawExpandedGroupBorders(GuiGraphicsExtractor graphics, Stream<IngredientListSlot> slots) {
        if (expandedMemberGroups.isEmpty()) {
            return;
        }

        Map<ExpandedGroup, List<IngredientListSlot>> slotsByGroup = new java.util.LinkedHashMap<>();
        slots.forEach(slot -> {
            slot.getOptionalElement().ifPresent(element -> {
                List<ExpandedGroup> groups = expandedMemberGroups.get(element);
                if (groups != null) {
                    for (ExpandedGroup group : groups) {
                        slotsByGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(slot);
                    }
                }
            });
        });

        // 只绘制展开成员集合的外轮廓，相邻成员之间不重复绘制内部线条。
        for (Map.Entry<ExpandedGroup, List<IngredientListSlot>> entry : slotsByGroup.entrySet()) {
            Set<SlotPosition> positions = new java.util.HashSet<>();
            for (IngredientListSlot slot : entry.getValue()) {
                ImmutableRect2i slotArea = slot.getArea();
                positions.add(new SlotPosition(slotArea.getX(), slotArea.getY()));
            }
            for (IngredientListSlot slot : entry.getValue()) {
                ImmutableRect2i slotArea = slot.getArea();
                int x = slotArea.getX();
                int y = slotArea.getY();
                int width = slotArea.getWidth();
                int height = slotArea.getHeight();
                int stepX = slotArea.getWidth();
                int stepY = slotArea.getHeight();
                int color = entry.getKey().borderColor();
                if (!positions.contains(new SlotPosition(slotArea.getX() - stepX, slotArea.getY()))) {
                    graphics.fill(x, y, x + 1, y + height, color);
                }
                if (!positions.contains(new SlotPosition(slotArea.getX() + stepX, slotArea.getY()))) {
                    graphics.fill(x + width - 1, y, x + width, y + height, color);
                }
                if (!positions.contains(new SlotPosition(slotArea.getX(), slotArea.getY() - stepY))) {
                    graphics.fill(x, y, x + width, y + 1, color);
                }
                if (!positions.contains(new SlotPosition(slotArea.getX(), slotArea.getY() + stepY))) {
                    graphics.fill(x, y + height - 1, x + width, y + height, color);
                }
            }
        }
    }

    private static void addAutomaticItemGroups(List<SourceItem> sourceItems, List<MatchingGroup> matchingGroups) {
        if (!autoItemSettings.enabled()) {
            return;
        }
        Set<IElement<?>> manualMembers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (MatchingGroup group : matchingGroups) {
            for (SourceItem member : group.members()) {
                manualMembers.add(member.element());
            }
        }
        List<SourceItem> available = sourceItems.stream().filter(member -> !manualMembers.contains(member.element())).toList();
        Map<String, List<SourceItem>> automaticGroups = autoItemSettings.collect(available,
            member -> BuiltInRegistries.ITEM.getKey(member.stack().getItem()).toString());
        for (Map.Entry<String, List<SourceItem>> entry : automaticGroups.entrySet()) {
            SourceItem first = entry.getValue().get(0);
            TagGroupConfig.GroupKey key = new TagGroupConfig.GroupKey(TagGroupConfig.TargetType.ITEM, entry.getKey());
            TagGroupConfig.TagGroupDefinition definition = new TagGroupConfig.TagGroupDefinition(key, null,
                List.of(first.stack().getItem()), null, null, BuiltInRegistries.ITEM.getKey(first.stack().getItem()),
                0xFF55AAFF, "jei_tag_groups.group.auto_item.name", List.of());
            matchingGroups.add(new MatchingGroup(definition, entry.getValue(), expandedGroups.contains(key)));
        }
        LOGGER.debug("Automatic item ID grouping: visibleEntries={}, unassignedEntries={}, groups={}", sourceItems.size(), available.size(), automaticGroups.size());
    }

    private static TagGroupElement createElement(MatchingGroup group, IIngredientManager ingredientManager) {
        Item iconItem = BuiltInRegistries.ITEM.getValue(group.definition().iconId());
        ItemStack iconStack = iconItem == null
            ? ItemStack.EMPTY
            : iconItem.getDefaultInstance();
        if (iconStack.isEmpty() || iconStack.is(Items.AIR)) {
            iconStack = group.members().get(0).stack().copy();
            LOGGER.warn("Configured icon {} is invalid for group {}; using the first visible member", group.definition().iconId(), group.definition().groupKey());
        }

        ITypedIngredient<ItemStack> typedIcon = ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, iconStack).orElseThrow();
        List<ITypedIngredient<?>> memberIngredients = group.members().stream()
            .<ITypedIngredient<?>>map(member -> member.element().getTypedIngredient())
            .toList();
        return new TagGroupElement(typedIcon, group.definition(), memberIngredients, ingredientManager);
    }

    // 按标签、物品 ID 或当前语言下的物品显示名称匹配成员。
    private static boolean matchesItem(TagGroupConfig.TagGroupDefinition definition, ItemStack stack) {
        if (definition.groupKey().type() == TagGroupConfig.TargetType.SMITHING_TRIM_TEMPLATE) {
            return smithingTrimTemplates != null && smithingTrimTemplates.contains(stack.getItem());
        }
        if (definition.tagKey() != null) {
            return stack.is(definition.tagKey());
        }
        if (definition.itemNameContains() != null) {
            return stack.getHoverName().getString().contains(definition.itemNameContains());
        }
        if (definition.itemClass() != null) {
            return definition.itemClass().isInstance(stack.getItem());
        }
        return definition.items().contains(stack.getItem());
    }

    private record SourceItem(int index, IElement<?> element, ItemStack stack) {
    }

    private record MatchingGroup(TagGroupConfig.TagGroupDefinition definition, List<SourceItem> members, boolean expanded) {
    }

    private record ExpandedGroup(TagGroupConfig.GroupKey groupKey, int borderColor) {
    }

    private record SlotPosition(int x, int y) {
    }
}
