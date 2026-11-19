package com.steamdeck.mc.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.gui.SteamDeckConfigScreen;
import com.steamdeck.mc.gui.VirtualKeyboard;
import com.steamdeck.mc.input.gyro.GyroManager;
import com.steamdeck.mc.mixin.AbstractContainerScreenAccessor;
import com.steamdeck.mc.mixin.GameModeSwitcherScreenAccessor;
import com.steamdeck.mc.mixin.KeyMappingAccessor;
import net.minecraft.client.InputType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent.ChatMethod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLJoystick;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLStdinc;

import java.nio.IntBuffer;
import java.util.EnumMap;
import java.util.Map;

/**
 * Handles Steam Deck controller input using Minecraft 26.3's native SDL3 engine.
 * Supports dynamic Action Sets (In-Game, Menus, Aiming), discrete 8-way movement,
 * and high-fidelity debug diagnostics.
 */
public class SteamDeckInputManager {
    private static final SteamDeckInputManager INSTANCE = new SteamDeckInputManager();

    private final DiscreteStickMapper stickMapper = new DiscreteStickMapper();
    private long activeGamepadHandle = 0;
    private int activeGamepadId = -1;
    private String activeDeviceName = "None";
    private long activeJoystickHandle = 0;
    private int activeJoystickId = -1;
    private ControllerKind activeKind = ControllerKind.NONE;
    private volatile long lastControllerInputTime = 0L;

    // Previous states for edge detection per button
    private final Map<DeckButton, Boolean> prevButtonStates = new EnumMap<>(DeckButton.class);

    // Steam Input Hardware Bridge state (for L4, R4, L5, R5, L3/R3 Touch mapped to keyboard keys)
    private boolean bridgeL4Down = false;
    private boolean bridgeR4Down = false;
    private boolean bridgeL5Down = false;
    private boolean bridgeR5Down = false;
    private boolean bridgeL3TouchDown = false;
    private boolean bridgeR3TouchDown = false;

    // Hotbar auto-repeat state
    private int activeHotbarDirection = 0; // -1 for PREV, +1 for NEXT
    private long hotbarHoldStartTime = 0;
    private long hotbarLastRepeatTime = 0;
    private static final long HOTBAR_INITIAL_DELAY_MS = 380; // Natural initial pause before continuous scrolling
    private static final long HOTBAR_REPEAT_INTERVAL_MS = 140; // Continuous scroll speed per slot

    // Menu & Gamemode Switcher navigation auto-repeat state (Left Stick & D-Pad)
    private int prevStickNavDx = 0;
    private int prevStickNavDy = 0;
    private long stickNavHoldStartTime = 0;
    private long stickNavLastRepeatTime = 0;
    private DeckButton activeDpadNavButton = null;
    private long dpadNavHoldStartTime = 0;
    private long dpadNavLastRepeatTime = 0;
    private static final long MENU_NAV_INITIAL_DELAY_MS = 340;
    private static final long MENU_NAV_REPEAT_INTERVAL_MS = 130;

    private boolean sneakToggled = false;
    private boolean isZooming = false;
    private int lastBowPullStage = -1;
    private int lastLowDurabilityItemSlot = -1;
    private long lastDeviceCheckTime = 0;
    private ActionSet currentActionSet = ActionSet.IN_GAME;

    public static SteamDeckInputManager getInstance() {
        return INSTANCE;
    }

    private SteamDeckInputManager() {
        for (DeckButton b : DeckButton.values()) {
            prevButtonStates.put(b, false);
        }
    }

    public boolean isZooming() {
        return isZooming;
    }

    public long getActiveGamepadHandle() {
        return activeGamepadHandle;
    }

    public long getActiveJoystickHandle() {
        return activeJoystickHandle;
    }

    public String getActiveDeviceName() {
        return activeDeviceName;
    }

    public ControllerKind getActiveKind() {
        return activeKind;
    }

    public boolean hasController() {
        return activeGamepadHandle != 0 || activeJoystickHandle != 0;
    }

    /** True while the player actually uses a controller (always on handhelds with a built-in pad). */
    public boolean isControllerRecentlyUsed() {
        if (!hasController()) return false;
        if (com.steamdeck.mc.platform.PlatformDetector.isHandheld()) return true;
        return System.currentTimeMillis() - lastControllerInputTime < 60_000L;
    }

    private void markControllerActivity() {
        lastControllerInputTime = System.currentTimeMillis();
        com.steamdeck.mc.power.PowerManager.getInstance().onUserActivity();
    }

    public ActionSet getCurrentActionSet() {
        return currentActionSet;
    }

