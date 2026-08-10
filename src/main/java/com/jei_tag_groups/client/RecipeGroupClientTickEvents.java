package com.jei_tag_groups.client;

import com.jei_tag_groups.Jei_tag_groups;
import com.jei_tag_groups.client.config.RecipeGroupManager;
import com.jei_tag_groups.client.input.RecipeGroupKeyMappings;
import mezz.jei.gui.recipes.RecipesGui;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Jei_tag_groups.MODID, value = Dist.CLIENT)
public final class RecipeGroupClientTickEvents {
    private RecipeGroupClientTickEvents() {
    }

    @SubscribeEvent
    // 在客户端 tick 中驱动折叠配方轮播。
    public static void onClientTick(ClientTickEvent.Post event) {
        RecipeGroupManager.tickCarousel();
    }

    @SubscribeEvent
    // 仅在鼠标悬停配方卡片时响应自定义按键，按一次切换当前折叠组。
    public static void onKeyInput(InputEvent.Key event) {
        if (!RecipeGroupKeyMappings.TOGGLE_RECIPE_GROUPS.matches(event.getKey(), event.getScanCode())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() != GLFW.GLFW_PRESS || !(minecraft.screen instanceof RecipesGui)) {
            return;
        }
        RecipeGroupManager.toggleHoveredRecipeGroup();
    }
}
