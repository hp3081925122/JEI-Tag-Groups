package com.jei_tag_groups.client.jei;

import com.jei_tag_groups.Jei_tag_groups;
import com.jei_tag_groups.client.config.RecipeGroupManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class JeiTagGroupsPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(Jei_tag_groups.MODID, "plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    // Runtime 创建完毕后才允许读取配方布局中的输入和输出物品。
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RecipeGroupManager.setJeiRuntimeAvailable(true);
    }

    @Override
    // JEI 关闭或重载时停止使用已失效的 Runtime。
    public void onRuntimeUnavailable() {
        RecipeGroupManager.setJeiRuntimeAvailable(false);
    }
}
