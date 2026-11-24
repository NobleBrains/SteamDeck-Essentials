package com.steamdeck.mc.mixin;

import com.steamdeck.mc.gui.BatteryHudRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects minimalist battery HUD telemetry (charge %, wattage, remaining playtime)
 * into the pause screen.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addDeckConfigButton(CallbackInfo ci) {
        int bx = this.width - 105;
        int by = 6;
        int bw = 95;
        int bh = 20;
        // Shift down if another mod (e.g. ModMenu, ReplayMod, Essential) already placed a button in the top-right corner
        for (int attempt = 0; attempt < 5; attempt++) {
            boolean overlap = false;
            for (var child : this.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible) {
                    if (w.getX() < bx + bw && w.getX() + w.getWidth() > bx
                            && w.getY() < by + bh && w.getY() + w.getHeight() > by) {
                        overlap = true;
                        break;
                    }
                }
            }
            if (!overlap) break;
            by += 24;
        }
        this.addRenderableWidget(
                net.minecraft.client.gui.components.Button.builder(
                        Component.literal("🎮 Controller"),
                        btn -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new com.steamdeck.mc.gui.ControllerLayoutScreen(this))
                ).bounds(bx, by, bw, bh).build()
        );
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderBatteryHud(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        BatteryHudRenderer.renderPauseScreenHud(graphics, this.width, this.height);
    }
}
