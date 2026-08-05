package com.jei_tag_groups.client;

import com.jei_tag_groups.Jei_tag_groups;
import com.jei_tag_groups.client.config.RecipeGroupManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Jei_tag_groups.MODID, value = Dist.CLIENT)
public final class RecipeGroupClientTickEvents {
    private RecipeGroupClientTickEvents() {
    }

    @SubscribeEvent
    // 在客户端 tick 中驱动折叠配方轮播。
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RecipeGroupManager.tickCarousel();
        }
    }
}
