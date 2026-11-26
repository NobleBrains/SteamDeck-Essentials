package com.steamdeck.mc.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.steamdeck.mc.input.SteamDeckInputManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla polls the physical Shift/Ctrl/Alt keys here (recipe book, item tooltips, chat, buttons).
 * A controller button that holds one of these keys (Sneak = Shift, Sprint = Ctrl, or a custom
 * Shift/Ctrl/Alt key) counts as held, exactly like the keyboard key it stands for.
 */
@Mixin(Minecraft.class)
public class MinecraftModifierKeysMixin {
    @Inject(method = "hasShiftDown", at = @At("RETURN"), cancellable = true)
    private void steamdeck$controllerShift(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (SteamDeckInputManager.getInstance().heldModifiers() & InputConstants.MOD_SHIFT) != 0) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasControlDown", at = @At("RETURN"), cancellable = true)
    private void steamdeck$controllerControl(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (SteamDeckInputManager.getInstance().heldModifiers() & InputConstants.MOD_CONTROL) != 0) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasAltDown", at = @At("RETURN"), cancellable = true)
    private void steamdeck$controllerAlt(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (SteamDeckInputManager.getInstance().heldModifiers() & InputConstants.MOD_ALT) != 0) {
            cir.setReturnValue(true);
        }
    }
}
