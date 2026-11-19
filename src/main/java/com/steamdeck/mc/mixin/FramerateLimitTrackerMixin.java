package com.steamdeck.mc.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.steamdeck.mc.power.PowerManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Throttles rendering frame rate when in menus (e.g. 30 FPS) or when the window is in the background
 * (5-10 FPS) to aggressively conserve battery life on the Steam Deck.
 */
@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void onGetFramerateLimit(CallbackInfoReturnable<Integer> cir) {
        int customCap = PowerManager.getInstance().getCustomFramerateCap(this.minecraft);
        if (customCap > 0) {
            cir.setReturnValue(customCap);
        }
    }

    @Inject(method = "onInputReceived", at = @At("TAIL"))
    private void onInputReceivedHook(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        PowerManager.getInstance().onUserActivity();
    }
}
