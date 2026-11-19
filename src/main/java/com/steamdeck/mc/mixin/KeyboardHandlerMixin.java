package com.steamdeck.mc.mixin;

import com.steamdeck.mc.input.SteamDeckInputManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts keyboard events to bridge Steam Input paddle bindings (L4, R4, L5, R5)
 * into direct Steam Deck controller actions.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (SteamDeckInputManager.getInstance().handleBridgeKeyEvent(event.key(), action)) {
            ci.cancel();
        }
    }
}
