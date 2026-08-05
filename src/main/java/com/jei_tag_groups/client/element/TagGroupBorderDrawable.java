package com.jei_tag_groups.client.element;

import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;

public final class TagGroupBorderDrawable implements IDrawable {
    private static final int SIZE = 18;
    private final int color;

    public TagGroupBorderDrawable(int color) {
        this.color = color;
    }

    @Override
    public int getWidth() {
        return SIZE;
    }

    @Override
    public int getHeight() {
        return SIZE;
    }

    @Override
    public void draw(GuiGraphics graphics, int x, int y) {
        // JEI 传入的是 16×16 内容区域坐标，向左上补回 1 像素后与 18×18 槽位边界对齐。
        int borderX = x - 1;
        int borderY = y - 1;
        graphics.fill(borderX, borderY, borderX + SIZE, borderY + 1, color);
        graphics.fill(borderX, borderY + SIZE - 1, borderX + SIZE, borderY + SIZE, color);
        graphics.fill(borderX, borderY + 1, borderX + 1, borderY + SIZE - 1, color);
        graphics.fill(borderX + SIZE - 1, borderY + 1, borderX + SIZE, borderY + SIZE - 1, color);
    }
}
