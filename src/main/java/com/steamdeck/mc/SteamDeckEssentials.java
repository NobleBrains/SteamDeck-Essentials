package com.steamdeck.mc;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.gui.VirtualKeyboard;
import com.steamdeck.mc.input.SteamDeckInputManager;
import com.steamdeck.mc.input.gyro.GyroManager;
import com.steamdeck.mc.platform.PlatformDetector;
import com.steamdeck.mc.power.SuspendHook;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.components.ChatComponent.ChatMethod;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main client entrypoint for SteamDeck Essentials.
 * Initializes Power/Eco management, Controller & Gyro engines, and Handheld GUI / OSK.
 */
public class SteamDeckEssentials implements ClientModInitializer {
    public static final String MOD_ID = "steamdeck_essentials";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyMapping toggleKey;
    public static KeyMapping cycleSizeKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[SteamDeck Essentials] Initializing mod for Minecraft 26.3 on Steam Deck...");

        // 1. Initialize configuration & detect hardware (Handheld vs. Desktop)
        ModConfig config = ModConfig.getInstance();
        PlatformDetector.getHardware();
        LOGGER.info("[SteamDeck Essentials] Device mode: {}", PlatformDetector.describe());

        // 1b. Initialize SDL Subsystems (Minecraft 26.3 only initializes SDL_INIT_VIDEO)
        try {
            SDLHints.SDL_SetHint(SDLHints.SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
            // Steam Controllers (2026 "Triton" & 2015) are only exposed as real gamepads by SDL's HIDAPI
            // driver, which is off by default. Enable it unless the user set the variable themselves.
            // (The Steam Deck has its own HIDAPI driver that is on by default.)
            if (config.enableNativeSteamControllers && System.getenv("SDL_JOYSTICK_HIDAPI_STEAM") == null) {
                SDLHints.SDL_SetHint(SDLHints.SDL_HINT_JOYSTICK_HIDAPI_STEAM, "1");
            }
            int flags = SDLInit.SDL_INIT_GAMEPAD | SDLInit.SDL_INIT_JOYSTICK | SDLInit.SDL_INIT_SENSOR;
            if ((SDLInit.SDL_WasInit(flags) & flags) != flags) {
                boolean ok = SDLInit.SDL_InitSubSystem(flags);
                LOGGER.info("[SteamDeck Essentials] Initialized SDL Gamepad/Joystick/Sensor subsystems: {}", ok);
            }
        } catch (Throwable t) {
            LOGGER.error("[SteamDeck Essentials] Error initializing SDL subsystems", t);
        }

        // 2. Register key mappings
        KeyMapping.Category category = Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.steamdeck_essentials.osk_toggle", Type.KEYBOARD, InputConstants.KEY_F12, category
        ));
        cycleSizeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.steamdeck_essentials.osk_cycle_size", Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category
        ));

        // 3. Initialize hardware subsystems & auto-install Steam Input VDF layout if possible
        GyroManager.getInstance().init();
        SuspendHook.getInstance().syncWithMode();
        // Steam Input template is only auto-installed on handhelds (never silently on a desktop PC)
        if (PlatformDetector.isHandheld() && !com.steamdeck.mc.input.SteamLayoutInstaller.isLayoutInstalled()) {
            com.steamdeck.mc.input.SteamLayoutInstaller.installLayout(false);
        }

        // 4. Client Tick Event (Input, Key presses, Telemetry)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Check OSK Toggle
            while (toggleKey.consumeClick()) {
                if (client.gui != null && client.player != null) {
                    if (client.gui.screen() == null) {
                        VirtualKeyboard.getInstance().setOpen(true);
                        client.gui.openChatScreen(ChatMethod.MESSAGE);
                    } else {
                        VirtualKeyboard.getInstance().toggle();
                    }
                }
            }

            // Check OSK Scale Cycle
            while (cycleSizeKey.consumeClick()) {
                VirtualKeyboard.getInstance().cycleScale();
                VirtualKeyboard.playClickSound();
            }

            // Start/stop handheld-only background services when the device mode changes
            SuspendHook.getInstance().syncWithMode();

            // Poll Gamepad / Controller
            SteamDeckInputManager.getInstance().poll(client);

            // Record Performance & Battery Telemetry
            com.steamdeck.mc.power.PerformanceTracker.getInstance().tick(client);
        });

        // 5. Screen Lifecycle Hooks (OSK Rendering & Input)
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            // Auto-open OSK only when opening the Chat screen
            if (config.oskAutoOpenOnInput && PlatformDetector.isHandheld() && screen instanceof ChatScreen) {
                VirtualKeyboard.getInstance().setOpen(true);
            } else {
                VirtualKeyboard.getInstance().setOpen(false);
            }

            // Render OSK, Controller Menu Hints, and AFK overlay above screen
            ScreenEvents.afterExtract(screen).register((screen1, graphics, mouseX, mouseY, tickDelta) -> {
                SteamDeckInputManager.getInstance().pollFrame(client, tickDelta);
                VirtualKeyboard.getInstance().render(graphics, screen1, mouseX, mouseY);
                com.steamdeck.mc.gui.BatteryHudRenderer.renderMenuControllerHints(graphics, screen1, screen1.width, screen1.height);
                com.steamdeck.mc.power.PowerManager.getInstance().renderAfkOverlay(graphics, screen1.width, screen1.height);
            });

            // Handle touch / mouse click
            ScreenMouseEvents.allowMouseClick(screen).register((screen1, event) -> {
                boolean handled = VirtualKeyboard.getInstance().mouseClicked(screen1, event.x(), event.y(), event.button());
                return !handled;
            });

            // Handle hardware key shortcuts while screen is active
            ScreenKeyboardEvents.allowKeyPress(screen).register((screen1, event) -> {
                if (toggleKey.matches(event)) {
                    VirtualKeyboard.getInstance().toggle();
                    VirtualKeyboard.playClickSound();
                    return false;
                }
                if (cycleSizeKey.matches(event)) {
                    VirtualKeyboard.getInstance().cycleScale();
                    VirtualKeyboard.playClickSound();
                    return false;
                }
                return true;
            });

            // Close OSK on screen dismiss
            ScreenEvents.remove(screen).register(screen1 -> {
                VirtualKeyboard.getInstance().setOpen(false);
            });
        });

        LOGGER.info("[SteamDeck Essentials] Ready! Mode: {}", PlatformDetector.describe());
    }
}
