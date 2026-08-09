package com.jei_tag_groups.client;

import com.jei_tag_groups.Jei_tag_groups;
import com.jei_tag_groups.client.config.TagGroupConfig;
import com.jei_tag_groups.client.config.TagGroupManager;
import com.jei_tag_groups.client.config.RecipeGroupManager;
import com.jei_tag_groups.client.input.RecipeGroupKeyMappings;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;

@EventBusSubscriber(modid = Jei_tag_groups.MODID, value = Dist.CLIENT)
public final class TagGroupClientEvents {
    private TagGroupClientEvents() {
    }

    @SubscribeEvent
    // 客户端首次初始化时立即读取配置，保证首次打开 JEI 就能使用聚合。
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            TagGroupConfig.Configuration configuration = TagGroupConfig.load();
            TagGroupManager.setDefinitions(configuration.tagGroups());
            RecipeGroupManager.setDefinitions(configuration.recipeGroups());
        });
    }

    @SubscribeEvent
    // 注册可在 Minecraft 按键设置中修改的配方展开按键。
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(RecipeGroupKeyMappings.TOGGLE_RECIPE_GROUPS);
    }

    @SubscribeEvent
    // F3+T 触发资源重载时重新读取外部配置文件。
    public static void registerReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.parse("jei_tag_groups:tag_groups"), new SimplePreparableReloadListener<TagGroupConfig.Configuration>() {
            @Override
            protected TagGroupConfig.Configuration prepare(net.minecraft.server.packs.resources.ResourceManager resourceManager, net.minecraft.util.profiling.ProfilerFiller profiler) {
                return TagGroupConfig.load();
            }

            @Override
            protected void apply(TagGroupConfig.Configuration configuration, net.minecraft.server.packs.resources.ResourceManager resourceManager, net.minecraft.util.profiling.ProfilerFiller profiler) {
                TagGroupManager.setDefinitions(configuration.tagGroups());
                RecipeGroupManager.setDefinitions(configuration.recipeGroups());
            }
        });
    }
}
