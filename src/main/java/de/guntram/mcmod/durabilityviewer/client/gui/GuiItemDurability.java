package de.guntram.mcmod.durabilityviewer.client.gui;

import com.google.common.collect.Ordering;
import com.mojang.blaze3d.systems.RenderSystem;
import de.guntram.mcmod.durabilityviewer.config.Configs;
import de.guntram.mcmod.durabilityviewer.config.WarnMode;
import de.guntram.mcmod.durabilityviewer.itemindicator.ColytraDamageIndicator;
import de.guntram.mcmod.durabilityviewer.itemindicator.InventorySlotsIndicator;
import de.guntram.mcmod.durabilityviewer.itemindicator.ItemCountIndicator;
import de.guntram.mcmod.durabilityviewer.itemindicator.ItemDamageIndicator;
import de.guntram.mcmod.durabilityviewer.itemindicator.ItemIndicator;
import de.guntram.mcmod.durabilityviewer.itemindicator.TREnergyIndicator;
import de.guntram.mcmod.durabilityviewer.sound.ColytraBreakingWarner;
import de.guntram.mcmod.durabilityviewer.sound.ItemBreakingWarner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4fStack;
import team.reborn.energy.api.EnergyStorage;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


public class GuiItemDurability {

    private static final Logger LOGGER = LogManager.getLogger();
    private final MinecraftClient minecraft;
    private static boolean visible;
    private final TextRenderer fontRenderer;
    private final ItemRenderer itemRenderer;

    private long lastWarningTime;
    private ItemStack lastWarningItem;

    private static final int iconWidth = 16;
    private static final int iconHeight = 16;
    private static final int spacing = 2;

    private static boolean haveTrinketsApi = false;
    private static boolean haveTRCore = false;

    private final ItemBreakingWarner mainHandWarner, offHandWarner, helmetWarner, chestWarner, pantsWarner, bootsWarner;
    private final ItemBreakingWarner colytraWarner;
    private ItemBreakingWarner[] trinketWarners;

    public static void toggleVisibility() {
        visible = !visible;
    }

    public GuiItemDurability() {
        minecraft = MinecraftClient.getInstance();
        fontRenderer = minecraft.textRenderer;
        itemRenderer = minecraft.getItemRenderer();
        visible = true;

        mainHandWarner = new ItemBreakingWarner();
        offHandWarner = new ItemBreakingWarner();
        helmetWarner = new ItemBreakingWarner();
        chestWarner = new ItemBreakingWarner();
        pantsWarner = new ItemBreakingWarner();
        bootsWarner = new ItemBreakingWarner();
        colytraWarner = new ColytraBreakingWarner();

        try {
            Class.forName("dev.emi.trinkets.api.TrinketsApi");
            LOGGER.warn("DurabilityViewer was compiled without trinkets support");
            trinketWarners = new ItemBreakingWarner[0];
        } catch (ClassNotFoundException ex) {
            LOGGER.info("DurabilityViewer did not find Trinkets API");
            trinketWarners = new ItemBreakingWarner[0];
        }
        try {
            Class.forName("team.reborn.energy.api.EnergyStorage");
            haveTRCore = true;
        } catch (ClassNotFoundException ex) {
            LOGGER.info("DurabilityViewer did not find Tech Reborn");
        }
    }

    private int getInventoryArrowCount() {
        int arrows = 0;
        for (final ItemStack stack : minecraft.player.getInventory().main) {
            if (isArrow(stack)) {
                arrows += stack.getCount();
            }
        }
        return arrows;
    }

    private ItemStack getFirstArrowStack() {
        if (isArrow(minecraft.player.getOffHandStack())) {
            return minecraft.player.getOffHandStack();
        }
        if (isArrow(minecraft.player.getMainHandStack())) {
            return minecraft.player.getMainHandStack();
        }
        int size = minecraft.player.getInventory().size();
        for (int i = 0; i < size; ++i) {
            final ItemStack itemstack = minecraft.player.getInventory().getStack(i);
            if (this.isArrow(itemstack)) {
                return itemstack;
            }
        }
        return null;
    }

    private boolean isArrow(final ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArrowItem;
    }

    private static class RenderSize {
        int width;
        int height;

        RenderSize(int w, int h) {
            width = w;
            height = h;
        }
    }

    private enum RenderPos {
        left, over, right;
    }

    public static float getConfigScaleFactor() {
        float scale = Configs.Settings.HUDScalePercent.getIntegerValue() / 100.0f;
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        return scale;
    }

