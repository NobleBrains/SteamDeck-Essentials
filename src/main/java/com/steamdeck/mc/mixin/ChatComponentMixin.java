package com.steamdeck.mc.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Modifies chat rendering bounds and scroll direction to integrate with the OSK.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;
    @Shadow
    private int chatScrollbarPos;
    @Shadow
    private boolean newMessageSinceScroll;
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    abstract int getWidth();

    @Shadow
    abstract double getScale();

    @Shadow
    abstract boolean isChatFocused();

    @Shadow
    public abstract void scrollChat(int lines);

    @Shadow
    public abstract int getLinesPerPage();

    @Inject(
            method = "addMessageToDisplayQueue",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddMessageToDisplayQueue(GuiMessage message, CallbackInfo ci) {
        int width = Mth.floor((double) this.getWidth() / this.getScale());
        List<FormattedCharSequence> lines = message.splitLines(this.minecraft.font, width);
        boolean focused = this.isChatFocused();

        for (int i = lines.size() - 1; i >= 0; --i) {
            FormattedCharSequence line = lines.get(i);
            if (focused && this.chatScrollbarPos > 0) {
                this.newMessageSinceScroll = true;
                this.scrollChat(1);
            }

            boolean isEndOfEntry = (i == lines.size() - 1);
            this.trimmedMessages.addFirst(new GuiMessage.Line(message, line, isEndOfEntry));
        }

        while (this.trimmedMessages.size() > 100) {
            this.trimmedMessages.removeLast();
        }

        ci.cancel();
    }

    @Redirect(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;floor(F)I"
            )
    )
    private int redirectChatBottom(float value) {
        float scale = (float) this.getScale();
        float effectiveScale = scale <= 0.0F ? 1.0F : scale;
        return (int) Math.ceil(18.0F / effectiveScale);
    }

    @ModifyArg(
            method = "forEachLine",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V"
            ),
            index = 1
    )
    private int modifyLineIndex(int lineIndex) {
        return -(lineIndex + 1);
    }

    @Redirect(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V",
                    ordinal = 2
            )
    )
    private void redirectScrollbarOuter(ChatComponent.ChatGraphicsAccess access, int x0, int y0, int x1, int y1, int color) {
        float scale = (float) this.getScale();
        float effectiveScale = scale <= 0.0F ? 1.0F : scale;
        int bottom = (int) Math.ceil(18.0F / effectiveScale);
        int barHeight = Math.abs(y1 - y0);
        int totalLines = this.trimmedMessages.size();
        int linesPerPage = this.getLinesPerPage();
        double lineSpacing = (Double) this.minecraft.options.chatLineSpacing().get();
        int lineHeight = (int) (9.0 * (lineSpacing + 1.0));
        int maxScroll = linesPerPage * lineHeight;
        int offset = totalLines > 0 ? this.chatScrollbarPos * maxScroll / totalLines : 0;
        int top = bottom + offset;
        access.fill(x0, top, x1, top + barHeight, color);
    }

    @Redirect(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V",
                    ordinal = 3
            )
    )
    private void redirectScrollbarInner(ChatComponent.ChatGraphicsAccess access, int x0, int y0, int x1, int y1, int color) {
        float scale = (float) this.getScale();
        float effectiveScale = scale <= 0.0F ? 1.0F : scale;
        int bottom = (int) Math.ceil(18.0F / effectiveScale);
        int barHeight = Math.abs(y1 - y0);
        int totalLines = this.trimmedMessages.size();
        int linesPerPage = this.getLinesPerPage();
        double lineSpacing = (Double) this.minecraft.options.chatLineSpacing().get();
        int lineHeight = (int) (9.0 * (lineSpacing + 1.0));
        int maxScroll = linesPerPage * lineHeight;
        int offset = totalLines > 0 ? this.chatScrollbarPos * maxScroll / totalLines : 0;
        int top = bottom + offset;
        access.fill(x0, top, x1, top + barHeight, color);
    }

    @Redirect(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V",
                    ordinal = 1
            )
    )
    private void redirectRestrictedPromptBg(ChatComponent.ChatGraphicsAccess access, int x0, int y0, int x1, int y1, int color) {
        float scale = (float) this.getScale();
        float effectiveScale = scale <= 0.0F ? 1.0F : scale;
        int bottom = (int) Math.ceil(18.0F / effectiveScale);
        double lineSpacing = (Double) this.minecraft.options.chatLineSpacing().get();
        int lineHeight = (int) (9.0 * (lineSpacing + 1.0));
        int promptTop = bottom + this.getLinesPerPage() * lineHeight;
        access.fill(x0, promptTop, x1, promptTop + lineHeight, color);
    }
}