    /**
     * Checks and connects to an SDL Gamepad or Joystick.
     */
    private void ensureGamepadConnected() {
        try {
            int flags = SDLInit.SDL_INIT_GAMEPAD | SDLInit.SDL_INIT_JOYSTICK | SDLInit.SDL_INIT_SENSOR;
            if ((SDLInit.SDL_WasInit(flags) & flags) != flags) {
                SDLHints.SDL_SetHint(SDLHints.SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
                SDLInit.SDL_InitSubSystem(flags);
            }
        } catch (Throwable t) {
            SteamDeckEssentials.LOGGER.error("[INPUT] Failed to verify SDL subsystem status", t);
        }

        if (activeGamepadHandle != 0) {
            if (SDLGamepad.SDL_GamepadConnected(activeGamepadHandle)) {
                // Switch over if a preferred controller (e.g. a Steam Controller) was plugged in later
                long nowCheck = System.currentTimeMillis();
                if (activeKind.priority() == 0 || nowCheck - lastDeviceCheckTime < 1500) return;
                lastDeviceCheckTime = nowCheck;
                int[] best = findBestGamepad();
                if (best[0] < 0 || best[0] == activeGamepadId
                        || ControllerKind.values()[best[1]].priority() >= activeKind.priority()) {
                    return;
                }
                SteamDeckEssentials.LOGGER.info("[INPUT] Switching to preferred controller (ID {})", best[0]);
                SDLGamepad.SDL_CloseGamepad(activeGamepadHandle);
                activeGamepadHandle = 0;
                activeGamepadId = -1;
                activeKind = ControllerKind.NONE;
                lastDeviceCheckTime = 0;
            } else {
                SteamDeckEssentials.LOGGER.info("[INPUT] SDL Gamepad disconnected: ID {}", activeGamepadId);
                SDLGamepad.SDL_CloseGamepad(activeGamepadHandle);
                activeGamepadHandle = 0;
                activeGamepadId = -1;
                activeDeviceName = "None";
                activeKind = ControllerKind.NONE;
            }
        }

        if (activeJoystickHandle != 0) {
            if (SDLJoystick.SDL_JoystickConnected(activeJoystickHandle)) {
                return;
            } else {
                SteamDeckEssentials.LOGGER.info("[INPUT] SDL Joystick disconnected: ID {}", activeJoystickId);
                SDLJoystick.SDL_CloseJoystick(activeJoystickHandle);
                activeJoystickHandle = 0;
                activeJoystickId = -1;
                activeDeviceName = "None";
                activeKind = ControllerKind.NONE;
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastDeviceCheckTime < 1500) return;
        lastDeviceCheckTime = now;

        // 1. Gamepad API: pick the best device instead of simply the first one
        //    (native Steam Deck / Steam Controller > Steam virtual pad > other gamepads)
        int[] best = findBestGamepad();
        if (best[0] >= 0) {
            ControllerKind bestKind = ControllerKind.values()[best[1]];
            long handle = SDLGamepad.SDL_OpenGamepad(best[0]);
            if (handle != 0) {
                activeGamepadHandle = handle;
                activeGamepadId = best[0];
                activeKind = bestKind;
                activeDeviceName = String.valueOf(SDLGamepad.SDL_GetGamepadName(handle));
                logControllerDiagnostics(handle, best[0], bestKind);
                return;
            }
        }

        // 2. Fallback: raw joystick API (devices without a gamepad mapping)
        SDLJoystick.SDL_UpdateJoysticks();
        IntBuffer joysticks = SDLJoystick.SDL_GetJoysticks();
        if (joysticks != null) {
            try {
                for (int i = 0; i < joysticks.limit(); i++) {
                    int joyId = joysticks.get(i);
                    if (SDLGamepad.SDL_IsGamepad(joyId)) continue; // handled above
                    long joyHandle = SDLJoystick.SDL_OpenJoystick(joyId);
                    if (joyHandle != 0) {
                        activeJoystickHandle = joyHandle;
                        activeJoystickId = joyId;
                        activeKind = ControllerKind.fromIds(
                                SDLJoystick.SDL_GetJoystickVendorForID(joyId) & 0xFFFF,
                                SDLJoystick.SDL_GetJoystickProductForID(joyId) & 0xFFFF);
                        activeDeviceName = String.valueOf(SDLJoystick.SDL_GetJoystickNameForID(joyId));
                        SteamDeckEssentials.LOGGER.info("[INPUT] Connected to raw SDL joystick: '{}' (ID {}, {})",
                                activeDeviceName, joyId, activeKind.getLabel());
                        return;
                    }
                }
            } finally {
                SDLStdinc.SDL_free(joysticks);
            }
        }
    }

    /** Returns {instanceId, ControllerKind ordinal} of the preferred connected gamepad, or {-1, -1}. */
    private int[] findBestGamepad() {
        SDLGamepad.SDL_UpdateGamepads();
        IntBuffer gamepads = SDLGamepad.SDL_GetGamepads();
        if (gamepads == null) return new int[]{-1, -1};
        int bestId = -1;
        ControllerKind bestKind = ControllerKind.NONE;
        try {
            // Absolute reads: SDL_free(IntBuffer) frees the address at the buffer *position*
            for (int i = 0; i < gamepads.limit(); i++) {
                int id = gamepads.get(i);
                ControllerKind kind = ControllerKind.fromIds(
                        SDLJoystick.SDL_GetJoystickVendorForID(id) & 0xFFFF,
                        SDLJoystick.SDL_GetJoystickProductForID(id) & 0xFFFF);
                if (kind.priority() < bestKind.priority()) {
                    bestKind = kind;
                    bestId = id;
                }
            }
        } finally {
            SDLStdinc.SDL_free(gamepads); // SDL allocates this array; it was leaked every 1.5 s before
        }
        return new int[]{bestId, bestId >= 0 ? bestKind.ordinal() : -1};
    }

    /**
     * Writes everything SDL knows about the controller to the log. Used to verify new hardware
     * (e.g. the 2026 Steam Controller) without a debugger: buttons, touchpads, sensors, rumble.
     */
    private void logControllerDiagnostics(long handle, int id, ControllerKind kind) {
        try {
            int vendor = SDLGamepad.SDL_GetGamepadVendor(handle) & 0xFFFF;
            int product = SDLGamepad.SDL_GetGamepadProduct(handle) & 0xFFFF;
            int touchpads = SDLGamepad.SDL_GetNumGamepadTouchpads(handle);
            boolean gyro = SDLGamepad.SDL_GamepadHasSensor(handle, org.lwjgl.sdl.SDLSensor.SDL_SENSOR_GYRO);
            boolean accel = SDLGamepad.SDL_GamepadHasSensor(handle, org.lwjgl.sdl.SDLSensor.SDL_SENSOR_ACCEL);
            int props = SDLGamepad.SDL_GetGamepadProperties(handle);
            boolean rumble = props != 0 && org.lwjgl.sdl.SDLProperties.SDL_GetBooleanProperty(
                    props, SDLGamepad.SDL_PROP_GAMEPAD_CAP_RUMBLE_BOOLEAN, false);
            StringBuilder buttons = new StringBuilder();
            int[][] extra = {
                    {SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1, 'L'}, {SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1, 'R'},
                    {SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE2, 'l'}, {SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2, 'r'},
                    {SDLGamepad.SDL_GAMEPAD_BUTTON_TOUCHPAD, 'T'}, {SDLGamepad.SDL_GAMEPAD_BUTTON_MISC1, '1'},
                    {SDLGamepad.SDL_GAMEPAD_BUTTON_MISC2, '2'}, {SDLGamepad.SDL_GAMEPAD_BUTTON_MISC3, '3'}
            };
            String[] names = {"L4", "R4", "L5", "R5", "TOUCHPAD", "MISC1", "MISC2", "MISC3"};
            for (int i = 0; i < extra.length; i++) {
                if (SDLGamepad.SDL_GamepadHasButton(handle, extra[i][0])) {
                    if (buttons.length() > 0) buttons.append(',');
                    buttons.append(names[i]);
                }
            }
            SteamDeckEssentials.LOGGER.info(
                    "[INPUT] Connected SDL gamepad '{}' (ID {}) {} [{}:{}] type={} touchpads={} gyro={} accel={} rumble={} extraButtons=[{}] steamInput={}",
                    activeDeviceName, id, kind.getLabel(),
                    String.format("%04x", vendor), String.format("%04x", product),
                    SDLGamepad.SDL_GetGamepadType(handle), touchpads, gyro, accel, rumble, buttons,
                    kind.isNativeValve() ? "nein (nativ)" : (kind == ControllerKind.STEAM_VIRTUAL ? "ja" : "-"));
        } catch (Throwable t) {
            SteamDeckEssentials.LOGGER.info("[INPUT] Connected to SDL gamepad '{}' (ID {}, {})", activeDeviceName, id, kind.getLabel());
            SteamDeckEssentials.LOGGER.debug("[INPUT] Diagnostics failed", t);
        }
    }

    /**
     * Determines current active Action Set based on gameplay context.
     * Guarantees Main Menu, loading screens, and GUIs always use MENUS.
     */
    public ActionSet evaluateActionSet(Minecraft client) {
        if (client == null) return ActionSet.MENUS;
        if (client.level == null || client.player == null) {
            return ActionSet.MENUS;
        }
        if (client.gui != null && (client.gui.screen() != null || client.gui.overlay() != null)) {
            return ActionSet.MENUS;
        }
        if (GyroManager.getInstance().isContextuallyAiming(client.player)) {
            return ActionSet.AIMING;
        }
        return ActionSet.IN_GAME;
    }

    /**
     * Polled on every client tick to process controller state.
     */
    public void poll(Minecraft client) {
        if (client == null) return;

        ensureGamepadConnected();
        this.currentActionSet = evaluateActionSet(client);
        if (this.currentActionSet == ActionSet.MENUS && this.sneakToggled) {
            this.sneakToggled = false;
        }
        updateGameplayHaptics(client);

        if (activeGamepadHandle != 0) {
            pollGamepad(client, currentActionSet);
        } else if (activeJoystickHandle != 0) {
            pollJoystickFallback(client, currentActionSet);
        } else {
            stickMapper.resetKeys(client.options);
            // Process bridge paddles even if SDL controller not yet attached
            processButton(DeckButton.L4, bridgeL4Down, client, currentActionSet);
            processButton(DeckButton.R4, bridgeR4Down, client, currentActionSet);
            processButton(DeckButton.L5, bridgeL5Down, client, currentActionSet);
            processButton(DeckButton.R5, bridgeR5Down, client, currentActionSet);
            processButton(DeckButton.LEFT_STICK_TOUCH, bridgeL3TouchDown, client, currentActionSet);
            processButton(DeckButton.RIGHT_STICK_TOUCH, bridgeR3TouchDown, client, currentActionSet);
        }
    }

    /**
     * Intercepts keyboard events sent from Steam Input representing back pedals (L4, R4, L5, R5).
     * Returns true if the key matches a bridge mapping and was processed as a controller button.
     */
    public boolean handleBridgeKeyEvent(int keyCode, int action) {
        if (keyCode <= 0) return false;

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gui != null && client.gui.screen() instanceof com.steamdeck.mc.gui.SteamDeckConfigScreen configScreen) {
            if (configScreen.isListening()) {
                return false;
            }
        }

        ModConfig config = ModConfig.getInstance();
        DeckButton targetButton = null;
        boolean isDown = (action != 0);

        if (keyCode == config.steamBridgeKeyL4) {
            targetButton = DeckButton.L4;
            bridgeL4Down = isDown;
        } else if (keyCode == config.steamBridgeKeyR4) {
            targetButton = DeckButton.R4;
            bridgeR4Down = isDown;
        } else if (keyCode == config.steamBridgeKeyL5) {
            targetButton = DeckButton.L5;
            bridgeL5Down = isDown;
        } else if (keyCode == config.steamBridgeKeyR5) {
            targetButton = DeckButton.R5;
            bridgeR5Down = isDown;
        } else if (config.steamBridgeKeyL3Touch > 0 && keyCode == config.steamBridgeKeyL3Touch) {
            targetButton = DeckButton.LEFT_STICK_TOUCH;
            bridgeL3TouchDown = isDown;
        } else if (config.steamBridgeKeyR3Touch > 0 && keyCode == config.steamBridgeKeyR3Touch) {
            targetButton = DeckButton.RIGHT_STICK_TOUCH;
            bridgeR3TouchDown = isDown;
        }

        if (targetButton == null) {
            return false;
        }

        markControllerActivity();
        if (client != null) {
            processButton(targetButton, isDown, client, currentActionSet);
        }
        return true;
    }

    private void pollGamepad(Minecraft client, ActionSet set) {
        SDLGamepad.SDL_UpdateGamepads();

        LocalPlayer player = client.player;
        boolean hasScreen = (client.gui != null && client.gui.screen() != null);

        // 1. Left Stick -> discrete 8-way movement in-game, or menu/gamemode navigation in screens
        short rawLx = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX);
        short rawLy = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY);
        float leftX = rawLx < 0 ? (float) rawLx / 32768.0f : (float) rawLx / 32767.0f;
        float leftY = rawLy < 0 ? (float) rawLy / 32768.0f : (float) rawLy / 32767.0f;

