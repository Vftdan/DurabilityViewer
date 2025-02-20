package de.guntram.mcmod.durabilityviewer.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.TreeSet;

@Mixin(ItemStack.class)
public abstract class TooltipMixin {

    @Shadow
    public abstract boolean isEmpty();

    @Shadow
    public abstract boolean isDamaged();

    @Shadow
    public abstract int getMaxDamage();

    @Shadow
    public abstract int getDamage();

    /*@Inject(method = "getTooltip", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void getTooltipdone(PlayerEntity playerIn, TooltipContext advanced, CallbackInfoReturnable<List<Text>> ci, List<Text> list) {
        if (!advanced.isAdvanced() && !this.isEmpty()) {
            if (this.isDamaged()) {
                Color4f color4f = Configs.Settings.TooltipColor.getColor();
                Color color = new Color(color4f.r, color4f.g, color4f.b);
                Text toolTip = Text.translatable("item.durability", this.getMaxDamage() - this.getDamage(), this.getMaxDamage()).setStyle(Style.EMPTY.withColor(color.getRGB()));
                if (!list.contains(toolTip)) {
                    list.add(toolTip);
                }
            }
        }

        if (Screen.hasAltDown()) {
            NbtCompound tag = this.getNbt();
            if (tag != null) {
                addNbtCompound("", list, tag);
            }
        }
    }*/

    private void addNbtCompound(String prefix, List<Text> list, NbtCompound tag) {
        TreeSet<String> sortedKeys = new TreeSet<>(tag.getKeys());
        for (String key : sortedKeys) {
            NbtElement elem = tag.get(key);
            switch (elem.getType()) {
                case 2 -> list.add(Text.literal(prefix + key + ": §2" + tag.getShort(key)));
                case 3 -> list.add(Text.literal(prefix + key + ": §3" + tag.getInt(key)));
                case 6 -> list.add(Text.literal(prefix + key + ": §6" + tag.getDouble(key)));
                case 8 -> list.add(Text.literal(prefix + key + ": §8" + tag.getString(key)));
                case 9 -> list.add(Text.literal(prefix + key + ": §9List, " + ((NbtList) elem).size() + " items"));
                case 10 -> {
                    list.add(Text.literal(prefix + key + ": §aCompound"));
                    if (Screen.hasShiftDown()) {
                        addNbtCompound(prefix + "    ", list, (NbtCompound) elem);
                    }
                }
                default -> list.add(Text.literal(prefix + key + ": Type " + elem.getType()));
            }
        }
    }
}
