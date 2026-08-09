package com.jei_tag_groups.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class RecipeGroupKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("jei_tag_groups", "keybinds"));

    // 使用左 Shift 作为默认按键，并限制为 GUI 内生效，避免在世界中影响其他操作。
    public static final KeyMapping TOGGLE_RECIPE_GROUPS = new KeyMapping(
        "key.jei_tag_groups.toggle_recipe_groups",
        KeyConflictContext.GUI,
        InputConstants.Type.KEYSYM,
        340,
        CATEGORY
    );

    private RecipeGroupKeyMappings() {
    }
}
