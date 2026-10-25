package com.jei_tag_groups.client;

import com.jei_tag_groups.client.config.TagGroupManager;
import com.jei_tag_groups.client.config.RecipeGroupManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

@JeiPlugin
public final class TagGroupJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.parse("jei_tag_groups:groups");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        TagGroupManager.setSmithingRecipeManager(runtime.getRecipeManager());
        RecipeGroupManager.setRecipeManager(runtime.getRecipeManager());
        RecipeGroupManager.setRuntimeAvailable(true);
    }

    @Override
    public void onRuntimeUnavailable() {
        TagGroupManager.setSmithingRecipeManager(null);
        RecipeGroupManager.setRuntimeAvailable(false);
        RecipeGroupManager.setRecipeManager(null);
    }
}
