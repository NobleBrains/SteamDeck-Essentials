package com.steamdeck.mc.mixin;

import com.steamdeck.mc.input.SteamDeckInputManager;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects native 4x handheld zoom when the DeckAction.ZOOM action is held.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void injectZoomFov(boolean firstPerson, float fovModifier, CallbackInfoReturnable<Float> cir) {
        if (SteamDeckInputManager.getInstance().isZooming()) {
            cir.setReturnValue(cir.getReturnValue() * 0.25f);
        }
    }
}