        if (!hasScreen) {
            if (Math.hypot(leftX, leftY) > ModConfig.getInstance().stickDeadzone) {
                markControllerActivity();
            }
            stickMapper.updateStickInput(client, leftX, leftY);
            resetMenuStickNav();
        } else {
            stickMapper.resetKeys(client.options);
            if (!VirtualKeyboard.getInstance().isOpen()) {
                updateStickMenuNavigation(client, leftX, leftY);
            } else {
                resetMenuStickNav();
            }
        }

        // 2. Right Stick: Radial Menu in normal gameplay, Precision Aiming when actively drawing Bow/Spyglass
        if (player != null && !hasScreen && client.mouseHandler.isMouseGrabbed()) {
            short rawRx = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX);
            short rawRy = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY);
            float rightX = rawRx < 0 ? (float) rawRx / 32768.0f : (float) rawRx / 32767.0f;
            float rightY = rawRy < 0 ? (float) rawRy / 32768.0f : (float) rawRy / 32767.0f;

            boolean aimingBowOrTool = GyroManager.getInstance().isContextuallyAiming(player);
            if (aimingBowOrTool || !ModConfig.getInstance().enableRightStickRadialMenu) {
                // Camera control from the right stick runs per frame in pollCameraFrame()
                com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().cancel();
            } else {
                if (Math.hypot(rightX, rightY) > 0.25f) {
                    markControllerActivity();
                }
                com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().updateStick(client, rightX, rightY);
            }
        } else {
            com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().cancel();
        }

        // 3. Triggers (analog to digital threshold > 30%)
        short rawRt = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER);
        short rawLt = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER);
        processButton(DeckButton.RT, rawRt > 9830, client, set);
        processButton(DeckButton.LT, rawLt > 9830, client, set);

        // 4. Face Buttons
        processButton(DeckButton.A, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH), client, set);
        processButton(DeckButton.B, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_EAST), client, set);
        processButton(DeckButton.X, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_WEST), client, set);
        processButton(DeckButton.Y, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH), client, set);

        // 5. Bumpers & Back Pedals
        processButton(DeckButton.LB, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER), client, set);
        processButton(DeckButton.RB, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER), client, set);
        processButton(DeckButton.L4, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1) || bridgeL4Down, client, set);
        processButton(DeckButton.R4, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1) || bridgeR4Down, client, set);
        processButton(DeckButton.L5, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE2) || bridgeL5Down, client, set);
        processButton(DeckButton.R5, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2) || bridgeR5Down, client, set);

        // 6. Sticks & Trackpad clicks
        processButton(DeckButton.L3, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK), client, set);
        processButton(DeckButton.R3, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK), client, set);
        processButton(DeckButton.LEFT_TRACKPAD, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_TOUCHPAD), client, set);
        processButton(DeckButton.RIGHT_TRACKPAD, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_MISC3), client, set);

        // 7. D-Pad
        processButton(DeckButton.DPAD_UP, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP), client, set);
        processButton(DeckButton.DPAD_DOWN, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN), client, set);
        processButton(DeckButton.DPAD_LEFT, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT), client, set);
        processButton(DeckButton.DPAD_RIGHT, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT), client, set);

        // 8. Start & Select
        processButton(DeckButton.START, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_START), client, set);
        processButton(DeckButton.SELECT, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_BACK), client, set);

        // 9. Joystick Capacitive Touch
        processButton(DeckButton.LEFT_STICK_TOUCH, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_MISC1) || bridgeL3TouchDown, client, set);
        processButton(DeckButton.RIGHT_STICK_TOUCH, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_MISC2) || bridgeR3TouchDown, client, set);
    }

    private void pollJoystickFallback(Minecraft client, ActionSet set) {
        SDLJoystick.SDL_UpdateJoysticks();

        LocalPlayer player = client.player;
        boolean hasScreen = (client.gui != null && client.gui.screen() != null);

        short rawLx = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 0);
        short rawLy = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 1);
        float leftX = rawLx < 0 ? (float) rawLx / 32768.0f : (float) rawLx / 32767.0f;
        float leftY = rawLy < 0 ? (float) rawLy / 32768.0f : (float) rawLy / 32767.0f;

        if (!hasScreen) {
            if (Math.hypot(leftX, leftY) > ModConfig.getInstance().stickDeadzone) {
                markControllerActivity();
            }
            stickMapper.updateStickInput(client, leftX, leftY);
            resetMenuStickNav();
        } else {
            stickMapper.resetKeys(client.options);
            if (!VirtualKeyboard.getInstance().isOpen()) {
                updateStickMenuNavigation(client, leftX, leftY);
            } else {
                resetMenuStickNav();
            }
        }

        if (player != null && !hasScreen && client.mouseHandler.isMouseGrabbed()) {
            short rawRx = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 2);
            short rawRy = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 3);
            float rightX = rawRx < 0 ? (float) rawRx / 32768.0f : (float) rawRx / 32767.0f;
            float rightY = rawRy < 0 ? (float) rawRy / 32768.0f : (float) rawRy / 32767.0f;

            boolean aimingBowOrTool = GyroManager.getInstance().isContextuallyAiming(player);
            if (aimingBowOrTool || !ModConfig.getInstance().enableRightStickRadialMenu) {
                // Camera control from the right stick runs per frame in pollCameraFrame()
                com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().cancel();
            } else {
                if (Math.hypot(rightX, rightY) > 0.25f) {
                    markControllerActivity();
                }
                com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().updateStick(client, rightX, rightY);
            }
        } else {
            com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().cancel();
        }

        // Triggers (axes 4 and 5)
        short rawLt = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 4);
        short rawRt = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 5);
        processButton(DeckButton.LT, rawLt > 9830, client, set);
        processButton(DeckButton.RT, rawRt > 9830, client, set);

        // Buttons
        processButton(DeckButton.A, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 0), client, set);
        processButton(DeckButton.B, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 1), client, set);
        processButton(DeckButton.X, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 2), client, set);
        processButton(DeckButton.Y, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 3), client, set);
        processButton(DeckButton.LB, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 4), client, set);
        processButton(DeckButton.RB, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 5), client, set);
        processButton(DeckButton.SELECT, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 6), client, set);
        processButton(DeckButton.START, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 7), client, set);
        processButton(DeckButton.L3, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 9), client, set);
        processButton(DeckButton.R3, SDLJoystick.SDL_GetJoystickButton(activeJoystickHandle, 10), client, set);

        // D-Pad Hat in Joystick Fallback
        byte hat = SDLJoystick.SDL_GetJoystickHat(activeJoystickHandle, 0);
        processButton(DeckButton.DPAD_UP, (hat & SDLJoystick.SDL_HAT_UP) != 0, client, set);
        processButton(DeckButton.DPAD_DOWN, (hat & SDLJoystick.SDL_HAT_DOWN) != 0, client, set);
        processButton(DeckButton.DPAD_LEFT, (hat & SDLJoystick.SDL_HAT_LEFT) != 0, client, set);
        processButton(DeckButton.DPAD_RIGHT, (hat & SDLJoystick.SDL_HAT_RIGHT) != 0, client, set);

        // Back Paddles via Steam Input Bridge
        processButton(DeckButton.L4, bridgeL4Down, client, set);
        processButton(DeckButton.R4, bridgeR4Down, client, set);
        processButton(DeckButton.L5, bridgeL5Down, client, set);
        processButton(DeckButton.R5, bridgeR5Down, client, set);

        // Joystick Capacitive Touch
        processButton(DeckButton.LEFT_STICK_TOUCH, bridgeL3TouchDown, client, set);
        processButton(DeckButton.RIGHT_STICK_TOUCH, bridgeR3TouchDown, client, set);
    }

    private void processButton(DeckButton button, boolean isDown, Minecraft client, ActionSet set) {
        if (isDown) {
            markControllerActivity();
        }

        boolean wasDown = prevButtonStates.getOrDefault(button, false);
        prevButtonStates.put(button, isDown);

        // Virtual Keyboard Dual-Trackpad / Dual-Cursor Typing
        if (com.steamdeck.mc.gui.VirtualKeyboard.getInstance().isOpen() && isDown && !wasDown) {
            net.minecraft.client.gui.screens.Screen screen = (client.gui != null) ? client.gui.screen() : null;
            if (screen != null) {
                if (button == DeckButton.LT || button == DeckButton.LEFT_TRACKPAD || button == DeckButton.LB) {
                    if (com.steamdeck.mc.gui.VirtualKeyboard.getInstance().triggerLeftCursorClick(screen)) {
                        return;
                    }
                } else if (button == DeckButton.RT || button == DeckButton.RIGHT_TRACKPAD || button == DeckButton.RB) {
                    if (com.steamdeck.mc.gui.VirtualKeyboard.getInstance().triggerRightCursorClick(screen)) {
                        return;
                    }
                }
            }
        }

        // Right Stick Click (R3) cycles Radial Menu pages when Radial Menu is enabled in-game
        boolean hasScreen = (client.gui != null && client.gui.screen() != null);
        if (button == DeckButton.R3 && !hasScreen && ModConfig.getInstance().enableRightStickRadialMenu) {
            if (isDown && !wasDown) {
                com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().cyclePage();
            }
            return;
        }

        // 2. Confirm with A in GameModeSwitcherScreen
        if (hasScreen && !VirtualKeyboard.getInstance().isOpen()) {
            Screen screen = client.gui.screen();

            // 1. D-Pad Up/Down/Left/Right in Menus, Inventory & Gamemode Switcher
            if (button == DeckButton.DPAD_UP || button == DeckButton.DPAD_DOWN
                    || button == DeckButton.DPAD_LEFT || button == DeckButton.DPAD_RIGHT) {
                ActionBinding dpadBind = ModConfig.getInstance().getBinding(set, button);
                if (screen instanceof GameModeSwitcherScreen || dpadBind.getAction() == DeckAction.NONE) {
                    int dx = (button == DeckButton.DPAD_LEFT) ? -1 : (button == DeckButton.DPAD_RIGHT ? 1 : 0);
                    int dy = (button == DeckButton.DPAD_UP) ? -1 : (button == DeckButton.DPAD_DOWN ? 1 : 0);
                    handleDpadMenuNavigation(client, button, dx, dy, isDown, wasDown);
                    return;
                }
            }

            // 2. Confirm with A in GameModeSwitcherScreen
            if (button == DeckButton.A && screen instanceof GameModeSwitcherScreen) {
                if (isDown && !wasDown) {
                    ((GameModeSwitcherScreenAccessor) screen).invokeSwitchToHoveredGameMode();
                    VirtualKeyboard.playClickSound();
                    com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(1);
                    client.gui.setScreen(null);
                }
                return;
            }
        }

        ActionBinding binding = ModConfig.getInstance().getBinding(set, button);
        if (binding.getAction() == DeckAction.NONE) return;

        if (binding.isCustomKey()) {
            int keyCode = binding.getCustomKeyCode();
            try {
                com.mojang.blaze3d.platform.InputConstants.Key key = com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(keyCode);
                KeyMapping.set(key, isDown);
                if (isDown && !wasDown) {
                    KeyMapping.click(key);
                }
                if (client.keyboardHandler != null && client.getWindow() != null && isDown != wasDown) {
                    client.keyboardHandler.keyPress(client.getWindow().handle(), isDown ? 1 : 0, new net.minecraft.client.input.KeyEvent(keyCode, keyCode, 0));
                }
            } catch (Throwable t) {
                SteamDeckEssentials.LOGGER.warn("Failed to dispatch custom key event", t);
            }
            return;
        }

        DeckAction action = binding.getAction();

        switch (action) {
            case RADIAL_MENU_CYCLE -> {
                if (isDown && !wasDown) {
                    com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().cyclePage();
                }
            }
            case JUMP -> setKeybindState(client.options.keyJump, isDown, wasDown);
            case ATTACK -> setKeybindState(client.options.keyAttack, isDown, wasDown);
            case USE -> setKeybindState(client.options.keyUse, isDown, wasDown);
            case DROP -> setKeybindState(client.options.keyDrop, isDown, wasDown);
            case INVENTORY -> setKeybindState(client.options.keyInventory, isDown, wasDown);
            case SWAP_OFFHAND -> setKeybindState(client.options.keySwapOffhand, isDown, wasDown);
            case SPRINT -> setKeybindState(client.options.keySprint, isDown, wasDown);
            case SNEAK -> setKeybindState(client.options.keyShift, isDown, wasDown);
            case SNEAK_TOGGLE -> {
                if (isDown && !wasDown) {
                    sneakToggled = !sneakToggled;
                    client.options.keyShift.setDown(sneakToggled);
                }
            }
            case ZOOM -> {
                this.isZooming = isDown;
            }
            case PICK_BLOCK -> setKeybindState(client.options.keyPickItem, isDown, wasDown);
            case TOGGLE_PERSPECTIVE -> setKeybindState(client.options.keyTogglePerspective, isDown, wasDown);
            case TOGGLE_DEBUG -> {
                if (isDown && !wasDown && client.debugEntries != null) {
                    client.debugEntries.toggleDebugOverlay();
                }
            }
            case OPEN_CHAT -> {
                if (isDown && !wasDown && client.gui != null) {
                    client.gui.openChatScreen(ChatMethod.MESSAGE);
                }
            }
            case HOTBAR_PREV -> handleHotbarAction(client, -1, isDown, wasDown);
            case HOTBAR_NEXT -> handleHotbarAction(client, 1, isDown, wasDown);
            case PAUSE -> {
                if (isDown && !wasDown) {
                    if (hasScreen) {
                        client.gui.screen().onClose();
                    } else {
                        client.setLastInputType(InputType.KEYBOARD_ARROW);
                        client.pauseGame(false);
                    }
                }
            }
            case CLOSE_SCREEN -> {
                if (isDown && !wasDown) {
                    if (VirtualKeyboard.getInstance().isOpen()) {
                        VirtualKeyboard.getInstance().setOpen(false);
                    } else if (hasScreen) {
                        client.gui.screen().onClose();
                    }
                }
            }
            case MOUSE_LEFT_CLICK -> {
                if (hasScreen && client.getWindow() != null) {
                    if (isDown != wasDown) {
                        Screen screen = client.gui.screen();
                        if (!(screen instanceof AbstractContainerScreen) && !(screen instanceof SteamDeckConfigScreen)
                                && screen != null && screen.getFocused() != null) {
                            double mx = client.mouseHandler.xpos() * (double) client.getWindow().getGuiScaledWidth()
                                    / (double) Math.max(1, client.getWindow().getWidth());
                            double my = client.mouseHandler.ypos() * (double) client.getWindow().getGuiScaledHeight()
                                    / (double) Math.max(1, client.getWindow().getHeight());
                            boolean mouseHoveringWidget = client.getLastInputType().isMouse() && screen.getChildAt(mx, my).isPresent();
                            if (!mouseHoveringWidget) {
                                if (isDown) {
                                    screen.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
                                } else {
                                    screen.keyReleased(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
                                }
                                break;
                            }
                        }
                        client.mouseHandler.onButton(client.getWindow().handle(), new MouseButtonInfo(SDLMouse.SDL_BUTTON_LEFT, 0), isDown ? 1 : 0);
                    }
                } else {
                    setKeybindState(client.options.keyAttack, isDown, wasDown);
                }
            }
            case MOUSE_RIGHT_CLICK -> {
                if (hasScreen && client.getWindow() != null) {
                    if (isDown != wasDown) {
                        client.mouseHandler.onButton(client.getWindow().handle(), new MouseButtonInfo(SDLMouse.SDL_BUTTON_RIGHT, 0), isDown ? 1 : 0);
                    }
                } else {
                    setKeybindState(client.options.keyUse, isDown, wasDown);
                }
            }
            case MOUSE_MIDDLE_CLICK -> {
                if (hasScreen && client.getWindow() != null) {
                    if (isDown != wasDown) {
                        client.mouseHandler.onButton(client.getWindow().handle(), new MouseButtonInfo(SDLMouse.SDL_BUTTON_MIDDLE, 0), isDown ? 1 : 0);
                    }
                } else {
                    setKeybindState(client.options.keyPickItem, isDown, wasDown);
                }
            }
            case MOUSE_QUICK_MOVE -> {
                if (hasScreen && client.getWindow() != null) {
                    if (isDown != wasDown) {
                        client.mouseHandler.onButton(client.getWindow().handle(), new MouseButtonInfo(SDLMouse.SDL_BUTTON_LEFT, InputConstants.MOD_SHIFT), isDown ? 1 : 0);
                    }
                }
            }
        }
    }

    private void resetMenuStickNav() {
        this.prevStickNavDx = 0;
        this.prevStickNavDy = 0;
    }

    private void updateStickMenuNavigation(Minecraft client, float leftX, float leftY) {
        int dx = 0;
        int dy = 0;
        if (Math.hypot(leftX, leftY) > 0.45f) {
            markControllerActivity();
            if (Math.abs(leftX) > Math.abs(leftY)) {
                dx = leftX > 0 ? 1 : -1;
            } else {
                dy = leftY > 0 ? 1 : -1;
            }
        }

        long now = System.currentTimeMillis();
        if (dx != 0 || dy != 0) {
            if (dx != this.prevStickNavDx || dy != this.prevStickNavDy) {
                stepMenuNavigation(client, dx, dy);
                this.prevStickNavDx = dx;
                this.prevStickNavDy = dy;
                this.stickNavHoldStartTime = now;
                this.stickNavLastRepeatTime = now;
            } else if (now - this.stickNavHoldStartTime >= MENU_NAV_INITIAL_DELAY_MS) {
                if (now - this.stickNavLastRepeatTime >= MENU_NAV_REPEAT_INTERVAL_MS) {
                    stepMenuNavigation(client, dx, dy);
                    this.stickNavLastRepeatTime = now;
                }
            }
        } else {
            this.prevStickNavDx = 0;
            this.prevStickNavDy = 0;
        }
    }

    private void handleDpadMenuNavigation(Minecraft client, DeckButton button, int dx, int dy, boolean isDown, boolean wasDown) {
        long now = System.currentTimeMillis();
        if (isDown && !wasDown) {
            stepMenuNavigation(client, dx, dy);
            this.activeDpadNavButton = button;
            this.dpadNavHoldStartTime = now;
            this.dpadNavLastRepeatTime = now;
        } else if (isDown && this.activeDpadNavButton == button) {
            if (now - this.dpadNavHoldStartTime >= MENU_NAV_INITIAL_DELAY_MS) {
                if (now - this.dpadNavLastRepeatTime >= MENU_NAV_REPEAT_INTERVAL_MS) {
                    stepMenuNavigation(client, dx, dy);
                    this.dpadNavLastRepeatTime = now;
                }
            }
        } else if (!isDown && wasDown && this.activeDpadNavButton == button) {
            this.activeDpadNavButton = null;
        }
    }

    private void stepMenuNavigation(Minecraft client, int dx, int dy) {
        if (client == null || client.gui == null) return;
        Screen screen = client.gui.screen();
        if (screen == null) return;

        if (screen instanceof GameModeSwitcherScreen) {
            int step = (dx != 0) ? dx : dy;
            if (step == 0) return;
            // 4 gamemode slots: 1 step forward = right (+1), 3 steps forward = left (-1 mod 4)
            int count = (step > 0) ? 1 : 3;
            KeyEvent switchEvent = new KeyEvent(InputConstants.KEY_F4, InputConstants.KEY_F4, 0);
            for (int i = 0; i < count; i++) {
                screen.keyPressed(switchEvent);
            }
            VirtualKeyboard.playClickSound();
            com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(step > 0 ? 1 : -1);
            return;
        }

        if (screen instanceof SteamDeckConfigScreen configScreen) {
            configScreen.navigateController(dx, dy);
            com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(dx != 0 ? dx : dy);
            return;
        }

        if (screen instanceof AbstractContainerScreen<?> containerScreen && ModConfig.getInstance().enableInventorySlotSnapping) {
            if (snapToNearestInventorySlot(client, containerScreen, dx, dy)) {
                com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(dx != 0 ? dx : dy);
            }
            return;
        }

        if (!(screen instanceof AbstractContainerScreen)) {
            client.setLastInputType(InputType.KEYBOARD_ARROW);
            KeyEvent arrowEvent;
            if (dx < 0) {
                arrowEvent = new KeyEvent(InputConstants.KEY_LEFT, InputConstants.KEYCODE_LEFT, 0);
            } else if (dx > 0) {
                arrowEvent = new KeyEvent(InputConstants.KEY_RIGHT, InputConstants.KEYCODE_RIGHT, 0);
            } else if (dy < 0) {
                arrowEvent = new KeyEvent(InputConstants.KEY_UP, InputConstants.KEYCODE_UP, 0);
            } else {
                arrowEvent = new KeyEvent(InputConstants.KEY_DOWN, InputConstants.KEYCODE_DOWN, 0);
            }
            screen.keyPressed(arrowEvent);
            if (screen.getFocused() == null) {
                screen.keyPressed(new KeyEvent(InputConstants.KEY_TAB, InputConstants.KEYCODE_TAB, 0));
            }
            com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(dx != 0 ? dx : dy);
        }
    }

    private boolean snapToNearestInventorySlot(Minecraft client, AbstractContainerScreen<?> containerScreen, int dx, int dy) {
        if (client == null || client.getWindow() == null || containerScreen.getMenu() == null) return false;
        var slots = containerScreen.getMenu().slots;
        if (slots == null || slots.isEmpty()) return false;

        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) containerScreen;
        int leftPos = acc.getLeftPos();
        int topPos = acc.getTopPos();

        com.mojang.blaze3d.platform.Window win = client.getWindow();
        double curGuiX = client.mouseHandler.xpos() * (double) win.getGuiScaledWidth() / (double) Math.max(1, win.getWidth());
        double curGuiY = client.mouseHandler.ypos() * (double) win.getGuiScaledHeight() / (double) Math.max(1, win.getHeight());

        Slot currentSlot = acc.getHoveredSlot();
        if (currentSlot != null) {
            curGuiX = leftPos + currentSlot.x + 8;
            curGuiY = topPos + currentSlot.y + 8;
        }

        Slot bestSlot = null;
        double bestScore = Double.MAX_VALUE;

        for (Slot slot : slots) {
            if (slot == null || !slot.isActive() || slot == currentSlot) continue;
            double sx = leftPos + slot.x + 8;
            double sy = topPos + slot.y + 8;
            double relX = sx - curGuiX;
            double relY = sy - curGuiY;

            // Project onto navigation direction (dx, dy)
            double primary = relX * dx + relY * dy;
            if (primary <= 2.0) continue; // Must be in the requested direction

            double perpendicular = Math.abs(relX * dy - relY * dx);
            // Weight perpendicular distance higher so row/column alignment is strongly preferred
            double score = primary + perpendicular * 2.6;
            if (score < bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        // Fallback: if no slot in that direction, snap to first active slot if none hovered
        if (bestSlot == null && currentSlot == null) {
            for (Slot slot : slots) {
                if (slot != null && slot.isActive()) {
                    bestSlot = slot;
                    break;
                }
            }
        }

        if (bestSlot != null) {
            int targetGuiX = leftPos + bestSlot.x + 8;
            int targetGuiY = topPos + bestSlot.y + 8;
            float winX = (float) targetGuiX * (float) win.getWidth() / (float) Math.max(1, win.getGuiScaledWidth());
            float winY = (float) targetGuiY * (float) win.getHeight() / (float) Math.max(1, win.getGuiScaledHeight());
            SDLMouse.SDL_WarpMouseInWindow(win.handle(), winX, winY);
            client.mouseHandler.onMove(win.handle(), winX, winY, 0.0, 0.0);
            acc.setHoveredSlot(bestSlot);
            return true;
        }
        return false;
    }

    private void handleHotbarAction(Minecraft client, int direction, boolean isDown, boolean wasDown) {
        if (client == null || client.player == null) return;
        long now = System.currentTimeMillis();

        if (isDown && !wasDown) {
            // Initial press: change slot immediately
            stepHotbar(client, direction);
            this.activeHotbarDirection = direction;
            this.hotbarHoldStartTime = now;
            this.hotbarLastRepeatTime = now;
        } else if (isDown && this.activeHotbarDirection == direction) {
            // Continuous hold: wait initial delay then scroll repeatedly
            long heldTime = now - this.hotbarHoldStartTime;
            if (heldTime >= HOTBAR_INITIAL_DELAY_MS) {
                if (now - this.hotbarLastRepeatTime >= HOTBAR_REPEAT_INTERVAL_MS) {
                    stepHotbar(client, direction);
                    this.hotbarLastRepeatTime = now;
                }
            }
        } else if (!isDown && wasDown && this.activeHotbarDirection == direction) {
            this.activeHotbarDirection = 0;
        }
    }

    private void stepHotbar(Minecraft client, int direction) {
        if (client == null || client.player == null) return;
        int current = client.player.getInventory().getSelectedSlot();
        int next = (current + direction + 9) % 9;
        client.player.getInventory().setSelectedSlot(next);
        com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(direction);
    }

    private void setKeybindState(KeyMapping key, boolean isDown, boolean wasDown) {
        if (key == null) return;
        if (isDown != wasDown) {
            key.setDown(isDown);
            if (isDown) {
                try {
                    KeyMappingAccessor accessor = (KeyMappingAccessor) key;
                    accessor.setClickCount(accessor.getClickCount() + 1);
                } catch (Throwable ignored) {}
            }
        } else if (isDown) {
            key.setDown(true);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Per-frame camera (right stick & native trackpad)
    // ---------------------------------------------------------------------------------------

    /** Entity.turn() multiplies by 0.15 to get degrees. */
    private static final double TURN_UNITS_PER_DEGREE = 1.0 / 0.15;
    private static final float AIM_STICK_SPEED_FACTOR = 0.35f;
    private static final float TRACKPAD_DEGREES_PER_PAD_WIDTH = 90.0f;

    private final java.nio.ByteBuffer touchDown = org.lwjgl.BufferUtils.createByteBuffer(1);
    private final java.nio.FloatBuffer touchX = org.lwjgl.BufferUtils.createFloatBuffer(1);
    private final java.nio.FloatBuffer touchY = org.lwjgl.BufferUtils.createFloatBuffer(1);
    private final java.nio.FloatBuffer touchPressure = org.lwjgl.BufferUtils.createFloatBuffer(1);
    private boolean trackpadWasDown = false;
    private float trackpadLastX = 0.0f;
    private float trackpadLastY = 0.0f;
    private long lastCameraFrameNanos = 0L;

    /**
     * Camera control, called once per rendered frame from the HUD (so it is smooth at 60/90/144 Hz
     * and frame-rate independent). Previously the right stick turned the camera in the 20 Hz client
     * tick with a fixed step (max ~10 deg/s), which was choppy and far too slow for stick-only play.
     * <ul>
     *     <li>Right stick: camera when the radial menu is off; precision aim while drawing a bow etc.</li>
     *     <li>Right trackpad: mouse-like camera for native Valve controllers (no Steam Input running,
     *     e.g. Steam Controller 2026 on a desktop without Steam). With Steam Input the trackpad is
     *     already a mouse, so this stays off there.</li>
     * </ul>
     */
    public void pollCameraFrame(Minecraft client) {
        long now = System.nanoTime();
        float dt = lastCameraFrameNanos == 0L ? 0.0f : Math.min(0.1f, (now - lastCameraFrameNanos) / 1_000_000_000.0f);
        lastCameraFrameNanos = now;

        if (client == null || dt <= 0.0f) return;
        LocalPlayer player = client.player;
        boolean hasScreen = client.gui != null && client.gui.screen() != null;
        if (player == null || hasScreen || !client.mouseHandler.isMouseGrabbed() || !hasController()) {
            trackpadWasDown = false;
            return;
        }

        ModConfig config = ModConfig.getInstance();
        if (activeGamepadHandle != 0) {
            SDLGamepad.SDL_UpdateGamepads();
        } else {
            SDLJoystick.SDL_UpdateJoysticks();
        }

        // 1. Right stick
        float[] stick = readRightStick();
        boolean aiming = GyroManager.getInstance().isContextuallyAiming(player);
        if (aiming || !config.enableRightStickRadialMenu) {
            float deadzone = aiming ? 0.02f : Math.max(0.05f, config.stickDeadzone * 0.5f);
            float mag = (float) Math.hypot(stick[0], stick[1]);
            if (mag > deadzone) {
                markControllerActivity();
                // Rescale past the deadzone and apply a quadratic curve for fine control near the center
                float norm = Math.min(1.0f, (mag - deadzone) / (1.0f - deadzone));
                float curved = norm * norm;
                float speed = config.rightStickCameraSpeed * (aiming ? AIM_STICK_SPEED_FACTOR : 1.0f);
                float degX = stick[0] / mag * curved * speed * dt;
                float degY = stick[1] / mag * curved * speed * dt;
                player.turn(degX * TURN_UNITS_PER_DEGREE, degY * TURN_UNITS_PER_DEGREE);
            }
        }

        // 2. Native right trackpad
        if (config.nativeTrackpadCamera && activeKind.isNativeValve() && activeGamepadHandle != 0) {
            pollNativeTrackpadCamera(player, config);
        } else {
            trackpadWasDown = false;
        }
    }

    private void pollNativeTrackpadCamera(LocalPlayer player, ModConfig config) {
        int pads = SDLGamepad.SDL_GetNumGamepadTouchpads(activeGamepadHandle);
        if (pads <= 0) {
            trackpadWasDown = false;
            return;
        }
        // Two pads: index 1 is the right one. A single reported pad is used as-is.
        int padIndex = pads >= 2 ? 1 : 0;

        touchDown.clear();
        touchX.clear();
        touchY.clear();
        touchPressure.clear();
        boolean ok = SDLGamepad.SDL_GetGamepadTouchpadFinger(activeGamepadHandle, padIndex, 0,
                touchDown, touchX, touchY, touchPressure);
        boolean down = ok && touchDown.get(0) != 0;
        if (!down) {
            trackpadWasDown = false;
            return;
        }

        float x = touchX.get(0);
        float y = touchY.get(0);
        if (trackpadWasDown) {
            float dx = x - trackpadLastX;
            float dy = y - trackpadLastY;
            if (dx != 0.0f || dy != 0.0f) {
                markControllerActivity();
                float scale = TRACKPAD_DEGREES_PER_PAD_WIDTH * config.trackpadSensitivity;
                player.turn(dx * scale * TURN_UNITS_PER_DEGREE, dy * scale * TURN_UNITS_PER_DEGREE);
            }
        }
        trackpadLastX = x;
        trackpadLastY = y;
        trackpadWasDown = true;
    }

    /** Right stick in [-1, 1] from the gamepad or the raw joystick fallback (axes 2/3). */
    private float[] readRightStick() {
        short rawRx;
        short rawRy;
        if (activeGamepadHandle != 0) {
            rawRx = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX);
            rawRy = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY);
        } else if (activeJoystickHandle != 0) {
            rawRx = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 2);
            rawRy = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 3);
        } else {
            return new float[]{0.0f, 0.0f};
        }
        return new float[]{
                rawRx < 0 ? rawRx / 32768.0f : rawRx / 32767.0f,
                rawRy < 0 ? rawRy / 32768.0f : rawRy / 32767.0f
        };
    }

    /**
     * Per-frame high-refresh update (60/90 Hz) for smooth OSK dual-cursor reticles.
     */
    public void pollFrame(Minecraft client, float frameDeltaTicks) {
        if (client == null || !VirtualKeyboard.getInstance().isOpen()) return;

        float speedScale = Math.max(0.15f, Math.min(2.5f, frameDeltaTicks));
        if (activeGamepadHandle != 0) {
            SDLGamepad.SDL_UpdateGamepads();
            short rawLx = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX);
            short rawLy = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY);
            float leftX = rawLx < 0 ? (float) rawLx / 32768.0f : (float) rawLx / 32767.0f;
            float leftY = rawLy < 0 ? (float) rawLy / 32768.0f : (float) rawLy / 32767.0f;
            if (Math.hypot(leftX, leftY) > 0.15f) {
                VirtualKeyboard.getInstance().moveLeftCursor(leftX * 6.5f * speedScale, leftY * 6.5f * speedScale);
                markControllerActivity();
            }

            short rawRx = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX);
            short rawRy = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY);
            float rightX = rawRx < 0 ? (float) rawRx / 32768.0f : (float) rawRx / 32767.0f;
            float rightY = rawRy < 0 ? (float) rawRy / 32768.0f : (float) rawRy / 32767.0f;
            if (Math.hypot(rightX, rightY) > 0.15f) {
                VirtualKeyboard.getInstance().moveRightCursor(rightX * 6.5f * speedScale, rightY * 6.5f * speedScale);
                markControllerActivity();
            }
        } else if (activeJoystickHandle != 0) {
            SDLJoystick.SDL_UpdateJoysticks();
            short rawLx = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 0);
            short rawLy = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 1);
            float leftX = rawLx < 0 ? (float) rawLx / 32768.0f : (float) rawLx / 32767.0f;
            float leftY = rawLy < 0 ? (float) rawLy / 32768.0f : (float) rawLy / 32767.0f;
            if (Math.hypot(leftX, leftY) > 0.15f) {
                VirtualKeyboard.getInstance().moveLeftCursor(leftX * 6.5f * speedScale, leftY * 6.5f * speedScale);
                markControllerActivity();
            }

            short rawRx = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 2);
            short rawRy = SDLJoystick.SDL_GetJoystickAxis(activeJoystickHandle, 3);
            float rightX = rawRx < 0 ? (float) rawRx / 32768.0f : (float) rawRx / 32767.0f;
            float rightY = rawRy < 0 ? (float) rawRy / 32768.0f : (float) rawRy / 32767.0f;
            if (Math.hypot(rightX, rightY) > 0.15f) {
                VirtualKeyboard.getInstance().moveRightCursor(rightX * 6.5f * speedScale, rightY * 6.5f * speedScale);
                markControllerActivity();
            }
        }
    }

    private void updateGameplayHaptics(Minecraft client) {
        if (!ModConfig.getInstance().enableGameplayHaptics || client == null || client.player == null) {
            lastBowPullStage = -1;
            return;
        }

        LocalPlayer player = client.player;
        // 1. Bow / Crossbow / Trident draw tension haptics
        if (player.isUsingItem() && GyroManager.getInstance().isContextuallyAiming(player)) {
            int ticksUsed = player.getTicksUsingItem();
            int stage = Math.min(3, ticksUsed / 7);
            if (stage > lastBowPullStage && stage > 0) {
                lastBowPullStage = stage;
                short strength = (stage == 3) ? (short) 42000 : (short) (20000 + stage * 6000);
                int duration = (stage == 3) ? 45 : 22;
                com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHaptic(true, true, strength, duration);
            }
        } else {
            lastBowPullStage = -1;
        }

        // 2. Low tool durability warning haptic (< 10% durability remaining)
        var mainHand = player.getMainHandItem();
        int currentSlot = player.getInventory().getSelectedSlot();
        if (!mainHand.isEmpty() && mainHand.isDamageableItem() && mainHand.getMaxDamage() > 0) {
            int remaining = mainHand.getMaxDamage() - mainHand.getDamageValue();
            float ratio = (float) remaining / (float) mainHand.getMaxDamage();
            if (ratio <= 0.10f && player.isSwinging() && lastLowDurabilityItemSlot != currentSlot) {
                lastLowDurabilityItemSlot = currentSlot;
                com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHaptic(true, true, (short) 48000, 65);
            } else if (ratio > 0.12f && lastLowDurabilityItemSlot == currentSlot) {
                lastLowDurabilityItemSlot = -1;
            }
        }
    }

    /**
     * Diagnostic string for F3 overlay and logs.
     */
    public String getDebugTelemetry() {
        String camera = (ModConfig.getInstance().nativeTrackpadCamera && activeKind.isNativeValve()) ? " | Trackpad-Kamera" : "";
        return String.format("Controller: '%s' (%s) | ActionSet: %s%s",
                activeDeviceName, activeKind.getLabel(), currentActionSet.name(), camera);
    }
}
