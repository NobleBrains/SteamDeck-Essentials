package com.steamdeck.mc.mixin;

import com.steamdeck.mc.config.ModConfig;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures tooltips are strictly clamped within the display bounds on the Steam Deck's 1280x800 display,
 * preventing long or edge-placed item tooltips from being truncated.
 */
@Mixin(DefaultTooltipPositioner.class)
public class TooltipMixin {
    @Inject(
            method = "positionTooltip(IIIIII)Lorg/joml/Vector2ic;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void clampTooltipToScreen(
            int screenWidth, int screenHeight, int mouseX, int mouseY,
            int tooltipWidth, int tooltipHeight, CallbackInfoReturnable<Vector2ic> cir
    ) {
        if (!ModConfig.getInstance().fixTooltipClipping) return;

        Vector2ic original = cir.getReturnValue();
        if (original == null) return;

        int margin = 4;
        int x = original.x();
        int y = original.y();

        // Prevent right-edge overflow
        if (x + tooltipWidth > screenWidth - margin) {
            x = Math.max(margin, screenWidth - tooltipWidth - margin);
        }
        // Prevent left-edge cutoff
        if (x < margin) {
            x = margin;
        }

        // Prevent bottom-edge overflow
        if (y + tooltipHeight > screenHeight - margin) {
            y = Math.max(margin, screenHeight - tooltipHeight - margin);
        }
        // Prevent top-edge cutoff
        if (y < margin) {
            y = margin;
        }

        if (x != original.x() || y != original.y()) {
            cir.setReturnValue(new Vector2i(x, y));
        }
    }
}
