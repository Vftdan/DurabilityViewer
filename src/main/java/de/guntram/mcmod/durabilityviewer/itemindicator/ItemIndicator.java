package de.guntram.mcmod.durabilityviewer.itemindicator;

import net.minecraft.item.ItemStack;

import java.awt.*;

public interface ItemIndicator {
    int color_white = Color.WHITE.getRGB(),
            color_green = Color.GREEN.getRGB(),
            color_yellow = Color.YELLOW.getRGB(),
            color_red = Color.RED.getRGB();

    String getDisplayValue();

    int getDisplayColor();

    boolean isEmpty();

    boolean isItemStackDamageable();

    ItemStack getItemStack();
}
