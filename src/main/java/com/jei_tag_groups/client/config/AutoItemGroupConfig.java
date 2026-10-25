package com.jei_tag_groups.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class AutoItemGroupConfig {
    public static final String FILE_NAME = "auto_item_groups.json";
    private static final Logger LOGGER = LogUtils.getLogger();

    private AutoItemGroupConfig() {
    }

    public static Settings load() {
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("config/jei_tag_groups").resolve(FILE_NAME);
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                try (InputStream input = AutoItemGroupConfig.class.getResourceAsStream("/config/jei_tag_groups/" + FILE_NAME)) {
                    if (input == null) {
                        throw new IOException("Missing bundled automatic item grouping configuration");
                    }
                    Files.copy(input, path);
                }
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Settings settings = parse(JsonParser.parseReader(reader));
                LOGGER.debug("Loaded automatic item grouping: {}", settings);
                return settings;
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to load automatic item grouping; automatic groups are disabled: {}", path, exception);
            return Settings.disabled();
        }
    }

    public static Settings parse(JsonElement root) {
        if (!root.isJsonObject()) {
            throw new JsonParseException("Automatic item grouping configuration must be an object");
        }
        JsonObject object = root.getAsJsonObject();
        boolean enabled = true;
        JsonElement enabledValue = object.get("enabled");
        if (enabledValue != null) {
            if (!enabledValue.isJsonPrimitive() || !enabledValue.getAsJsonPrimitive().isBoolean()) {
                throw new JsonParseException("enabled must be a boolean");
            }
            enabled = enabledValue.getAsBoolean();
        }

        int threshold = 10;
        JsonElement thresholdValue = object.get("threshold");
        if (thresholdValue != null) {
            try {
                if (!thresholdValue.isJsonPrimitive() || !thresholdValue.getAsJsonPrimitive().isNumber()) {
                    throw new NumberFormatException("Not a number");
                }
                threshold = thresholdValue.getAsBigDecimal().intValueExact();
                if (threshold < 1) {
                    throw new NumberFormatException("Less than 1");
                }
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new JsonParseException("threshold must be an integer of at least 1", exception);
            }
        }

        Set<String> blacklist = new LinkedHashSet<>();
        JsonElement blacklistValue = object.get("blacklist");
        if (blacklistValue != null) {
            if (!blacklistValue.isJsonArray()) {
                throw new JsonParseException("blacklist must be an array of item IDs");
            }
            for (JsonElement entry : blacklistValue.getAsJsonArray()) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()
                    || !entry.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                    throw new JsonParseException("Invalid blacklist item ID: " + entry);
                }
                blacklist.add(entry.getAsString());
            }
        }
        return new Settings(enabled, threshold, blacklist);
    }

    public record Settings(boolean enabled, int threshold, Set<String> blacklist) {
        public Settings {
            if (threshold < 1) {
                throw new IllegalArgumentException("Automatic item grouping threshold must be at least 1");
            }
            blacklist = Set.copyOf(blacklist);
        }

        public static Settings disabled() {
            return new Settings(false, 10, Set.of());
        }

        public <T> Map<String, List<T>> collect(List<T> source, Function<T, String> itemId) {
            if (!enabled) {
                return Map.of();
            }
            Map<String, List<T>> groups = new LinkedHashMap<>();
            for (T member : source) {
                String id = itemId.apply(member);
                if (!blacklist.contains(id)) {
                    groups.computeIfAbsent(id, ignored -> new java.util.ArrayList<>()).add(member);
                }
            }
            groups.entrySet().removeIf(entry -> entry.getValue().size() <= threshold);
            groups.replaceAll((id, members) -> List.copyOf(members));
            return Collections.unmodifiableMap(groups);
        }
    }
}
