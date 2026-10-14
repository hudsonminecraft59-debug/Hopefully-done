package com.verdant;

import com.mojang.blaze3d.platform.InputConstants;
import com.verdant.config.VerdantConfig;
import com.verdant.feature.TargetScanner;
import com.verdant.gui.VerdantScreen;
import com.verdant.render.EspRenderer;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class VerdantClient implements ClientModInitializer {
    public static final String MOD_ID = "verdant";

    private static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        VerdantConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.verdant.gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new VerdantScreen());
                }
            }
            TargetScanner.tick(client);
        });

        EspRenderer.init();
    }
}
