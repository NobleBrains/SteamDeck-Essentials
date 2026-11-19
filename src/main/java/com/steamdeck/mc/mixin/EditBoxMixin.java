package com.steamdeck.mc.mixin;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.gui.VirtualKeyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Automatically opens the on-screen keyboard when an input field receives focus,
 * except when opening the Creative Inventory search tab (where the mini 'KB' button is shown instead).
 */
@Mixin(EditBox.class)
public class EditBoxMixin {
    @Inject(method = "setFocused", at = @At("TAIL"))
    private void onSetFocused(boolean focused, CallbackInfo ci) {
        if (focused && ModConfig.getInstance().oskAutoOpenOnInput && com.steamdeck.mc.platform.PlatformDetector.isHandheld()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.gui != null && client.gui.screen() instanceof CreativeModeInventoryScreen) {
                return;
            }
            VirtualKeyboard.getInstance().setOpen(true);
        }
    }
}
