package com.jei_tag_groups.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TagGroupConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_PATH = Path.of("config", "jei_tag_groups", "tag_groups.json");
    private static final String DEFAULT_CONFIG_RESOURCE = "/config/jei_tag_groups/tag_groups.json";

    private TagGroupConfig() {
    }

    // 读取客户端目录中的 JSON 配置，并让单个坏组不影响其他组继续加载。
    public static Configuration load() {
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve(CONFIG_PATH);
        if (Files.notExists(path)) {
            // 第一次启动时复制内置默认配置，之后始终以用户配置文件为准。
            try (InputStream input = TagGroupConfig.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
                if (input == null) {
                    LOGGER.warn("Default tag group configuration resource is missing");
                    return new Configuration(List.of(), List.of());
                }
                Files.createDirectories(path.getParent());
                Files.copy(input, path);
                LOGGER.info("Created default tag group configuration: {}", path);
            } catch (IOException exception) {
                LOGGER.warn("Failed to create default tag group configuration: {}", path, exception);
                return new Configuration(List.of(), List.of());
            }
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new JsonParseException("The root element must be an object");
            }

            JsonObject rootObject = root.getAsJsonObject();
            JsonElement groupsElement = rootObject.get("groups");
            if (groupsElement == null || !groupsElement.isJsonArray()) {
                throw new JsonParseException("The groups property must be an array");
            }

            List<TagGroupDefinition> definitions = new ArrayList<>();
            Set<GroupKey> groupKeys = new LinkedHashSet<>();
            JsonArray groups = groupsElement.getAsJsonArray();
            for (JsonElement groupElement : groups) {
                try {
                    TagGroupDefinition definition = parseGroup(groupElement);
                    if (groupKeys.add(definition.groupKey())) {
                        definitions.add(definition);
                    } else {
                        LOGGER.warn("Skipping duplicate group: {}", definition.groupKey());
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn("Skipping invalid group: {}", exception.getMessage());
                }
            }
            List<RecipeGroupDefinition> recipeDefinitions = new ArrayList<>();
            Set<RecipeGroupDefinition> recipeDefinitionSet = new LinkedHashSet<>();
            JsonElement recipeGroupsElement = rootObject.get("recipe_groups");
            if (recipeGroupsElement != null) {
                if (!recipeGroupsElement.isJsonArray()) {
                    throw new JsonParseException("The recipe_groups property must be an array");
                }
                for (JsonElement recipeGroupElement : recipeGroupsElement.getAsJsonArray()) {
                    try {
                        RecipeGroupDefinition definition = parseRecipeGroup(recipeGroupElement);
                        if (recipeDefinitionSet.add(definition)) {
                            recipeDefinitions.add(definition);
                        } else {
                            LOGGER.warn("Skipping duplicate recipe group: {}", definition);
                        }
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Skipping invalid recipe group: {}", exception.getMessage());
                    }
                }
            }
            return new Configuration(List.copyOf(definitions), List.copyOf(recipeDefinitions));
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("Failed to load tag group configuration: {}", path, exception);
            return new Configuration(List.of(), List.of());
        }
    }

    private static TagGroupDefinition parseGroup(JsonElement groupElement) {
        // 先严格校验配置字段，再创建当前物品注册表使用的目标对象。
        if (!groupElement.isJsonObject()) {
            throw new JsonParseException("A tag group must be an object");
        }

        JsonObject group = groupElement.getAsJsonObject();
        JsonElement tagElement = group.get("tag");
        JsonElement itemElement = group.get("item");
        if ((tagElement == null) == (itemElement == null)) {
            throw new JsonParseException("A group must define exactly one of tag or item");
        }

        boolean tagTarget = tagElement != null;
        String targetValue = getRequiredString(group, tagTarget ? "tag" : "item");
        String iconValue = getRequiredString(group, "icon");
        String borderValue = getRequiredString(group, "border_color");
        if (!isCompleteResourceLocation(targetValue) || !ResourceLocation.isValidResourceLocation(targetValue)) {
            throw new JsonParseException("Invalid target resource location: " + targetValue);
        }
        if (!isCompleteResourceLocation(iconValue) || !ResourceLocation.isValidResourceLocation(iconValue)) {
            throw new JsonParseException("Invalid icon resource location: " + iconValue);
        }
        if (!borderValue.matches("#[0-9a-fA-F]{6}")) {
            throw new JsonParseException("Invalid border color: " + borderValue);
        }

        ResourceLocation targetId = ResourceLocation.parse(targetValue);
        ResourceLocation iconId = ResourceLocation.parse(iconValue);
        int borderColor = 0xFF000000 | Integer.parseInt(borderValue.substring(1), 16);
        if (tagTarget) {
            return new TagGroupDefinition(new GroupKey(TargetType.TAG, targetId), TagKey.create(Registries.ITEM, targetId), null, iconId, borderColor);
        }

        Item item = ForgeRegistries.ITEMS.getValue(targetId);
        if (item == null || item == Items.AIR) {
            throw new JsonParseException("Unknown item resource location: " + targetValue);
        }
        return new TagGroupDefinition(new GroupKey(TargetType.ITEM, targetId), null, item, iconId, borderColor);
    }

    // 解析一个按配方 ID、产出物品或输入物品匹配的配方折叠组。
    private static RecipeGroupDefinition parseRecipeGroup(JsonElement groupElement) {
        if (!groupElement.isJsonObject()) {
            throw new JsonParseException("A recipe group must be an object");
        }

        JsonObject group = groupElement.getAsJsonObject();
        String recipeValue = getOptionalString(group, "recipe_id");
        String recipeContainsValue = getOptionalString(group, "recipe_id_contains");
        String outputValue = getOptionalString(group, "output_item");
        String inputValue = getOptionalString(group, "input_item");
        if (recipeValue == null && recipeContainsValue == null && outputValue == null && inputValue == null) {
            throw new JsonParseException("A recipe group must define at least one matching property");
        }
        if (recipeContainsValue != null && recipeContainsValue.isBlank()) {
            throw new JsonParseException("The recipe_id_contains property must not be blank");
        }

        ResourceLocation recipeId = parseOptionalResourceLocation(recipeValue, "recipe_id");
        ResourceLocation outputItemId = parseOptionalItemResourceLocation(outputValue, "output_item");
        ResourceLocation inputItemId = parseOptionalItemResourceLocation(inputValue, "input_item");
        String borderValue = getRequiredString(group, "border_color");
        if (!borderValue.matches("#[0-9a-fA-F]{6}")) {
            throw new JsonParseException("Invalid recipe group border color: " + borderValue);
        }

        int borderColor = 0xFF000000 | Integer.parseInt(borderValue.substring(1), 16);
        return new RecipeGroupDefinition(recipeId, recipeContainsValue, outputItemId, inputItemId, borderColor);
    }

    private static String getRequiredString(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Missing string property: " + property);
        }
        return value.getAsString();
    }

    // 读取可选字符串字段，让 recipe_id、output_item 和 input_item 可以自由组合。
    private static String getOptionalString(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Property must be a string: " + property);
        }
        return value.getAsString();
    }

    // 解析需要完整命名空间的可选资源 ID。
    private static ResourceLocation parseOptionalResourceLocation(String value, String property) {
        if (value == null) {
            return null;
        }
        if (!isCompleteResourceLocation(value) || !ResourceLocation.isValidResourceLocation(value)) {
            throw new JsonParseException("Invalid " + property + " resource location: " + value);
        }
        return ResourceLocation.parse(value);
    }

    // 解析并确认可选字段对应的物品已经注册。
    private static ResourceLocation parseOptionalItemResourceLocation(String value, String property) {
        ResourceLocation itemId = parseOptionalResourceLocation(value, property);
        if (itemId == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == Items.AIR) {
            throw new JsonParseException("Unknown item resource location: " + value);
        }
        return itemId;
    }

    private static boolean isCompleteResourceLocation(String value) {
        // 配置要求显式写出命名空间，避免不同环境对默认 minecraft 命名空间产生歧义。
        int separator = value.indexOf(':');
        return separator > 0 && separator < value.length() - 1 && separator == value.lastIndexOf(':');
    }

    public enum TargetType {
        TAG,
        ITEM
    }

    public record GroupKey(TargetType type, ResourceLocation id) {
    }

    public record TagGroupDefinition(GroupKey groupKey, TagKey<Item> tagKey, Item item, ResourceLocation iconId, int borderColor) {
    }

    // 保存物品组和配方组两类配置，资源重载时一次性替换。
    public record Configuration(List<TagGroupDefinition> tagGroups, List<RecipeGroupDefinition> recipeGroups) {
    }

    // 保存配方折叠组的精确 ID、ID 包含文本、物品条件和边框颜色。
    public record RecipeGroupDefinition(ResourceLocation recipeId, String recipeIdContains, ResourceLocation outputItemId, ResourceLocation inputItemId, int borderColor) {
    }
}