    public void onRenderGameOverlayPost(DrawContext context, float partialTicks) {
        float scale = getConfigScaleFactor();

        MatrixStack stack = context.getMatrices();
        stack.push();
        stack.scale(scale, scale, scale);

        try {
            onRenderGameOverlayPostPrescaled(context, partialTicks);
        } finally {
            stack.pop();
        }
    }

    private static RenderSize getScaledWindowSize() {
        float scale = getConfigScaleFactor();

        Window mainWindow = MinecraftClient.getInstance().getWindow();
        return new RenderSize((int) (mainWindow.getScaledWidth() / scale), (int) (mainWindow.getScaledHeight() / scale));
    }

    private static Rect2i getScaledHotbarPlusIconRect() {
        float scale = getConfigScaleFactor();
        RenderSize windowSize = getScaledWindowSize();
        MinecraftClient minecraft = MinecraftClient.getInstance();

        float leftOffset = -120 + iconWidth;
        float rightOffset = 100;
        if (!minecraft.player.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty()) {
            if (minecraft.options.getMainArm().getValue() == Arm.RIGHT) {
                leftOffset -= 20;
            } else {
                rightOffset += 20;
            }
        }

        leftOffset /= scale;
        rightOffset /= scale;
        leftOffset -= iconWidth;
        float height = 20 / scale;

        return new Rect2i((int) (windowSize.width / 2 + leftOffset), (int) (windowSize.height - height), (int) (rightOffset - leftOffset), (int) height);
    }

    private static int getScaledEffectsHeight() {
        float scale = getConfigScaleFactor();
        return (int) (55 / scale);
    }

