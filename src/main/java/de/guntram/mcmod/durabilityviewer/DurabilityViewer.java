package de.guntram.mcmod.durabilityviewer;

import de.guntram.mcmod.durabilityviewer.client.gui.GuiItemDurability;
import de.guntram.mcmod.durabilityviewer.handler.ConfigurationHandler;
import de.guntram.mcmod.fabrictools.ConfigurationProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_H;

public class DurabilityViewer implements ClientModInitializer {
    public static final String MODID = "durabilityviewer";
    public static final String MODNAME = "Durability Viewer";

    public static DurabilityViewer instance;
    private static ConfigurationHandler confHandler;
    private static String changedWindowTitle;
    private KeyBinding showHide;

    @Override
    public void onInitializeClient() {
        setKeyBindings();
        confHandler = ConfigurationHandler.getInstance();
        ConfigurationProvider.register(MODNAME, confHandler);
        confHandler.load(ConfigurationProvider.getSuggestedFile(MODID));
        changedWindowTitle = null;
    }

    public static void setWindowTitle(String s) {
        changedWindowTitle = s;
    }

    public static String getWindowTitle() {
        return changedWindowTitle;
    }

    public void processKeyBinds() {
        if (showHide.wasPressed()) {
            GuiItemDurability.toggleVisibility();
        }
    }

    public void setKeyBindings() {
        final String category = "key.categories.durabilityviewer";
        KeyBindingHelper.registerKeyBinding(showHide = new KeyBinding("key.durabilityviewer.showhide", InputUtil.Type.KEYSYM, GLFW_KEY_H, category));
        ClientTickEvents.END_CLIENT_TICK.register(e -> processKeyBinds());
    }
}
