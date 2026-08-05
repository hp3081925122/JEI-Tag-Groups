package com.jei_tag_groups.client;

import com.jei_tag_groups.Jei_tag_groups;
import com.jei_tag_groups.client.config.RecipeGroupManager;
import com.jei_tag_groups.client.input.RecipeGroupKeyMappings;
import com.mojang.logging.LogUtils;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = Jei_tag_groups.MODID, value = Dist.CLIENT)
public final class RecipeGroupClientTickEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RecipeGroupClientTickEvents() {
    }

    @SubscribeEvent
    // 在客户端 tick 中驱动折叠配方轮播。
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RecipeGroupManager.tickCarousel();
        }
    }

    @SubscribeEvent
    // 仅在鼠标悬停配方卡片时响应自定义按键，按一次切换当前折叠组。
    public static void onKeyInput(InputEvent.Key event) {
        if (!RecipeGroupKeyMappings.TOGGLE_RECIPE_GROUPS.matches(event.getKey(), event.getScanCode())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LOGGER.debug("Recipe group key event: key={}, scanCode={}, action={}, screen={}", event.getKey(), event.getScanCode(), event.getAction(),
            minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        if (event.getAction() != GLFW.GLFW_PRESS || !(minecraft.screen instanceof RecipesGui)) {
            return;
        }
        RecipeGroupManager.toggleHoveredRecipeGroup();
    }
}
