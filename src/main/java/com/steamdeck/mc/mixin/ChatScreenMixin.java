package com.steamdeck.mc.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Positions chat input at the top of the screen on Steam Deck
 * to leave lower screen area unobstructed for the virtual keyboard (HANDHELD mode only).
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow
    protected EditBox input;
    @Shadow
    private CommandSuggestions commandSuggestions;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!com.steamdeck.mc.platform.PlatformDetector.isHandheld()) return;
        if (this.input != null) {
            this.input.setY(2);
            this.input.setHeight(12);
        }

        this.commandSuggestions = new CommandSuggestions(
                this.minecraft, this, this.input, this.font, false, false, 1, 10, false, 0xCF000000
        );
        this.commandSuggestions.setAllowHiding(false);
        this.commandSuggestions.setAllowSuggestions(false);
        this.commandSuggestions.updateCommandInfo();
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V",
                    ordinal = 0
            )
    )
    private void redirectChatInputBackground(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        if (!com.steamdeck.mc.platform.PlatformDetector.isHandheld()) {
            graphics.fill(x0, y0, x1, y1, color);
            return;
        }
        graphics.fill(2, 1, this.width - 2, 15, color);
    }

    @ModifyArg(
            method = "mouseScrolled",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;scrollChat(I)V"
            ),
            index = 0
    )
    private int invertScrollChat(int lines) {
        // Chat is drawn top-down in handheld mode, so scrolling has to be inverted there only
        return com.steamdeck.mc.platform.PlatformDetector.isHandheld() ? -lines : lines;
    }
}
