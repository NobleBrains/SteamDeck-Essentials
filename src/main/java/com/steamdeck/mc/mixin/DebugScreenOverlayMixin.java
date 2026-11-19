package com.steamdeck.mc.mixin;

import com.steamdeck.mc.gui.BatteryHudRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects Steam Deck battery metrics into the F3 debug overlay.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Shadow
    public abstract boolean showDebugScreen();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderBatteryHudInDebug(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.showDebugScreen()) {
            BatteryHudRenderer.renderDebugOverlayHud(graphics, graphics.guiWidth(), graphics.guiHeight());
        }
    }
}
