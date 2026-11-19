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
        this.addRenderableWidget(
                net.minecraft.client.gui.components.Button.builder(
                        Component.literal("🎮 Controller"),
                        btn -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new com.steamdeck.mc.gui.SteamDeckConfigScreen(this))
                ).bounds(this.width - 105, 6, 95, 20).build()
        );
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderBatteryHud(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        BatteryHudRenderer.renderPauseScreenHud(graphics, this.width, this.height);
    }
}
