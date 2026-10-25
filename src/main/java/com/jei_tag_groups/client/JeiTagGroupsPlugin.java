package com.jei_tag_groups.client;

import com.jei_tag_groups.client.config.RecipeGroupManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class JeiTagGroupsPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.parse("jei_tag_groups:main");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RecipeGroupManager.setRecipeManager(runtime.getRecipeManager());
        RecipeGroupManager.setJeiRuntimeAvailable(true);
    }

    @Override
    public void onRuntimeUnavailable() {
        RecipeGroupManager.setJeiRuntimeAvailable(false);
        RecipeGroupManager.setRecipeManager(null);
    }
}
