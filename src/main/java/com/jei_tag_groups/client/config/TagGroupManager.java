package com.jei_tag_groups.client.config;

import com.jei_tag_groups.client.element.TagGroupElement;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

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
    private static final Set<TagGroupConfig.GroupKey> expandedGroups = new java.util.HashSet<>();
    private static final Map<IElement<?>, List<ExpandedGroup>> expandedMemberGroups = new IdentityHashMap<>();
    private static final AtomicLong REVISION = new AtomicLong();
    private static List<IElement<?>> cachedSource;
    private static long cachedRevision = -1L;
    private static List<IElement<?>> cachedElements = List.of();

    private TagGroupManager() {
    }

    // 资源重载时替换配置并清空玩家当前的展开状态。
    public static synchronized void setDefinitions(List<TagGroupConfig.TagGroupDefinition> newDefinitions) {
        definitions = List.copyOf(newDefinitions);
        expandedGroups.clear();
        expandedMemberGroups.clear();
        cachedSource = null;
        cachedElements = List.of();
        cachedRevision = -1L;
        REVISION.incrementAndGet();
    }

    public static synchronized void toggle(TagGroupConfig.GroupKey groupKey) {
        if (!expandedGroups.add(groupKey)) {
            expandedGroups.remove(groupKey);
        }
        expandedMemberGroups.clear();
        cachedSource = null;
        cachedElements = List.of();
        cachedRevision = -1L;
        REVISION.incrementAndGet();
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
        if (definitions.isEmpty()) {
            cachedSource = source;
            cachedRevision = revision;
            cachedElements = source;
            return source;
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
            List<SourceItem> members = sourceItems.stream().filter(sourceItem -> definition.tagKey() != null
                ? sourceItem.stack().is(definition.tagKey())
                : sourceItem.stack().is(definition.item())).toList();
            if (!members.isEmpty()) {
                matchingGroups.add(new MatchingGroup(definition, members, expandedGroups.contains(definition.groupKey())));
            }
        }
        if (matchingGroups.isEmpty()) {
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
                result.add(createElement(group, ingredientManager));
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

        cachedSource = source;
        cachedRevision = revision;
        cachedElements = List.copyOf(result);
        return cachedElements;
    }

    public static synchronized void drawExpandedGroupBorders(GuiGraphics graphics, Stream<IngredientListSlot> slots) {
        if (expandedMemberGroups.isEmpty()) {
            return;
        }

        Map<ExpandedGroup, List<IngredientListSlot>> slotsByGroup = new java.util.LinkedHashMap<>();
        slots.forEach(slot -> {
            IElement<?> element = slot.getElement();
            List<ExpandedGroup> groups = expandedMemberGroups.get(element);
            if (groups != null) {
                for (ExpandedGroup group : groups) {
                    slotsByGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(slot);
                }
            }
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

    private static TagGroupElement createElement(MatchingGroup group, IIngredientManager ingredientManager) {
        Item iconItem = ForgeRegistries.ITEMS.getValue(group.definition().iconId());
        ItemStack iconStack = iconItem == null
            ? ItemStack.EMPTY
            : iconItem.getDefaultInstance();
        if (iconStack.isEmpty() || iconStack.is(Items.AIR)) {
            iconStack = group.members().get(0).stack().copy();
            LOGGER.warn("Configured icon {} is invalid for group {}; using the first visible member", group.definition().iconId(), group.definition().groupKey());
        }

        ITypedIngredient<ItemStack> typedIcon = ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, iconStack).orElseThrow();
        List<ItemStack> memberStacks = group.members().stream().map(member -> member.stack().copy()).toList();
        return new TagGroupElement(typedIcon, group.definition().groupKey(), group.definition().borderColor(), memberStacks);
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
