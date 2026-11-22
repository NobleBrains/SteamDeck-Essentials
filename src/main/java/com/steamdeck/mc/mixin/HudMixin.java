package com.steamdeck.mc.mixin;

import com.steamdeck.mc.gui.RadialMenuOverlay;
import com.steamdeck.mc.input.gyro.GyroManager;
import com.steamdeck.mc.power.PowerManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects in-game AFK screen dimming, frame-synchronous 90Hz Gyro aiming, and the Right-Stick Radial Menu overlay onto the HUD.
 */
@Mixin(Hud.class)
public class HudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        com.steamdeck.mc.input.SteamDeckInputManager.getInstance().pollCameraFrame(Minecraft.getInstance());
        GyroManager.getInstance().update(Minecraft.getInstance());
        RadialMenuOverlay.getInstance().render(graphics, graphics.guiWidth(), graphics.guiHeight());
        com.steamdeck.mc.gui.TrackpadGridOverlay.render(graphics, graphics.guiWidth(), graphics.guiHeight());
        if (Minecraft.getInstance().gui == null || Minecraft.getInstance().gui.screen() == null) {
            com.steamdeck.mc.gui.LevelOsd.render(graphics, graphics.guiWidth(), graphics.guiHeight());
        }
        if (Minecraft.getInstance().gui == null || Minecraft.getInstance().gui.screen() == null) {
            com.steamdeck.mc.gui.TrackpadDebugOverlay.render(graphics, graphics.guiWidth(), graphics.guiHeight());
        }
        PowerManager.getInstance().renderAfkOverlay(graphics, graphics.guiWidth(), graphics.guiHeight());
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.screen() == null) {
            // With a screen open the popup is drawn by the screen overlay hook (on top of the screen)
            com.steamdeck.mc.gui.ControllerToast.getInstance().render(graphics, graphics.guiWidth(), graphics.guiHeight());
        }
    }
}
