package com.aicompanion.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static final String KEY_CATEGORY = "key.categories.aicompanion";

    public static final KeyMapping KEY_OPEN_GUI = new KeyMapping(
            "key.aicompanion.open_gui",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            KEY_CATEGORY
    );

    public static final KeyMapping KEY_RECALL = new KeyMapping(
            "key.aicompanion.recall",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY
    );

    public static final KeyMapping KEY_TOGGLE_HUD = new KeyMapping(
            "key.aicompanion.toggle_hud",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KEY_CATEGORY
    );
}
