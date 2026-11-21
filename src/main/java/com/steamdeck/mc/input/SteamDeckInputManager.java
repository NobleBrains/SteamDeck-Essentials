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
    /** Action set a button was pressed in while no screen was open (released with that binding). */
    private final Map<DeckButton, ActionSet> heldInGameSet = new EnumMap<>(DeckButton.class);

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

    /**
     * The Steam Input bridge (paddles/stick touch arriving as keyboard keys) is only needed while Steam
     * Input translates the controller. A native Valve controller delivers those buttons directly, and on
     * a desktop the bridge would swallow real keyboard keys ('/', '[', ...).
     */
    public boolean isSteamInputBridgeActive() {
        if (activeKind.isNativeValve()) return false;
        return com.steamdeck.mc.platform.PlatformDetector.isHandheld() || ModConfig.getInstance().steamInputBridgeOnDesktop;
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
        // Vanilla only counts keyboard/mouse as input: with a controller alone the game dropped to
        // 30 FPS after 1 minute ("Inactivity FPS limit: AFK") and to 10 FPS after 10 minutes.
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getFramerateLimitTracker() != null) {
            mc.getFramerateLimitTracker().onInputReceived();
        }
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
                releaseAllButtons();
                SDLGamepad.SDL_CloseGamepad(activeGamepadHandle);
                activeGamepadHandle = 0;
                activeGamepadId = -1;
                activeKind = ControllerKind.NONE;
                lastDeviceCheckTime = 0;
                com.steamdeck.mc.power.TritonTrackpadHaptics.getInstance().clearDevice();
                LizardMode.getInstance().onControllerInactive();
            } else {
                SteamDeckEssentials.LOGGER.info("[INPUT] SDL Gamepad disconnected: ID {}", activeGamepadId);
                com.steamdeck.mc.gui.ControllerToast.getInstance().onDisconnected(activeKind, activeDeviceName);
                releaseAllButtons();
                SDLGamepad.SDL_CloseGamepad(activeGamepadHandle);
                activeGamepadHandle = 0;
                activeGamepadId = -1;
                activeDeviceName = "None";
                activeKind = ControllerKind.NONE;
                com.steamdeck.mc.power.TritonTrackpadHaptics.getInstance().clearDevice();
                LizardMode.getInstance().onControllerInactive();
            }
        }

        if (activeJoystickHandle != 0) {
            if (SDLJoystick.SDL_JoystickConnected(activeJoystickHandle)) {
                return;
            } else {
                SteamDeckEssentials.LOGGER.info("[INPUT] SDL Joystick disconnected: ID {}", activeJoystickId);
                com.steamdeck.mc.gui.ControllerToast.getInstance().onDisconnected(activeKind, activeDeviceName);
                releaseAllButtons();
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
                lastPowerQuery = 0L; // battery of the new controller, not the cached one of the old
                com.steamdeck.mc.gui.ControllerToast.getInstance().onConnected(bestKind, activeDeviceName);
                if (bestKind == ControllerKind.STEAM_CONTROLLER_2026) {
                    com.steamdeck.mc.power.TritonTrackpadHaptics.getInstance().setDevice(
                            SDLGamepad.SDL_GetGamepadPath(handle),
                            SDLGamepad.SDL_GetGamepadVendor(handle) & 0xFFFF,
                            SDLGamepad.SDL_GetGamepadProduct(handle) & 0xFFFF);
                    // Firmware keyboard/mouse emulation off while Minecraft reads the controller natively
                    LizardMode.getInstance().onControllerActive(
                            com.steamdeck.mc.power.TritonTrackpadHaptics.getInstance().getDevicePath());
                } else {
                    com.steamdeck.mc.power.TritonTrackpadHaptics.getInstance().clearDevice();
                    LizardMode.getInstance().onControllerInactive();
                }
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
                        lastPowerQuery = 0L;
                        com.steamdeck.mc.gui.ControllerToast.getInstance().onConnected(activeKind, activeDeviceName);
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
                    {SDLGamepad.SDL_GAMEPAD_BUTTON_MISC2, '2'}, {SDLGamepad.SDL_GAMEPAD_BUTTON_MISC3, '3'},
                    {SDLGamepad.SDL_GAMEPAD_BUTTON_MISC4, '4'}, {SDLGamepad.SDL_GAMEPAD_BUTTON_MISC5, '5'},
                    {SDLGamepad.SDL_GAMEPAD_BUTTON_MISC6, '6'}
            };
            String[] names = {"L4", "R4", "L5", "R5", "PadL-Klick", "QAM", "PadR-Klick", "LStick-Touch",
                    "RStick-Touch", "Grip-L", "Grip-R"};
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
        return ActionSet.IN_GAME;
    }

    /**
     * Polled on every client tick to process controller state.
     */
    public void poll(Minecraft client) {
        if (client == null) return;

        // Minecraft in the background: the controller belongs to the desktop again
        if (!updateWindowFocus(client)) {
            ensureGamepadConnected(); // keep connect/disconnect detection (and its popup) alive
            return;
        }

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

    // ---------------------------------------------------------------------------------------
    // Which device is the player using right now: controller or a real keyboard?
    // ---------------------------------------------------------------------------------------

    private long lastPhysicalKeyboardMs = 0L;
    private int syntheticKeyDepth = 0;

    /** Key events the mod sends for controller buttons go through vanilla's KeyboardHandler too. */
    private void dispatchKey(Minecraft client, int action, KeyEvent event) {
        if (client.keyboardHandler == null || client.getWindow() == null) return;
        syntheticKeyDepth++;
        try {
            client.keyboardHandler.keyPress(client.getWindow().handle(), action, event);
        } finally {
            syntheticKeyDepth--;
        }
    }

    public boolean isDispatchingSyntheticKey() {
        return syntheticKeyDepth > 0;
    }

    /** A real keyboard key was pressed. */
    public void noteKeyboardInput() {
        lastPhysicalKeyboardMs = System.currentTimeMillis();
    }

    /**
     * True while the controller is the input device in use: it was used more recently than a real
     * keyboard. Then text input gets the on-screen keyboard and the chat moves to the top. On a
     * handheld the controller counts as in use until a keyboard is typed on.
     */
    public boolean isControllerContext() {
        if (!hasController()) return false;
        if (lastPhysicalKeyboardMs == 0L) {
            return com.steamdeck.mc.platform.PlatformDetector.isHandheld() || lastControllerInputTime > 0L;
        }
        return lastControllerInputTime >= lastPhysicalKeyboardMs;
    }

    /** True while Minecraft has the window focus (or the option is off). */
    private boolean inputFocused = true;

    public boolean isInputFocused() {
        return inputFocused;
    }

    /**
     * Tracks the window focus. On losing it all held buttons are released, the trackpad pointer stops
     * and the Steam Controller's lizard mode (mouse/keyboard emulation) is restored; on regaining it the
     * mod takes the controller back. Returns whether controller input should be processed now.
     */
    private boolean updateWindowFocus(Minecraft client) {
        boolean focused = !ModConfig.getInstance().releaseControllerWhenUnfocused
                || (client.getWindow() != null && client.getWindow().isFocused());
        if (focused == inputFocused) return focused;
        inputFocused = focused;
        if (!focused) {
            releaseAllButtons();
            stickMapper.resetKeys(client.options);
            com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().cancel();
            SteamDeckEssentials.LOGGER.info("[INPUT] Minecraft im Hintergrund: Controller-Eingaben pausiert");
        } else {
            SteamDeckEssentials.LOGGER.info("[INPUT] Minecraft wieder im Vordergrund: Controller-Eingaben aktiv");
        }
        LizardMode.getInstance().setSuspended(!focused);
        return focused;
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
        if (client != null && client.gui != null && client.gui.screen() instanceof com.steamdeck.mc.gui.ControllerLayoutScreen layout
                && layout.isListeningForKey()) {
            return false;
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

        // 0. Trackpad pressure, clicks (hardware or Steam-style click pressure) and left-pad D-Pad
        ModConfig cfg = ModConfig.getInstance();
        updateTrackpadPressure();
        boolean hardLeft = SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_TOUCHPAD);
        boolean hardRight = SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_MISC2);
        // Steam-Input-style click pressure: the configured force threshold counts as a click (a full hardware click always counts)
        if (isPressureClickActive(0)) hardLeft |= updatePressureClick(0, cfg.leftTrackpadClickPressure);
        if (isPressureClickActive(1)) hardRight |= updatePressureClick(1, cfg.rightTrackpadClickPressure);
        boolean leftPadDpad = "DPAD_MOVE".equals(cfg.leftTrackpadMode) && activeKind.isNativeValve() && !hasScreen;
        boolean leftPadGrid = isGridModeActive() && !hasScreen && !VirtualKeyboard.getInstance().isOpen();

        // 1. Left Stick -> discrete 8-way movement in-game, or menu/gamemode navigation in screens
        short rawLx = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX);
        short rawLy = SDLGamepad.SDL_GetGamepadAxis(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY);
        float leftX = rawLx < 0 ? (float) rawLx / 32768.0f : (float) rawLx / 32767.0f;
        float leftY = rawLy < 0 ? (float) rawLy / 32768.0f : (float) rawLy / 32767.0f;

        // Left trackpad as click-D-Pad (Steam "D-Pad" mode, requires click): while clicked, the finger
        // position is the movement direction. The stick wins if both are used.
        if (leftPadDpad && hardLeft && Math.hypot(leftX, leftY) <= getStickDeadzone()) {
            float px = (padX[0] - 0.5f) * 2.0f;
            float py = (padY[0] - 0.5f) * 2.0f;
            float mag = (float) Math.hypot(px, py);
            if (padPressure[0] >= 0.0f && mag > 0.25f) {
                leftX = px / mag;
                leftY = py / mag;
            }
        }

        if (!hasScreen) {
            if (Math.hypot(leftX, leftY) > getStickDeadzone()) {
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
        if (!isSteamInputBridgeActive()) {
            bridgeL4Down = bridgeR4Down = bridgeL5Down = bridgeR5Down = bridgeL3TouchDown = bridgeR3TouchDown = false;
        }
        processButton(DeckButton.L4, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1) || bridgeL4Down, client, set);
        processButton(DeckButton.R4, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1) || bridgeR4Down, client, set);
        processButton(DeckButton.L5, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE2) || bridgeL5Down, client, set);
        processButton(DeckButton.R5, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2) || bridgeR5Down, client, set);

        // 6. Sticks & Trackpad clicks
        processButton(DeckButton.L3, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK), client, set);
        processButton(DeckButton.R3, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK), client, set);
        if (leftPadDpad) {
            // The pad is a D-Pad now: keep the click haptic, but don't fire the "Trackpad L" binding
            if (hardLeft != leftPadDpadWasDown) {
                com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerTrackpadClickHaptic(false, hardLeft);
                if (hardLeft) markControllerActivity();
            }
            leftPadDpadWasDown = hardLeft;
            processButton(DeckButton.LEFT_TRACKPAD, false, client, set);
        } else if (leftPadGrid) {
            // The pad is a 3x3 grid: the click fires the field under the finger, not "Trackpad L"
            leftPadDpadWasDown = false;
            processButton(DeckButton.LEFT_TRACKPAD, false, client, set);
            updateTrackpadGrid(client, set, hardLeft);
        } else {
            leftPadDpadWasDown = false;
            processButton(DeckButton.LEFT_TRACKPAD, hardLeft, client, set);
        }
        if (!leftPadGrid) {
            releaseTrackpadGrid(client, set);
            // Grid editor in the configurator shows where the thumb is (hover only, no clicks)
            if (isGridModeActive() && padPressure[0] >= 0.0f
                    && client.gui != null && client.gui.screen() instanceof com.steamdeck.mc.gui.ControllerLayoutScreen) {
                gridHoverCell = gridCellAt(padX[0], padY[0], gridHoverCell);
            }
        }
        // SDL 3.4 mapping for Steam Deck & Steam Controller 2026 (libsdl-org/SDL #15601, #15612):
        //   TOUCHPAD = left pad click, MISC2 = right pad click, MISC3 = left stick touch,
        //   MISC4 = right stick touch, MISC1 = QAM, MISC5/6 = grip sense (Steam Controller only)
        processButton(DeckButton.RIGHT_TRACKPAD, hardRight, client, set);

        // 7. D-Pad
        processButton(DeckButton.DPAD_UP, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP), client, set);
        processButton(DeckButton.DPAD_DOWN, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN), client, set);
        processButton(DeckButton.DPAD_LEFT, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT), client, set);
        processButton(DeckButton.DPAD_RIGHT, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT), client, set);

        // 8. Start & Select
        processButton(DeckButton.START, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_START), client, set);
        processButton(DeckButton.SELECT, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_BACK), client, set);

        // 9. Joystick Capacitive Touch
        processButton(DeckButton.LEFT_STICK_TOUCH, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_MISC3) || bridgeL3TouchDown, client, set);
        processButton(DeckButton.RIGHT_STICK_TOUCH, SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_MISC4) || bridgeR3TouchDown, client, set);
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
            if (Math.hypot(leftX, leftY) > getStickDeadzone()) {
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

    /** True while {@link #releaseAllButtons()} runs: no haptics for a controller that is going away. */
    private boolean releasingAll = false;

    /**
     * Releases every button that is still held (controller disconnected or replaced by a preferred one).
     * Without this, bindings held at that moment (e.g. RT = attack, A = jump, custom keys) stayed pressed
     * forever, because the release edge was never seen.
     */
    private void releaseAllButtons() {
        Minecraft client = Minecraft.getInstance();
        releasingAll = true;
        try {
            for (DeckButton b : DeckButton.values()) {
                if (Boolean.TRUE.equals(prevButtonStates.get(b))) {
                    if (client != null) {
                        processButton(b, false, client, currentActionSet);
                    } else {
                        prevButtonStates.put(b, false);
                    }
                }
            }
        } catch (Throwable t) {
            SteamDeckEssentials.LOGGER.warn("[INPUT] Failed to release held buttons", t);
            for (DeckButton b : DeckButton.values()) prevButtonStates.put(b, false);
        } finally {
            releasingAll = false;
        }
        repeatState.clear();
        activeHotbarDirection = 0;
        activeDpadNavButton = null;
        menuShiftHeld = false;
        leftPadDpadWasDown = false;
        padPressureClick[0] = padPressureClick[1] = false;
        padPressure[0] = padPressure[1] = -1.0f;
        cameraPad.reset();
        cursorPad.reset();
        if (client != null && client.options != null) {
            stickMapper.resetKeys(client.options);
        }
    }

    private void processButton(DeckButton button, boolean isDown, Minecraft client, ActionSet set) {
        if (isDown) {
            markControllerActivity();
        }

        boolean wasDown = prevButtonStates.getOrDefault(button, false);
        prevButtonStates.put(button, isDown);

        // Physical click feel for trackpad presses (only native controllers; Steam Input does its own)
        if ((button == DeckButton.LEFT_TRACKPAD || button == DeckButton.RIGHT_TRACKPAD) && isDown != wasDown
                && !releasingAll) {
            com.steamdeck.mc.power.HapticFeedbackManager.getInstance()
                    .triggerTrackpadClickHaptic(button == DeckButton.RIGHT_TRACKPAD, isDown);
        }

        // A button pressed in-game keeps its in-game binding until it is released, even if a screen opens
        // or the action set changes meanwhile (e.g. hold A = jump, then open the inventory). Before, the
        // release went to the new set's binding and the original key (jump, attack, zoom, custom key) stuck.
        boolean screenNow = client.gui != null && client.gui.screen() != null;
        if (isDown && !wasDown) {
            if (screenNow) heldInGameSet.remove(button);
            else heldInGameSet.put(button, set);
        } else if (wasDown) {
            ActionSet pressedSet = heldInGameSet.get(button);
            if (!isDown) heldInGameSet.remove(button);
            if (pressedSet != null && (pressedSet != set || screenNow)) {
                if (!isDown) {
                    ActionBinding pressed = ModConfig.getInstance().getBinding(pressedSet, button);
                    if (pressed.getAction() != DeckAction.NONE) {
                        // same context as the press (no screen), so MOUSE_* actions release attack/use again
                        executeBinding(pressed, false, true, client, false, false);
                    }
                    repeatState.remove(button);
                }
                return; // while still held in the new context: no new action
            }
        }

        // On-screen keyboard face buttons (like Steam's keyboard): A = key under the last moved pointer,
        // X = backspace, Y = space, Start = enter/send, B = hide the keyboard. Both edges are consumed so
        // the MENUS binding of these buttons (e.g. A = Enter) can't send the half-typed message.
        if (com.steamdeck.mc.gui.VirtualKeyboard.getInstance().isOpen() && client.gui != null && client.gui.screen() != null
                && (button == DeckButton.A || button == DeckButton.B || button == DeckButton.X
                    || button == DeckButton.Y || button == DeckButton.START)) {
            if (isDown && !wasDown) {
                com.steamdeck.mc.gui.VirtualKeyboard osk = com.steamdeck.mc.gui.VirtualKeyboard.getInstance();
                Screen screen = client.gui.screen();
                switch (button) {
                    case A -> osk.triggerLastCursorClick(screen);
                    case X -> osk.tapAction(com.steamdeck.mc.gui.VirtualKeyboard.KeyType.BACKSPACE, screen);
                    case Y -> osk.tapAction(com.steamdeck.mc.gui.VirtualKeyboard.KeyType.SPACE, screen);
                    case START -> osk.tapAction(com.steamdeck.mc.gui.VirtualKeyboard.KeyType.ENTER, screen);
                    default -> {
                        osk.setOpen(false);
                        com.steamdeck.mc.gui.VirtualKeyboard.playClickSound();
                    }
                }
            }
            return;
        }

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

            // 0. Controller configurator: raw buttons, independent of the MENUS bindings (can't lock yourself out)
            if (screen instanceof com.steamdeck.mc.gui.ControllerLayoutScreen layoutScreen) {
                if (button == DeckButton.DPAD_UP || button == DeckButton.DPAD_DOWN
                        || button == DeckButton.DPAD_LEFT || button == DeckButton.DPAD_RIGHT) {
                    int dx = (button == DeckButton.DPAD_LEFT) ? -1 : (button == DeckButton.DPAD_RIGHT ? 1 : 0);
                    int dy = (button == DeckButton.DPAD_UP) ? -1 : (button == DeckButton.DPAD_DOWN ? 1 : 0);
                    handleDpadMenuNavigation(client, button, dx, dy, isDown, wasDown);
                } else if (button == DeckButton.RIGHT_TRACKPAD && isNativeTrackpadActive(ModConfig.getInstance())
                        && client.getWindow() != null) {
                    // Pad click = real mouse click where the trackpad pointer is (footer, tabs, outside popups)
                    if (isDown != wasDown) {
                        client.mouseHandler.onButton(client.getWindow().handle(),
                                new MouseButtonInfo(SDLMouse.SDL_BUTTON_LEFT, 0), isDown ? 1 : 0);
                    }
                } else if (isDown && !wasDown) {
                    layoutScreen.onControllerButton(button);
                }
                return;
            }

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
        if (!hasScreen) menuShiftHeld = false;
        if (binding.getAction() == DeckAction.NONE) return;

        long now = System.currentTimeMillis();
        if (isDown && !wasDown) {
            repeatState.put(button, new long[]{now, now});
            if (binding.hasHaptic()) {
                // Steam "haptic_intensity": short tick on the pad of the button's side
                com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerPadHaptic(
                        isRightSide(button), false, (short) 22000, 18);
            }
        } else if (isDown && binding.hasRepeat()) {
            long[] st = repeatState.get(button);
            if (st != null && now - st[0] >= binding.getRepeatDelayMs() && now - st[1] >= binding.getRepeatRateMs()) {
                st[1] = now;
                // Repeat = release + press of the same binding (keys get a real key-repeat event)
                executeBinding(binding, false, true, client, hasScreen, true);
                executeBinding(binding, true, false, client, hasScreen, true);
                if (binding.hasHaptic()) {
                    com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerPadHaptic(
                            isRightSide(button), false, (short) 22000, 18);
                }
                return;
            }
        }
        executeBinding(binding, isDown, wasDown, client, hasScreen, false);
    }

    private static boolean isRightSide(DeckButton b) {
        return switch (b) {
            case A, B, X, Y, RB, RT, R3, R4, R5, RIGHT_TRACKPAD, START, RIGHT_STICK_TOUCH -> true;
            default -> false;
        };
    }

    private void scrollOnce(Minecraft client, boolean hasScreen, int direction) {
        if (hasScreen) {
            Screen screen = client.gui.screen();
            com.mojang.blaze3d.platform.Window win = client.getWindow();
            if (screen == null || win == null) return;
            double mx = client.mouseHandler.xpos() * win.getGuiScaledWidth() / (double) Math.max(1, win.getScreenWidth());
            double my = client.mouseHandler.ypos() * win.getGuiScaledHeight() / (double) Math.max(1, win.getScreenHeight());
            screen.mouseScrolled(mx, my, 0.0, direction);
        } else {
            // Like the mouse wheel in vanilla: wheel up = previous hotbar slot
            stepHotbar(client, -direction);
        }
    }

    /** Performs a binding (custom key or semantic action). {@code repeat} marks synthetic hold-repeats. */
    private void executeBinding(ActionBinding binding, boolean isDown, boolean wasDown, Minecraft client,
                                boolean hasScreen, boolean repeat) {
        if (binding.isCustomKey()) {
            int keyCode = binding.getCustomKeyCode();
            try {
                // Exactly like a real keyboard: vanilla's KeyboardHandler decides what the key does (it sets
                // and clicks key mappings only when no screen is open). Setting them here as well counted
                // every press twice in-game and let keys act "through" open menus.
                if (client.keyboardHandler != null && client.getWindow() != null && isDown != wasDown) {
                    if (repeat && !isDown) {
                        // a synthetic repeat sends one "repeat" key event (action 2) instead of release+press
                    } else {
                        dispatchKey(client, repeat ? 2 : (isDown ? 1 : 0), new KeyEvent(keyCode, keyCode, 0));
                    }
                }
            } catch (Throwable t) {
                SteamDeckEssentials.LOGGER.warn("Failed to dispatch custom key event", t);
            }
            return;
        }

        if (binding.isMapping()) {
            // Minecraft / other mods' key mapping (e.g. a zoom mod): pressed like its own key would be
            KeyMapping mapping = KeyMapping.get(binding.getMappingName());
            if (mapping != null) {
                if (repeat && !isDown) return; // repeat = one extra press, see processButton
                pressMapping(client, mapping, isDown, repeat ? false : wasDown, hasScreen);
            }
            return;
        }

        DeckAction action = binding.getAction();

        switch (action) {
            case RADIAL_MENU_CYCLE -> {
                if (isDown && !wasDown && !hasScreen) {
                    com.steamdeck.mc.gui.RadialMenuOverlay.getInstance().cyclePage();
                }
            }
            case JUMP -> pressMapping(client, client.options.keyJump, isDown, wasDown, hasScreen);
            case ATTACK -> pressMapping(client, client.options.keyAttack, isDown, wasDown, hasScreen);
            case USE -> pressMapping(client, client.options.keyUse, isDown, wasDown, hasScreen);
            case DROP -> pressMapping(client, client.options.keyDrop, isDown, wasDown, hasScreen);
            case INVENTORY -> pressMapping(client, client.options.keyInventory, isDown, wasDown, hasScreen);
            case SWAP_OFFHAND -> pressMapping(client, client.options.keySwapOffhand, isDown, wasDown, hasScreen);
            case SPRINT -> pressMapping(client, client.options.keySprint, isDown, wasDown, hasScreen);
            case SNEAK -> {
                if (hasScreen) {
                    menuShiftHeld = isDown; // held Shift in inventories: clicks become shift-clicks
                } else {
                    setKeybindState(client.options.keyShift, isDown, wasDown);
                }
            }
            case MOUSE_SCROLL_UP -> {
                if (isDown && !wasDown) scrollOnce(client, hasScreen, 1);
            }
            case MOUSE_SCROLL_DOWN -> {
                if (isDown && !wasDown) scrollOnce(client, hasScreen, -1);
            }
            case SNEAK_TOGGLE -> {
                // A toggle key does nothing in menus in vanilla either
                if (isDown && !wasDown && !hasScreen) {
                    sneakToggled = !sneakToggled;
                    client.options.keyShift.setDown(sneakToggled);
                }
            }
            case PICK_BLOCK -> pressMapping(client, client.options.keyPickItem, isDown, wasDown, hasScreen);
            case TOGGLE_PERSPECTIVE -> pressMapping(client, client.options.keyTogglePerspective, isDown, wasDown, hasScreen);
            case TOGGLE_DEBUG -> {
                if (isDown && !wasDown && !hasScreen && client.debugEntries != null) {
                    client.debugEntries.toggleDebugOverlay();
                }
            }
            case OPEN_CHAT -> {
                if (hasScreen) {
                    pressMapping(client, client.options.keyChat, isDown, wasDown, true);
                } else if (isDown && !wasDown && client.gui != null) {
                    client.gui.openChatScreen(ChatMethod.MESSAGE);
                }
            }
            case HOTBAR_SLOT_1, HOTBAR_SLOT_2, HOTBAR_SLOT_3, HOTBAR_SLOT_4, HOTBAR_SLOT_5,
                 HOTBAR_SLOT_6, HOTBAR_SLOT_7, HOTBAR_SLOT_8, HOTBAR_SLOT_9 -> {
                if (hasScreen) {
                    // In inventories the number keys swap the hovered item into that slot (vanilla)
                    pressMapping(client, client.options.keyHotbarSlots[action.hotbarSlot()], isDown, wasDown, true);
                } else if (isDown && !wasDown && client.player != null) {
                    client.player.getInventory().setSelectedSlot(action.hotbarSlot());
                }
            }
            // There is no "next slot" key in vanilla menus (the mouse wheel scrolls the screen instead)
            case HOTBAR_PREV -> {
                if (!hasScreen || !isDown) handleHotbarAction(client, -1, isDown && !hasScreen, wasDown);
            }
            case HOTBAR_NEXT -> {
                if (!hasScreen || !isDown) handleHotbarAction(client, 1, isDown && !hasScreen, wasDown);
            }
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
                                    / (double) Math.max(1, client.getWindow().getScreenWidth());
                            double my = client.mouseHandler.ypos() * (double) client.getWindow().getGuiScaledHeight()
                                    / (double) Math.max(1, client.getWindow().getScreenHeight());
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
                        client.mouseHandler.onButton(client.getWindow().handle(), new MouseButtonInfo(SDLMouse.SDL_BUTTON_LEFT, menuShiftHeld ? InputConstants.MOD_SHIFT : 0), isDown ? 1 : 0);
                    }
                } else {
                    setKeybindState(client.options.keyAttack, isDown, wasDown);
                }
            }
            case MOUSE_RIGHT_CLICK -> {
                if (hasScreen && client.getWindow() != null) {
                    if (isDown != wasDown) {
                        client.mouseHandler.onButton(client.getWindow().handle(), new MouseButtonInfo(SDLMouse.SDL_BUTTON_RIGHT, menuShiftHeld ? InputConstants.MOD_SHIFT : 0), isDown ? 1 : 0);
                    }
                } else {
                    setKeybindState(client.options.keyUse, isDown, wasDown);
                }
            }
            case MOUSE_MIDDLE_CLICK -> {
                if (hasScreen && client.getWindow() != null) {
                    if (isDown != wasDown) {
                        client.mouseHandler.onButton(client.getWindow().handle(), new MouseButtonInfo(SDLMouse.SDL_BUTTON_MIDDLE, menuShiftHeld ? InputConstants.MOD_SHIFT : 0), isDown ? 1 : 0);
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

        if (screen instanceof com.steamdeck.mc.gui.ControllerLayoutScreen layoutScreen) {
            layoutScreen.navigate(dx, dy);
            com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(dx != 0 ? dx : dy);
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
        double curGuiX = client.mouseHandler.xpos() * (double) win.getGuiScaledWidth() / (double) Math.max(1, win.getScreenWidth());
        double curGuiY = client.mouseHandler.ypos() * (double) win.getGuiScaledHeight() / (double) Math.max(1, win.getScreenHeight());

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
            float winX = (float) targetGuiX * (float) win.getScreenWidth() / (float) Math.max(1, win.getGuiScaledWidth());
            float winY = (float) targetGuiY * (float) win.getScreenHeight() / (float) Math.max(1, win.getGuiScaledHeight());
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

    /**
     * Presses a Minecraft key mapping. In-game the mapping is set directly (same as its key being held).
     * With a screen open, the mapping's real key or mouse button is sent through vanilla's own input
     * handlers instead, so the controller can do exactly what keyboard and mouse can in that screen and
     * nothing more (no jumping or walking with a menu open; Q over a slot drops, E closes the inventory).
     */
    private void pressMapping(Minecraft client, KeyMapping mapping, boolean isDown, boolean wasDown, boolean hasScreen) {
        if (!hasScreen) {
            setKeybindState(mapping, isDown, wasDown);
            return;
        }
        if (isDown == wasDown || client.getWindow() == null || mapping == null) return;
        InputConstants.Key key = ((KeyMappingAccessor) mapping).getKey();
        if (key == null || key.getValue() < 0) return;
        long window = client.getWindow().handle();
        if (key.getType() == InputConstants.Type.MOUSE) {
            client.mouseHandler.onButton(window,
                    new MouseButtonInfo(key.getValue(), menuShiftHeld ? InputConstants.MOD_SHIFT : 0), isDown ? 1 : 0);
        } else if (client.keyboardHandler != null) {
            dispatchKey(client, isDown ? 1 : 0, new KeyEvent(key.getValue(), key.getValue(), 0));
        }
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

    /** Finger tracking state of the right trackpad. Separate instances for camera and menu cursor,
     *  because the HUD (camera) keeps rendering underneath open screens. */
    /**
     * Relative finger tracking for one trackpad (camera / mouse cursor).
     *
     * <p>Noise handling follows what Steamify and mc-deck-native-controls do (both based on
     * Jibb Smart's gyro filtering, gyrowiki.jibbsmart.com), applied to the finger velocity in
     * pad widths per second:</p>
     * <ol>
     *     <li><b>Settle:</b> the first 40 ms after the finger lands are ignored (the contact point
     *     jumps while the fingertip flattens).</li>
     *     <li><b>Soft tiered smoothing:</b> above {@code HI} the motion is passed through directly
     *     (no lag on fast swipes), below {@code LO} it is fully smoothed with a time-based
     *     exponential filter ({@code TAU}), in between it is blended.</li>
     *     <li><b>Tightening:</b> below {@code TIGHT} the output shrinks proportionally to the speed,
     *     a soft deadzone that removes the jitter of a resting finger without a hard edge.</li>
     *     <li><b>Click freeze:</b> pressing deforms the fingertip and drags the point, so motion is
     *     frozen for 60 ms when a click starts (cursor stays on the target being clicked).</li>
     * </ol>
     */
    private static final class TrackpadTracker {
        static final long SETTLE_NANOS = 40_000_000L;
        static final long CLICK_FREEZE_NANOS = 60_000_000L;
        static final double TIGHT = 0.05;
        static final double LO = 0.15;
        static final double HI = 0.6;
        static final double TAU = 0.05;

        boolean wasDown = false;
        float lastX;
        float lastY;
        long landNanos;
        long lastNanos;
        float lastDt = 0.016f;
        double smoothX;
        double smoothY;
        boolean wasClicked = false;
        long freezeUntil = 0L;

        void reset() {
            wasDown = false;
            smoothX = smoothY = 0.0;
            wasClicked = false;
        }
    }

    // Live pressure per pad (0..1, -1 = no finger) and pressure-click state with hysteresis
    private final float[] padPressure = {-1.0f, -1.0f};
    private final float[] padX = {0.5f, 0.5f};
    private final float[] padY = {0.5f, 0.5f};
    private boolean leftPadDpadWasDown = false;
    private boolean menuShiftHeld = false;
    // Hold-to-repeat state per button (Steam "hold_repeats")
    private final Map<DeckButton, long[]> repeatState = new EnumMap<>(DeckButton.class);
    private final boolean[] padPressureClick = {false, false};

    private final TrackpadTracker cameraPad = new TrackpadTracker();
    private final TrackpadTracker cursorPad = new TrackpadTracker();
    private double cursorX = -1;
    private double cursorY = -1;
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
        noteFrame(now);
        float dt = lastCameraFrameNanos == 0L ? 0.0f : Math.min(0.1f, (now - lastCameraFrameNanos) / 1_000_000_000.0f);
        lastCameraFrameNanos = now;

        if (client == null || dt <= 0.0f) return;
        LocalPlayer player = client.player;
        boolean hasScreen = client.gui != null && client.gui.screen() != null;
        if (player == null || hasScreen || !client.mouseHandler.isMouseGrabbed() || !hasController() || !inputFocused) {
            cameraPad.reset();
            cameraPendingX = cameraPendingY = 0.0;
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
            float deadzone = aiming ? 0.02f : Math.max(0.05f, getStickDeadzone() * 0.5f);
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
        if (isNativeTrackpadActive(config)) {
            float[] d = readRightTrackpadDelta(cameraPad);
            float scale = TRACKPAD_DEGREES_PER_PAD_WIDTH * config.trackpadSensitivity;
            if (d != null) {
                markControllerActivity();
                if (config.trackpadInterpolation) {
                    // Spread each sample over one sample interval instead of turning in steps
                    cameraPendingX += d[0] * scale;
                    cameraPendingY += d[1] * scale;
                    cameraSegmentEnd = now + (long) getPadSampleIntervalNanos();
                } else {
                    player.turn(d[0] * scale * TURN_UNITS_PER_DEGREE, d[1] * scale * TURN_UNITS_PER_DEGREE);
                }
            }
            if (cameraPendingX != 0.0 || cameraPendingY != 0.0) {
                double frameNanos = dt * 1_000_000_000.0;
                double left = Math.max(0.0, cameraSegmentEnd - now);
                double f = left <= 0.0 ? 1.0 : Math.min(1.0, frameNanos / (left + frameNanos));
                double tx = cameraPendingX * f;
                double ty = cameraPendingY * f;
                cameraPendingX -= tx;
                cameraPendingY -= ty;
                if (Math.abs(cameraPendingX) < 1e-4 && Math.abs(cameraPendingY) < 1e-4) cameraPendingX = cameraPendingY = 0.0;
                player.turn(tx * TURN_UNITS_PER_DEGREE, ty * TURN_UNITS_PER_DEGREE);
            }
        } else {
            cameraPad.reset();
            cameraPendingX = cameraPendingY = 0.0;
        }
    }

    private double cameraPendingX = 0.0;
    private double cameraPendingY = 0.0;
    private long cameraSegmentEnd = 0L;

    private boolean isNativeTrackpadActive(ModConfig config) {
        return config.nativeTrackpadCamera && activeKind.isNativeValve() && activeGamepadHandle != 0;
    }

    /**
     * Movement of the finger on the right trackpad since the last call, in pad widths
     * (0..1 coordinates), or null if there was no movement / no finger.
     */
    private float[] readRightTrackpadDelta(TrackpadTracker tracker) {
        int pads = SDLGamepad.SDL_GetNumGamepadTouchpads(activeGamepadHandle);
        if (pads <= 0) {
            tracker.reset();
            return null;
        }
        // Two pads: index 1 is the right one. A single reported pad is used as-is.
        int padIndex = pads >= 2 ? 1 : 0;

        touchDown.clear();
        touchX.clear();
        touchY.clear();
        touchPressure.clear();
        boolean ok = SDLGamepad.SDL_GetGamepadTouchpadFinger(activeGamepadHandle, padIndex, 0,
                touchDown, touchX, touchY, touchPressure);
        if (!ok || touchDown.get(0) == 0) {
            tracker.reset();
            return null;
        }

        float x = touchX.get(0);
        float y = touchY.get(0);
        long now = System.nanoTime();
        if (!tracker.wasDown) {
            tracker.wasDown = true;
            tracker.lastX = x;
            tracker.lastY = y;
            tracker.landNanos = tracker.lastNanos = now;
            tracker.smoothX = tracker.smoothY = 0.0;
            return null;
        }

        // Only real new samples count. The pad reports at its own rate (BLE often slower than the frame
        // rate): frames without a new sample used to yield "speed 0" followed by a double-speed frame,
        // which made the tiered smoothing and the pointer acceleration jump back and forth (stutter).
        boolean newSample = x != tracker.lastX || y != tracker.lastY;

        // Click freeze: detect the start of a press (hardware click or Steam-style click pressure)
        boolean clicked = SDLGamepad.SDL_GetGamepadButton(activeGamepadHandle, SDLGamepad.SDL_GAMEPAD_BUTTON_MISC2)
                || (isPressureClickActive(1) && touchPressure.get(0) >= ModConfig.getInstance().rightTrackpadClickPressure);
        if (clicked && !tracker.wasClicked) {
            tracker.freezeUntil = now + TrackpadTracker.CLICK_FREEZE_NANOS;
        }
        tracker.wasClicked = clicked;

        if (now - tracker.landNanos < TrackpadTracker.SETTLE_NANOS || now < tracker.freezeUntil) {
            // keep following the finger silently so nothing jumps afterwards
            tracker.lastX = x;
            tracker.lastY = y;
            tracker.lastNanos = now;
            tracker.smoothX = tracker.smoothY = 0.0;
            return null;
        }

        if (!newSample) {
            // Keep lastNanos: the next real sample then gets the full time span since the previous one
            return null;
        }
        notePadSample(now);
        double dt = Math.max(0.001, Math.min(0.1, (now - tracker.lastNanos) / 1_000_000_000.0));
        double vx = (x - tracker.lastX) / dt;
        double vy = (y - tracker.lastY) / dt;
        tracker.lastX = x;
        tracker.lastY = y;
        tracker.lastNanos = now;
        tracker.lastDt = (float) dt;

        double speed = Math.hypot(vx, vy);
        double direct = Math.max(0.0, Math.min(1.0, (speed - TrackpadTracker.LO) / (TrackpadTracker.HI - TrackpadTracker.LO)));
        double k = 1.0 - Math.exp(-dt / TrackpadTracker.TAU);
        tracker.smoothX += (vx * (1.0 - direct) - tracker.smoothX) * k;
        tracker.smoothY += (vy * (1.0 - direct) - tracker.smoothY) * k;
        double outX = vx * direct + tracker.smoothX;
        double outY = vy * direct + tracker.smoothY;

        double outSpeed = Math.hypot(outX, outY);
        if (outSpeed < TrackpadTracker.TIGHT) {
            double f = outSpeed / TrackpadTracker.TIGHT;
            outX *= f;
            outY *= f;
        }

        float dx = (float) (outX * dt);
        float dy = (float) (outY * dt);
        if (dx == 0.0f && dy == 0.0f) return null;
        return new float[]{dx, dy};
    }

    /**
     * Right trackpad moves the mouse cursor in menus/inventories (native Valve controllers only;
     * with Steam Input the pad already is a mouse). The right pad click is MOUSE_LEFT_CLICK in the
     * MENUS action set, so pointing and clicking works like on the Deck under Steam.
     */
    private void pollTrackpadCursor(Minecraft client) {
        ModConfig config = ModConfig.getInstance();
        if (!isNativeTrackpadActive(config) || client.getWindow() == null) {
            cursorPad.reset();
            cursorX = -1;
            return;
        }
        boolean wasTouching = cursorPad.wasDown;
        float[] d = readRightTrackpadDelta(cursorPad);
        long now = System.nanoTime();
        com.mojang.blaze3d.platform.Window win = client.getWindow();

        if (!cursorPad.wasDown) {
            // Finger lifted: finish the running segment so the pointer ends exactly on the target
            if (cursorX >= 0 && (shownCursorX != cursorX || shownCursorY != cursorY)) {
                warpCursor(client, win, cursorX, cursorY);
            }
            return;
        }
        if (!wasTouching || cursorX < 0) {
            // Finger just landed: continue from the current OS cursor position
            cursorX = shownCursorX = segStartX = client.mouseHandler.xpos();
            cursorY = shownCursorY = segStartY = client.mouseHandler.ypos();
            segStartNanos = now;
        }

        if (d != null) {
            markControllerActivity();
            // Mouse coordinates are SDL window points (getScreenWidth), NOT framebuffer pixels (getWidth):
            // with display scaling (e.g. 1.4) the old pixel bounds let the cursor run far past the edge.
            int w = Math.max(1, win.getScreenWidth());
            int h = Math.max(1, win.getScreenHeight());
            // Pointer acceleration like Steam's mouse mode: slow = precise, fast swipe = far
            float speed = (float) Math.hypot(d[0], d[1]) / cursorPad.lastDt; // pad widths per second
            double accel = 0.6 + Math.min(1.4, speed * 0.7);
            double scale = w * config.trackpadSensitivity * accel;
            cursorX = Math.max(0, Math.min(w - 1, cursorX + d[0] * scale));
            cursorY = Math.max(0, Math.min(h - 1, cursorY + d[1] * scale));
            // New interpolation segment from where the pointer is shown now to the new target
            segStartX = shownCursorX;
            segStartY = shownCursorY;
            segStartNanos = now;
        }

        double x = cursorX;
        double y = cursorY;
        if (config.trackpadInterpolation) {
            // The pad reports slower than the frame rate (e.g. ~50-60 Hz via Bluetooth). Instead of jumping
            // once per sample, glide to the target over one sample interval: smooth at any frame rate,
            // costs about one sample interval of latency.
            double dur = getPadSampleIntervalNanos();
            double t = Math.min(1.0, (now - segStartNanos) / dur);
            x = segStartX + (cursorX - segStartX) * t;
            y = segStartY + (cursorY - segStartY) * t;
        }
        if (x != shownCursorX || y != shownCursorY) {
            warpCursor(client, win, x, y);
        }
    }

    private void warpCursor(Minecraft client, com.mojang.blaze3d.platform.Window win, double x, double y) {
        shownCursorX = x;
        shownCursorY = y;
        SDLMouse.SDL_WarpMouseInWindow(win.handle(), (float) x, (float) y);
        client.mouseHandler.onMove(win.handle(), x, y, 0.0, 0.0);
        client.setLastInputType(InputType.MOUSE);
    }

    // ---------------------------------------------------------------------------------------
    // Trackpad sample-rate measurement (debug overlay) and interpolation timing
    // ---------------------------------------------------------------------------------------

    private double shownCursorX = -1;
    private double shownCursorY = -1;
    private double segStartX;
    private double segStartY;
    private long segStartNanos;

    /** Smoothed time between two real samples of the right pad (only while the finger moves). */
    private double padSampleIntervalEmaMs = 16.0;
    private double padSampleJitterEmaMs = 0.0;
    private long lastPadSampleNanos = 0L;
    private int padSamplesInWindow = 0;
    private int framesInWindow = 0;
    private long statsWindowStart = 0L;
    private long lastFrameNanos = 0L;
    private int padSampleHz = 0;
    private int frameHz = 0;

    /** Called for every new right-pad sample (finger moved). */
    private void notePadSample(long now) {
        if (lastPadSampleNanos != 0L) {
            double ms = (now - lastPadSampleNanos) / 1_000_000.0;
            if (ms < 60.0) { // ignore pauses (finger resting), they are not the report rate
                padSampleJitterEmaMs += (Math.abs(ms - padSampleIntervalEmaMs) - padSampleJitterEmaMs) * 0.1;
                padSampleIntervalEmaMs += (ms - padSampleIntervalEmaMs) * 0.1;
            }
        }
        lastPadSampleNanos = now;
        padSamplesInWindow++;
    }

    /** Called once per rendered frame (HUD or screen overlay, de-duplicated). */
    private void noteFrame(long now) {
        if (now - lastFrameNanos < 2_000_000L) return; // HUD + screen hook in the same frame
        lastFrameNanos = now;
        framesInWindow++;
        if (statsWindowStart == 0L) statsWindowStart = now;
        if (now - statsWindowStart >= 1_000_000_000L) {
            double secs = (now - statsWindowStart) / 1_000_000_000.0;
            padSampleHz = (int) Math.round(padSamplesInWindow / secs);
            frameHz = (int) Math.round(framesInWindow / secs);
            padSamplesInWindow = 0;
            framesInWindow = 0;
            statsWindowStart = now;
        }
    }

    /** Interpolation segment length: one measured sample interval, clamped to 4..40 ms. */
    private double getPadSampleIntervalNanos() {
        return Math.max(4.0, Math.min(40.0, padSampleIntervalEmaMs)) * 1_000_000.0;
    }

    /** One-line live measurement for the debug overlay. */
    public String getTrackpadDebugLine() {
        ModConfig c = ModConfig.getInstance();
        return String.format(java.util.Locale.ROOT,
                "Pad R: %d Samples/s (Ø %.1f ms, Jitter %.1f ms) • Frames: %d/s • Interpolation: %s",
                padSampleHz, padSampleIntervalEmaMs, padSampleJitterEmaMs, frameHz,
                c.trackpadInterpolation ? "an" : "aus");
    }

    /**
     * Effective stick deadzone. In auto mode Steam Input's per-controller default is used
     * (steamclient GetDefaultJoystickDeadzone, FINDINGS 4.1): Steam Controller 2026 4096/32767 = 12.5 %,
     * Steam Deck 8192 = 25 %. Other controllers use the configured value.
     */
    public float getStickDeadzone() {
        ModConfig c = ModConfig.getInstance();
        if (c.stickDeadzoneAuto) {
            if (activeKind == ControllerKind.STEAM_CONTROLLER_2026) return 0.125f;
            if (activeKind == ControllerKind.STEAM_DECK) return 0.25f;
        }
        return c.stickDeadzone;
    }

    private final java.nio.IntBuffer powerPercent = org.lwjgl.BufferUtils.createIntBuffer(1);
    private int cachedPowerState = org.lwjgl.sdl.SDLPower.SDL_POWERSTATE_UNKNOWN;
    private int cachedPowerPercent = -1;
    private long lastPowerQuery = 0L;

    /**
     * Battery of the active controller via SDL (for the Steam Controller 2026 SDL parses input report
     * 0x43: status + percent). Returns {SDL_PowerState, percent or -1}; cached for 2 s.
     */
    public int[] getControllerBattery() {
        long now = System.currentTimeMillis();
        if (now - lastPowerQuery > 2000L) {
            lastPowerQuery = now;
            if (activeGamepadHandle != 0) {
                powerPercent.clear();
                powerPercent.put(0, -1);
                cachedPowerState = SDLGamepad.SDL_GetGamepadPowerInfo(activeGamepadHandle, powerPercent);
                cachedPowerPercent = powerPercent.get(0);
            } else {
                cachedPowerState = org.lwjgl.sdl.SDLPower.SDL_POWERSTATE_UNKNOWN;
                cachedPowerPercent = -1;
            }
        }
        return new int[]{cachedPowerState, cachedPowerPercent};
    }

    /** Pressure-based trackpad click only for native Valve pads (Steam Input handles it itself otherwise). */
    public boolean isPressureClickActive(int side) {
        ModConfig c = ModConfig.getInstance();
        String mode = side == 0 ? c.leftTrackpadClickMode : c.rightTrackpadClickMode;
        return "PRESSURE".equals(mode) && activeKind.isNativeValve() && activeGamepadHandle != 0;
    }

    /** Finger on the pad (left 0 / right 1)? Used e.g. for "gyro while touching a trackpad". */
    public boolean isTrackpadTouched(int side) {
        return padPressure[side] >= 0.0f;
    }

    /** Current (last polled) state of a physical button. */
    public boolean isButtonDown(DeckButton button) {
        return prevButtonStates.getOrDefault(button, false);
    }

    /** Current pressure of the left (0) / right (1) pad in 0..1, or -1 without a finger / without data. */
    public float getTrackpadPressure(int side) {
        return padPressure[side];
    }

    public boolean isTrackpadPressureClicked(int side) {
        return padPressureClick[side];
    }

    /** Reads the finger pressure of both pads from SDL (touchpad 0 = left, 1 = right). */
    public void updateTrackpadPressure() {
        if (activeGamepadHandle == 0 || !activeKind.isNativeValve()) {
            padPressure[0] = padPressure[1] = -1.0f;
            return;
        }
        int pads = SDLGamepad.SDL_GetNumGamepadTouchpads(activeGamepadHandle);
        for (int side = 0; side < 2; side++) {
            int index = pads >= 2 ? side : 0;
            if (pads <= 0 || (pads < 2 && side == 0)) {
                padPressure[side] = -1.0f;
                continue;
            }
            touchDown.clear();
            touchX.clear();
            touchY.clear();
            touchPressure.clear();
            boolean ok = SDLGamepad.SDL_GetGamepadTouchpadFinger(activeGamepadHandle, index, 0,
                    touchDown, touchX, touchY, touchPressure);
            padPressure[side] = (ok && touchDown.get(0) != 0) ? Math.max(0.0f, Math.min(1.0f, touchPressure.get(0))) : -1.0f;
            if (padPressure[side] >= 0.0f) {
                padX[side] = touchX.get(0);
                padY[side] = touchY.get(0);
            }
        }
    }

    /** Hysteresis: press at the threshold, release below 80 % of it (no flicker around the edge). */
    private boolean updatePressureClick(int side, float threshold) {
        float p = padPressure[side];
        boolean pressed = padPressureClick[side];
        if (p < 0.0f) {
            pressed = false;
        } else if (pressed) {
            pressed = p >= threshold * 0.8f;
        } else {
            pressed = p >= threshold;
        }
        padPressureClick[side] = pressed;
        return pressed;
    }

    // ---------------------------------------------------------------------------------------
    // Left trackpad as 3x3 grid (nine extra buttons, e.g. hotbar slots 1-9)
    // ---------------------------------------------------------------------------------------

    /** Fraction of a cell the finger must move past the border before the hovered cell changes. */
    private static final float GRID_HYSTERESIS = 0.04f;

    private int gridHoverCell = -1;
    private int gridPressedCell = -1;
    private boolean gridClickWasDown = false;
    private long gridLastTouchMs = 0L;

    public boolean isGridModeActive() {
        return "GRID_3X3".equals(ModConfig.getInstance().leftTrackpadMode)
                && activeKind.isNativeValve() && activeGamepadHandle != 0;
    }

    /** Field under the finger (0..8) or -1. */
    public int getGridHoverCell() {
        return gridHoverCell;
    }

    /** Field that is clicked right now (0..8) or -1. */
    public int getGridPressedCell() {
        return gridPressedCell;
    }

    /** The grid overlay is shown while a finger is on the pad and shortly after lifting it. */
    public boolean isGridOverlayVisible() {
        return isGridModeActive() && System.currentTimeMillis() - gridLastTouchMs < 250L;
    }

    private int gridCellAt(float x, float y, int previous) {
        if (previous >= 0) {
            // Stay in the previous cell until the finger is clearly past its border (no flicker)
            int pc = previous % 3;
            int pr = previous / 3;
            float m = GRID_HYSTERESIS;
            if (x >= pc / 3.0f - m && x <= (pc + 1) / 3.0f + m && y >= pr / 3.0f - m && y <= (pr + 1) / 3.0f + m) {
                return previous;
            }
        }
        int col = Math.max(0, Math.min(2, (int) (x * 3.0f)));
        int row = Math.max(0, Math.min(2, (int) (y * 3.0f)));
        return row * 3 + col;
    }

    private void updateTrackpadGrid(Minecraft client, ActionSet set, boolean clicked) {
        long now = System.currentTimeMillis();
        if (padPressure[0] >= 0.0f) {
            gridLastTouchMs = now;
            int cell = gridCellAt(padX[0], padY[0], gridHoverCell);
            if (cell != gridHoverCell) {
                // Steam "touch menu" feel: a light tick whenever the finger crosses into another field
                if (gridHoverCell >= 0 && gridPressedCell < 0) {
                    com.steamdeck.mc.power.HapticFeedbackManager.getInstance()
                            .triggerPadHaptic(false, false, (short) 14000, 10);
                }
                gridHoverCell = cell;
                markControllerActivity();
            }
        } else if (!clicked) {
            gridHoverCell = -1;
        }

        if (clicked != gridClickWasDown) {
            com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerTrackpadClickHaptic(false, clicked);
            if (clicked) {
                markControllerActivity();
                // The field is locked at the moment of the click, sliding while held does not switch it
                gridPressedCell = gridHoverCell;
            }
        }
        gridClickWasDown = clicked;

        for (int i = 0; i < 9; i++) {
            processButton(DeckButton.gridCell(i), clicked && gridPressedCell == i, client, set);
        }
        if (!clicked) gridPressedCell = -1;
    }

    /** Releases a held grid field (grid mode switched off, screen opened, controller changed). */
    private void releaseTrackpadGrid(Minecraft client, ActionSet set) {
        for (int i = 0; i < 9; i++) {
            DeckButton b = DeckButton.gridCell(i);
            if (Boolean.TRUE.equals(prevButtonStates.get(b))) processButton(b, false, client, set);
        }
        gridPressedCell = -1;
        gridClickWasDown = false;
        if (!isGridModeActive() || padPressure[0] < 0.0f) gridHoverCell = -1;
    }

    // On-screen keyboard: time of the last touch per pad (SteamOS keeps the pointer 100 ms after lifting)
    private final long[] oskLastTouchNanos = {0L, 0L};

    /**
     * Absolute trackpad pointers for the on-screen keyboard, like SteamOS (FINDINGS 3): the raw pad
     * position is mapped linearly onto the pad's half of the keyboard, without smoothing; the pointer
     * disappears 100 ms after the finger lifts (bridges single dropped touch reports).
     * Typing happens through the pad click (hardware or click pressure) in processButton().
     */
    private void pollTrackpadKeyboard() {
        long now = System.nanoTime();
        VirtualKeyboard osk = VirtualKeyboard.getInstance();
        for (int side = 0; side < 2; side++) {
            if (padPressure[side] >= 0.0f) {
                oskLastTouchNanos[side] = now;
                markControllerActivity();
                osk.setPadCursor(side == 1, padX[side], padY[side], true);
            } else if (now - oskLastTouchNanos[side] > 100_000_000L) {
                osk.setPadCursor(side == 1, 0, 0, false);
            }
        }
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
        if (client == null || !inputFocused) return;
        noteFrame(System.nanoTime());
        if (activeGamepadHandle != 0) {
            SDLGamepad.SDL_UpdateGamepads();
            updateTrackpadPressure();
            if (VirtualKeyboard.getInstance().isOpen() && activeKind.isNativeValve()) {
                // SteamOS-style typing: both pads point at their half of the keyboard
                cursorPad.reset();
                pollTrackpadKeyboard();
            } else {
                pollTrackpadCursor(client);
            }
        }
        if (!VirtualKeyboard.getInstance().isOpen()) return;

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
