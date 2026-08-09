package com.jei_tag_groups.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class RecipeGroupKeyMappings {
    // 使用左 Shift 作为默认按键，并限制为 GUI 内生效，避免在世界中影响其他操作。
    public static final KeyMapping TOGGLE_RECIPE_GROUPS = new KeyMapping(
        "key.jei_tag_groups.toggle_recipe_groups",
        InputConstants.Type.KEYSYM,
        340,
        "key.categories.jei_tag_groups"
    );

    private RecipeGroupKeyMappings() {
    }
}
