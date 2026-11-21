package com.steamdeck.mc.gui;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.SteamDeckInputManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Decides whether the chat sits at the TOP of the screen (input line at the top edge, messages
 * top-down) so the on-screen keyboard at the bottom never covers it, or stays vanilla at the bottom.
 *
 * <p>Option {@code chatLayout}: {@code AUTO} (default) follows the input device in use: top while
 * the controller is used (e.g. chat opened with a controller button, always the case on the Deck),
 * vanilla as soon as a real keyboard is typed on. {@code TOP} / {@code BOTTOM} force one layout.</p>
 *
 * <p>The layout never changes while the chat is open. The chat component stores its lines in a
 * layout-dependent order, so every change bumps {@link #version()} and the component rebuilds them.</p>
 */
public final class ChatLayout {
    private static boolean top = false;
    private static int version = 0;

    private ChatLayout() {}

    public static boolean isTop() {
        return top;
    }

    /** Incremented on every change; the chat component rebuilds its line list when it differs. */
    public static int version() {
        return version;
    }

    /** Called every client tick. */
    public static void tick(Minecraft client) {
        if (client == null || client.gui == null) return;
        if (client.gui.screen() instanceof ChatScreen) return; // never move an open chat
        apply(wanted());
    }

    /** Called when the chat opens: decide right now (e.g. the button that opened it was the controller). */
    public static void onChatOpening() {
        apply(wanted());
    }

    private static boolean wanted() {
        String mode = ModConfig.getInstance().chatLayout;
        if ("TOP".equals(mode)) return true;
        if ("BOTTOM".equals(mode)) return false;
        return SteamDeckInputManager.getInstance().isControllerContext();
    }

    private static void apply(boolean value) {
        if (value != top) {
            top = value;
            version++;
        }
    }
}