    protected void onRenderGameOverlayPostPrescaled(DrawContext context, float partialTicks) {

        PlayerEntity player = minecraft.player;
        ItemStack needToWarn = null;

        ItemIndicator mainHand, offHand;
        mainHand = damageOrEnergy(player, EquipmentSlot.MAINHAND);
        offHand = damageOrEnergy(player, EquipmentSlot.OFFHAND);

        ItemStack chestItem = player.getEquippedStack(EquipmentSlot.CHEST);
        ItemIndicator colytra = null;
        /*if (chestItem != null && chestItem.getNbt() != null && chestItem.getNbt().contains("colytra:ElytraUpgrade")) {
            colytra = new ColytraDamageIndicator(chestItem);
        }*/

        ItemIndicator boots = new ItemDamageIndicator(player.getEquippedStack(EquipmentSlot.FEET));
        ItemIndicator leggings = new ItemDamageIndicator(player.getEquippedStack(EquipmentSlot.LEGS));
        ItemIndicator chestplate = new ItemDamageIndicator(chestItem);
        ItemIndicator helmet = new ItemDamageIndicator(player.getEquippedStack(EquipmentSlot.HEAD));
        ItemIndicator arrows = null;
        ItemIndicator invSlots = (Configs.Settings.ShowFreeInventorySlots.getBooleanValue() ? new InventorySlotsIndicator(minecraft.player.getInventory()) : null);

        if (mainHandWarner.checkBreaks(player.getEquippedStack(EquipmentSlot.MAINHAND)))
            needToWarn = player.getEquippedStack(EquipmentSlot.MAINHAND);
        if (needToWarn == null && offHandWarner.checkBreaks(player.getEquippedStack(EquipmentSlot.OFFHAND)))
            needToWarn = player.getEquippedStack(EquipmentSlot.OFFHAND);
        if (needToWarn == null && bootsWarner.checkBreaks(player.getEquippedStack(EquipmentSlot.FEET)))
            needToWarn = player.getEquippedStack(EquipmentSlot.FEET);
        if (needToWarn == null && pantsWarner.checkBreaks(player.getEquippedStack(EquipmentSlot.LEGS)))
            needToWarn = player.getEquippedStack(EquipmentSlot.LEGS);
        if (needToWarn == null && chestWarner.checkBreaks(chestItem)) needToWarn = chestItem;
        if (needToWarn == null && helmetWarner.checkBreaks(player.getEquippedStack(EquipmentSlot.HEAD)))
            needToWarn = player.getEquippedStack(EquipmentSlot.HEAD);
        if (needToWarn == null && colytraWarner.checkBreaks(chestItem)) needToWarn = chestItem;

        ItemIndicator[] trinkets;
        if (haveTrinketsApi) {
            trinkets = new ItemIndicator[0];
        } else {
            trinkets = new ItemIndicator[0];
        }

        WarnMode warnMode = (WarnMode) Configs.Settings.WarningMode.getOptionListValue();
        if (needToWarn != null) {
            if (warnMode == WarnMode.SOUND || warnMode == WarnMode.BOTH) {
                ItemBreakingWarner.playWarningSound();
            }
            lastWarningTime = System.currentTimeMillis();
            lastWarningItem = needToWarn;
        }

        long timeSinceLastWarning = System.currentTimeMillis() - lastWarningTime;
        if (timeSinceLastWarning < 1000 && (warnMode == WarnMode.VISUAL || warnMode == WarnMode.BOTH)) {
            renderItemBreakingOverlay(context, lastWarningItem, timeSinceLastWarning);
        }

        // Moved this check to down here, in order to play the 
        // warning sound / do the visible 
        if (!visible || minecraft.getDebugHud().shouldShowDebugHud()) {
            return;
        }


        if (mainHand.getItemStack().getItem() instanceof RangedWeaponItem
                || offHand.getItemStack().getItem() instanceof RangedWeaponItem) {
            arrows = new ItemCountIndicator(getFirstArrowStack(), getInventoryArrowCount());
        }

        RenderSize windowSize = getScaledWindowSize();
        RenderSize armorSize, toolsSize, trinketsSize;
        if (Configs.Settings.ArmorAroundHotbar.getBooleanValue()) {
            armorSize = new RenderSize(0, 0);
        } else {
            armorSize = this.renderItems(context, 0, 0, false, RenderPos.left, 0, boots, leggings, colytra, chestplate, helmet);
        }
        toolsSize = this.renderItems(context, 0, 0, false, RenderPos.right, 0, invSlots, mainHand, offHand, arrows);
        trinketsSize = this.renderItems(context, 0, 0, false, RenderPos.left, 0, trinkets);

        int totalHeight = (Math.max(toolsSize.height, armorSize.height));
        if (trinketsSize.height > totalHeight) {
            totalHeight = trinketsSize.height;
        }
        if (trinketsSize.width == 0 && trinkets.length > 0 && Configs.Settings.ShowAllTrinkets.getBooleanValue()) {
            trinketsSize.width = iconWidth + spacing * 2;
        }
        int xposArmor, xposTools, xposTrinkets, ypos;

        Corner corner = (Corner) Configs.Settings.HUDCorner.getOptionListValue();
        switch (corner) {
            case TOP_LEFT -> {
                xposArmor = 5;
                xposTools = 5 + armorSize.width;
                xposTrinkets = 5 + armorSize.width + trinketsSize.width;
                ypos = 5;
            }
            case TOP_RIGHT -> {
                xposArmor = windowSize.width - 5 - armorSize.width;
                xposTools = xposArmor - toolsSize.width;
                xposTrinkets = xposTools - trinketsSize.width;
                ypos = 5 + getScaledEffectsHeight();   // below buff/debuff effects
            }
            case BOTTOM_LEFT -> {
                xposArmor = 5;
                xposTools = 5 + armorSize.width;
                xposTrinkets = 5 + armorSize.width + trinketsSize.width;
                ypos = windowSize.height - 5 - totalHeight;
            }
            case BOTTOM_RIGHT -> {
                xposArmor = windowSize.width - 5 - armorSize.width;
                xposTools = windowSize.width - 5 - armorSize.width - toolsSize.width;
                xposTrinkets = xposTools - trinketsSize.width;
                ypos = windowSize.height - 5 - totalHeight;
            }
            default -> {
                return;
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (Configs.Settings.ArmorAroundHotbar.getBooleanValue()) {
            Rect2i hotbarRect = getScaledHotbarPlusIconRect();
            int helmetTextWidth = fontRenderer.getWidth(helmet.getDisplayValue());
            int chestTextWidth = fontRenderer.getWidth(chestplate.getDisplayValue());
            this.renderItems(context, hotbarRect.getX() - helmetTextWidth, windowSize.height - iconHeight * 2 - 2, true, RenderPos.left, helmetTextWidth + iconWidth + spacing, helmet);
            this.renderItems(context, hotbarRect.getX() - chestTextWidth, windowSize.height - iconHeight - 2, true, RenderPos.left, chestTextWidth + iconWidth + spacing, chestplate);
            if (colytra != null) {
                int colytraTextWidth = fontRenderer.getWidth(colytra.getDisplayValue());
                this.renderItems(context, hotbarRect.getX() - chestTextWidth - colytraTextWidth - iconWidth, windowSize.height - iconHeight - 2, true, RenderPos.left, colytraTextWidth + iconWidth + spacing, colytra);
            }
            this.renderItems(context, hotbarRect.getX() + hotbarRect.getWidth(), windowSize.height - iconHeight * 2 - 2, true, RenderPos.right, armorSize.width, leggings);
            this.renderItems(context, hotbarRect.getX() + hotbarRect.getWidth(), windowSize.height - iconHeight - 2, true, RenderPos.right, armorSize.width, boots);
            if (corner.isRight()) {
                xposTools += armorSize.width;
            } else {
                xposTools -= armorSize.width;
            }
        } else {
            this.renderItems(context, xposArmor, ypos, true, corner.isLeft() ? RenderPos.left : RenderPos.right, armorSize.width, helmet, chestplate, colytra, leggings, boots);
        }
        this.renderItems(context, xposTools, ypos, true, corner.isRight() ? RenderPos.right : RenderPos.left, toolsSize.width, invSlots, mainHand, offHand, arrows);
        this.renderItems(context, xposTrinkets, ypos, true, corner.isRight() ? RenderPos.right : RenderPos.left, trinketsSize.width, trinkets);
    }

    private ItemIndicator damageOrEnergy(PlayerEntity player, EquipmentSlot slot) {
        ItemStack stack = player.getEquippedStack(slot);
        if (stack.isDamageable()) {
            return new ItemDamageIndicator(stack);
        } else if (haveTRCore) {
            /*if (stack.getItem() instanceof EnergyHolder && stack.getNbt() != null && stack.getNbt().contains("energy", 6)) {
                return new TREnergyIndicator(stack);
            }*/
        }
        return new ItemDamageIndicator(stack);
    }

    private void renderItemBreakingOverlay(DrawContext context, ItemStack itemStack, long timeDelta) {
        RenderSize windowSize = getScaledWindowSize();
        float alpha = 1.0f - ((float) timeDelta / 1000.0f);
        float xWarn = windowSize.width / 2f;
        float yWarn = windowSize.height / 2f;
        float scale = 5.0f;

        context.fill(0, 0, windowSize.width, windowSize.height, 0xff0000 + ((int) (alpha * 128) << 24));

        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.scale(scale, scale, scale);

        context.drawItem(itemStack, (int) ((xWarn) / scale - 8), (int) ((yWarn) / scale - 8));

        stack.popMatrix();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public void afterRenderStatusEffects(DrawContext context, float partialTicks) {
        if (Configs.Settings.EffectDuration.getBooleanValue()) {
            // a lot of this is copied from net/minecraft/client/gui/GuiIngame.java
            Window mainWindow = MinecraftClient.getInstance().getWindow();
            Collection<StatusEffectInstance> collection = minecraft.player.getStatusEffects();
            int posGood = 0, posBad = 0;
            for (StatusEffectInstance potioneffect : Ordering.natural().reverse().sortedCopy(collection)) {
                if (potioneffect.shouldShowIcon()) {
                    StatusEffect potion = potioneffect.getEffectType().value();
                    int xpos = mainWindow.getScaledWidth();
                    int ypos;
                    if (potion.isBeneficial()) {     // isBeneficial
                        posGood += 25;
                        xpos -= posGood;
                        ypos = 15;
                    } else {
                        posBad += 25;
                        xpos -= posBad;
                        ypos = 41;
                    }
                    int duration = potioneffect.getDuration();
                    String show;
                    if (duration > 1200)
                        show = (duration / 1200) + "m";
                    else
                        show = (duration / 20) + "s";
                    context.drawText(fontRenderer, show, xpos + 2, ypos, ItemIndicator.color_yellow, true);
                }
            }
        }
    }

    private RenderSize renderItems(DrawContext context, int xpos, int ypos, boolean reallyDraw, RenderPos numberPos, int maxWidth, ItemIndicator... items) {
        RenderSize result = new RenderSize(0, 0);

        for (ItemIndicator item : items) {
            if (item != null && !item.isEmpty() && item.isItemStackDamageable()) {
                String displayString = item.getDisplayValue();
                int width = fontRenderer.getWidth(displayString);
                if (width > result.width)
                    result.width = width;
                if (reallyDraw) {
                    int color = item.getDisplayColor();
                    context.drawItem(item.getItemStack(), numberPos == RenderPos.left ? xpos + maxWidth - iconWidth - spacing : xpos, ypos + result.height);
                    context.drawText(fontRenderer, displayString, numberPos != RenderPos.right ? xpos : xpos + iconWidth + spacing, (int) (ypos + result.height + fontRenderer.fontHeight / 2f + (numberPos == RenderPos.over ? 10 : 0)), color, true);
                }
                result.height += 16;
            }
        }
        if (result.width != 0)
            result.width += iconWidth + spacing * 2;
        return result;
    }

    public int getTrinketSlotCount(LivingEntity player) {
        return 0;//component.map(trinketComponent -> trinketComponent.getEquipped(prdct -> true).size()).orElse(0);
    }

    public List<ItemStack> getTrinkets(LivingEntity player) {
        return null;//component.map(trinketComponent -> trinketComponent.getEquipped(prdct -> true).stream().map(Pair::getRight).toList()).orElse(null);
    }
}
