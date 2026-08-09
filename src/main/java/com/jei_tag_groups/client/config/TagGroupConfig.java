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
    private static final Path CONFIG_DIRECTORY = Path.of("config", "jei_tag_groups");
    private static final String DEFAULT_CONFIG_RESOURCE_PREFIX = "/config/jei_tag_groups/";
    private static final String DEFAULT_CONFIG_INITIALIZED_MARKER = ".defaults_initialized";
    private static final List<String> DEFAULT_CONFIG_FILES = List.of(
        "item_enchanted_book.json",
        "item_potion.json",
        "item_splash_potion.json",
        "item_lingering_potion.json",
        "recipe_repair.json",
        "recipe_enchanted_book.json",
        "recipe_smithing_trim.json"
    );

    private TagGroupConfig() {
    }

    // 读取配置目录中的所有 JSON 文件，并让单个坏文件或坏规则不影响其他规则继续加载。
    public static Configuration load() {
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve(CONFIG_DIRECTORY);
        initializeDefaultConfigs(directory);

        List<TagGroupDefinition> definitions = new ArrayList<>();
        Set<GroupKey> groupKeys = new LinkedHashSet<>();
        List<RecipeGroupDefinition> recipeDefinitions = new ArrayList<>();
        Set<RecipeGroupDefinition> recipeDefinitionSet = new LinkedHashSet<>();
        for (Path path : listConfigFiles(directory)) {
            try {
                Configuration fileConfiguration = loadFile(path);
                for (TagGroupDefinition definition : fileConfiguration.tagGroups()) {
                    if (groupKeys.add(definition.groupKey())) {
                        definitions.add(definition);
                    } else {
                        LOGGER.warn("Skipping duplicate group: {}", definition.groupKey());
                    }
                }
                for (RecipeGroupDefinition definition : fileConfiguration.recipeGroups()) {
                    if (recipeDefinitionSet.add(definition)) {
                        recipeDefinitions.add(definition);
                    } else {
                        LOGGER.warn("Skipping duplicate recipe group: {}", definition);
                    }
                }
            } catch (IOException | JsonParseException exception) {
                LOGGER.warn("Failed to load tag group configuration file: {}", path, exception);
            }
        }
        return new Configuration(List.copyOf(definitions), List.copyOf(recipeDefinitions));
    }

    // 首次创建配置目录时复制默认规则，升级旧配置时先拆分旧文件；之后删除某个规则文件不会被自动恢复。
    private static void initializeDefaultConfigs(Path directory) {
        Path marker = directory.resolve(DEFAULT_CONFIG_INITIALIZED_MARKER);
        if (Files.exists(marker)) {
            return;
        }

        boolean success = true;
        try {
            Files.createDirectories(directory);
            Path legacyPath = directory.resolve("tag_groups.json");
            if (Files.exists(legacyPath)) {
                success = migrateLegacyConfig(legacyPath, directory);
            } else {
                for (String fileName : DEFAULT_CONFIG_FILES) {
                    Path target = directory.resolve(fileName);
                    if (Files.exists(target)) {
                        continue;
                    }
                    try (InputStream input = TagGroupConfig.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE_PREFIX + fileName)) {
                        if (input == null) {
                            success = false;
                            LOGGER.warn("Default tag group configuration resource is missing: {}", fileName);
                            continue;
                        }
                        Files.copy(input, target);
                    } catch (IOException exception) {
                        success = false;
                        LOGGER.warn("Failed to copy default tag group configuration: {}", target, exception);
                    }
                }
            }
            if (success) {
                Files.writeString(marker, "initialized\n", StandardCharsets.UTF_8);
                LOGGER.info("Created default tag group configuration files in: {}", directory);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to initialize default tag group configuration directory: {}", directory, exception);
        }
    }

    // 将旧版单文件配置拆分为每条规则一个 JSON 文件，并保留可恢复的备份文件。
    private static boolean migrateLegacyConfig(Path legacyPath, Path directory) {
        try (Reader reader = Files.newBufferedReader(legacyPath, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new JsonParseException("The legacy configuration root must be an object");
            }
            JsonObject rootObject = root.getAsJsonObject();
            int migratedCount = migrateLegacyRules(rootObject, "groups", "item", "legacy_item_", directory);
            migratedCount += migrateLegacyRules(rootObject, "recipe_groups", "recipe", "legacy_recipe_", directory);
            if (migratedCount == 0) {
                return false;
            }

            Path backupPath = directory.resolve("tag_groups.json.legacy");
            int backupIndex = 1;
            while (Files.exists(backupPath)) {
                backupPath = directory.resolve("tag_groups.json.legacy." + backupIndex++);
            }
            Files.move(legacyPath, backupPath);
            LOGGER.info("Migrated legacy tag group configuration: source={}, backup={}, ruleCount={}", legacyPath, backupPath, migratedCount);
            return true;
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("Failed to migrate legacy tag group configuration: {}", legacyPath, exception);
            return false;
        }
    }

    // 写出旧配置中的单条物品规则或配方规则，并保留原有匹配字段。
    private static int migrateLegacyRules(JsonObject rootObject, String property, String type, String filePrefix, Path directory) throws IOException {
        JsonElement rulesElement = rootObject.get(property);
        if (rulesElement == null) {
            return 0;
        }
        if (!rulesElement.isJsonArray()) {
            throw new JsonParseException("The legacy " + property + " property must be an array");
        }

        int migratedCount = 0;
        for (JsonElement ruleElement : rulesElement.getAsJsonArray()) {
            if (!ruleElement.isJsonObject()) {
                LOGGER.warn("Skipping invalid legacy rule in property: {}", property);
                continue;
            }
            JsonObject fileObject = new JsonObject();
            fileObject.addProperty("description", "从旧版配置迁移的" + ("item".equals(type) ? "物品" : "配方") + "规则");
            fileObject.addProperty("type", type);
            for (var entry : ruleElement.getAsJsonObject().entrySet()) {
                fileObject.add(entry.getKey(), entry.getValue());
            }

            Path target;
            int fileIndex = 1;
            do {
                target = directory.resolve(filePrefix + String.format("%03d", fileIndex++) + ".json");
            } while (Files.exists(target));
            Files.writeString(target, fileObject.toString(), StandardCharsets.UTF_8);
            migratedCount++;
        }
        return migratedCount;
    }

    // 按文件名排序读取配置，保证默认规则和玩家规则的加载顺序稳定。
    private static List<Path> listConfigFiles(Path directory) {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            return paths
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                .sorted()
                .toList();
        } catch (IOException exception) {
            LOGGER.warn("Failed to list tag group configuration directory: {}", directory, exception);
            return List.of();
        }
    }

    // 解析单个规则文件，同时兼容旧版包含 groups 和 recipe_groups 数组的配置文件。
    private static Configuration loadFile(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new JsonParseException("The root element must be an object");
            }

            JsonObject rootObject = root.getAsJsonObject();
            String description = getOptionalString(rootObject, "description");
            List<TagGroupDefinition> definitions = new ArrayList<>();
            List<RecipeGroupDefinition> recipeDefinitions = new ArrayList<>();
            boolean hasDefinition = false;

            JsonElement groupsElement = rootObject.get("groups");
            if (groupsElement != null) {
                if (!groupsElement.isJsonArray()) {
                    throw new JsonParseException("The groups property must be an array");
                }
                hasDefinition = true;
                Set<GroupKey> groupKeys = new LinkedHashSet<>();
                for (JsonElement groupElement : groupsElement.getAsJsonArray()) {
                    try {
                        TagGroupDefinition definition = parseGroup(groupElement);
                        if (groupKeys.add(definition.groupKey())) {
                            definitions.add(definition);
                        } else {
                            LOGGER.warn("Skipping duplicate group in {}: {}", path, definition.groupKey());
                        }
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Skipping invalid group in {}: {}", path, exception.getMessage());
                    }
                }
            }

            JsonElement recipeGroupsElement = rootObject.get("recipe_groups");
            if (recipeGroupsElement != null) {
                if (!recipeGroupsElement.isJsonArray()) {
                    throw new JsonParseException("The recipe_groups property must be an array");
                }
                hasDefinition = true;
                Set<RecipeGroupDefinition> recipeDefinitionSet = new LinkedHashSet<>();
                for (JsonElement recipeGroupElement : recipeGroupsElement.getAsJsonArray()) {
                    try {
                        RecipeGroupDefinition definition = parseRecipeGroup(recipeGroupElement);
                        if (recipeDefinitionSet.add(definition)) {
                            recipeDefinitions.add(definition);
                        } else {
                            LOGGER.warn("Skipping duplicate recipe group in {}: {}", path, definition);
                        }
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Skipping invalid recipe group in {}: {}", path, exception.getMessage());
                    }
                }
            }

            String type = getOptionalString(rootObject, "type");
            if (type != null) {
                hasDefinition = true;
                if ("item".equals(type)) {
                    definitions.add(parseGroup(rootObject));
                } else if ("recipe".equals(type)) {
                    recipeDefinitions.add(parseRecipeGroup(rootObject));
                } else {
                    throw new JsonParseException("Unknown configuration type: " + type);
                }
            }
            if (!hasDefinition) {
                throw new JsonParseException("A configuration file must define a group or recipe group");
            }
            LOGGER.debug("Loaded tag group configuration file: file={}, description={}", path, description == null ? "none" : description);
            return new Configuration(List.copyOf(definitions), List.copyOf(recipeDefinitions));
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
        JsonElement itemsElement = group.get("items");
        JsonElement itemNameElement = group.get("item_name_contains");
        int matcherCount = (tagElement == null ? 0 : 1)
            + (itemElement == null ? 0 : 1)
            + (itemsElement == null ? 0 : 1)
            + (itemNameElement == null ? 0 : 1);
        if (matcherCount != 1 || (itemElement != null && itemsElement != null)) {
            throw new JsonParseException("A group must define exactly one of tag, item, items or item_name_contains");
        }

        String displayNameKey = getOptionalString(group, "display_name");
        List<String> tooltipKeys = getOptionalStringList(group, "tooltip");
        String iconValue = getRequiredString(group, "icon");
        String borderValue = getRequiredString(group, "border_color");
        if (!isCompleteResourceLocation(iconValue) || !ResourceLocation.isValidResourceLocation(iconValue)) {
            throw new JsonParseException("Invalid icon resource location: " + iconValue);
        }
        if (!borderValue.matches("#[0-9a-fA-F]{6}")) {
            throw new JsonParseException("Invalid border color: " + borderValue);
        }

        ResourceLocation iconId = ResourceLocation.parse(iconValue);
        int borderColor = 0xFF000000 | Integer.parseInt(borderValue.substring(1), 16);
        if (tagElement != null) {
            String targetValue = getRequiredString(group, "tag");
            ResourceLocation targetId = parseRequiredResourceLocation(targetValue, "tag");
            return new TagGroupDefinition(new GroupKey(TargetType.TAG, targetId.toString()), TagKey.create(Registries.ITEM, targetId), List.of(), null, iconId, borderColor, displayNameKey, tooltipKeys);
        }

        if (itemNameElement != null) {
            String itemNameContains = getRequiredString(group, "item_name_contains");
            if (itemNameContains.isBlank()) {
                throw new JsonParseException("The item_name_contains property must not be blank");
            }
            return new TagGroupDefinition(new GroupKey(TargetType.ITEM_NAME, itemNameContains), null, List.of(), itemNameContains, iconId, borderColor, displayNameKey, tooltipKeys);
        }

        JsonElement itemValuesElement = itemsElement == null ? itemElement : itemsElement;
        List<ResourceLocation> itemIds = parseItemResourceLocations(itemValuesElement, itemsElement == null ? "item" : "items");
        List<Item> items = itemIds.stream().map(ForgeRegistries.ITEMS::getValue).toList();
        String groupValue = String.join(",", itemIds.stream().map(ResourceLocation::toString).sorted().toList());
        return new TagGroupDefinition(new GroupKey(TargetType.ITEM, groupValue), null, items, null, iconId, borderColor, displayNameKey, tooltipKeys);
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

    // 读取支持单个字符串或字符串数组的可选语言键字段。
    private static List<String> getOptionalStringList(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null) {
            return List.of();
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return List.of(value.getAsString());
        }
        if (!value.isJsonArray()) {
            throw new JsonParseException("Property must be a string or string array: " + property);
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString() || element.getAsString().isBlank()) {
                throw new JsonParseException("Property must contain only non-blank strings: " + property);
            }
            values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    // 解析并确认物品 ID 字符串来自当前 Forge 物品注册表。
    private static List<ResourceLocation> parseItemResourceLocations(JsonElement value, String property) {
        List<String> values = new ArrayList<>();
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            values.add(value.getAsString());
        } else if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new JsonParseException("Property must contain only strings: " + property);
                }
                values.add(element.getAsString());
            }
        } else {
            throw new JsonParseException("Property must be a string or string array: " + property);
        }
        if (values.isEmpty()) {
            throw new JsonParseException("Property must contain at least one item: " + property);
        }

        List<ResourceLocation> itemIds = new ArrayList<>();
        for (String valueString : values) {
            ResourceLocation itemId = parseRequiredResourceLocation(valueString, property);
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item == Items.AIR) {
                throw new JsonParseException("Unknown item resource location: " + valueString);
            }
            itemIds.add(itemId);
        }
        return List.copyOf(itemIds);
    }

    // 解析必须显式包含命名空间的资源 ID。
    private static ResourceLocation parseRequiredResourceLocation(String value, String property) {
        if (!isCompleteResourceLocation(value) || !ResourceLocation.isValidResourceLocation(value)) {
            throw new JsonParseException("Invalid " + property + " resource location: " + value);
        }
        return ResourceLocation.parse(value);
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
        ITEM,
        ITEM_NAME
    }

    public record GroupKey(TargetType type, String value) {
    }

    public record TagGroupDefinition(GroupKey groupKey, TagKey<Item> tagKey, List<Item> items, String itemNameContains, ResourceLocation iconId, int borderColor, String displayNameKey, List<String> tooltipKeys) {
        public TagGroupDefinition {
            items = List.copyOf(items);
            tooltipKeys = List.copyOf(tooltipKeys);
        }
    }

    // 保存物品组和配方组两类配置，资源重载时一次性替换。
    public record Configuration(List<TagGroupDefinition> tagGroups, List<RecipeGroupDefinition> recipeGroups) {
    }

    // 保存配方折叠组的精确 ID、ID 包含文本、物品条件和边框颜色。
    public record RecipeGroupDefinition(ResourceLocation recipeId, String recipeIdContains, ResourceLocation outputItemId, ResourceLocation inputItemId, int borderColor) {
    }
}
