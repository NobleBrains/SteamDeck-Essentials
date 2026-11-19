package com.steamdeck.mc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.input.ActionBinding;
import com.steamdeck.mc.input.ActionSet;
import com.steamdeck.mc.input.DeckAction;
import com.steamdeck.mc.input.DeckButton;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Mod configuration persisted as JSON in the config directory.
 * Houses settings for Action Sets, Power/Eco, Input/Controller, Contextual Gyro, and Debugging.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE;

    // Power & Eco settings
    public int menuFpsCap = 30;
    public int acMenuFpsCap = 60; // Smoother menus on AC power while keeping fan quiet
    public int unfocusedFpsCap = 10;
    public boolean enableDynamicChunkThreading = true;
    public int batteryChunkThreads = 2;
    public int acChunkThreads = 3; // Controlled chunk threads on AC to hit 90/60 FPS without loud fan
    public boolean enableThermalFanGuard = true; // Caps excessive uncapped FPS at 90 Hz to keep fan silent
    public boolean enableBatteryMonitor = true;
    public boolean enableBatteryHudInPause = true;
    public boolean enableBatteryHudInF3 = true;
    public boolean enableAutoSuspendSave = true;

    // Dynamic AFK & Eco throttling
    public boolean enableDynamicAfkThrottle = true;
    public int afkThrottleStartMinutes = 2; // Starts after 2 minutes on battery
    public int acAfkThrottleStartMinutes = 4; // Starts after 4 minutes on AC power
    public float afkThrottleRatePerMinute = 0.10f; // 10% drop per minute
    public float maxAfkThrottle = 0.80f; // Max 80% performance throttled on battery
    public float acMaxAfkThrottle = 0.50f; // Max 50% throttled on AC power (lighter throttle)
    public int minAfkFps = 15; // Minimum FPS floor on battery
    public int acMinAfkFps = 40; // Minimum FPS floor on AC power
    public boolean enableAfkScreenDimming = true; // OLED battery saver
    public int afkDimStartMinutes = 3; // Starts dimming 1 min after throttle begins
    public int acAfkDimStartMinutes = 5; // Starts dimming later on AC power
    public float maxAfkDimAlpha = 0.65f; // 65% darkness on battery
    public float acMaxAfkDimAlpha = 0.30f; // Subtle 30% dimming on AC power
    public boolean enableAfkChunkWorkerIdle = true; // Idle chunk threads during AFK

    // Input & Controller settings
    public float stickDeadzone = 0.25f;
    public boolean enable8WayStickMapping = true;
    public float trackpadSensitivity = 1.0f;
    public boolean trackpadClickJump = true;
    public boolean enableHotbarHaptics = true; // Haptic rumble on trackpad per hotbar scroll
    public boolean enableRightStickRadialMenu = true; // 3-page Radial Menu on Right Joystick

    // Steam Input Hardware Bridge (Rücktasten & Stick-Touch)
    // Uses Minecraft 26.3 SDL3 Scancodes (InputConstants.KEY_LBRACKET=47, KEY_RBRACKET=48, KEY_BACKSLASH=49, KEY_SLASH=56)
    public int steamBridgeKeyL4 = 47; // '[' (SDL3 Scancode 47)
    public int steamBridgeKeyR4 = 48; // ']' (SDL3 Scancode 48)
    public int steamBridgeKeyL5 = 49; // '\' (SDL3 Scancode 49)
    public int steamBridgeKeyR5 = 56; // '/' (SDL3 Scancode 56)
    public int steamBridgeKeyL3Touch = 104; // F13 (SDL3 Scancode 104)
    public int steamBridgeKeyR3Touch = 105; // F14 (SDL3 Scancode 105)
    public boolean enableInventorySlotSnapping = true;
    public boolean enableControllerMenuHints = true;
    public boolean enableGameplayHaptics = true;
    public boolean enableLowBatterySaver = true;

    // Gyro settings
    public boolean enableGyroAiming = true;
    public float gyroSensitivityX = 1.0f;
    public float gyroSensitivityY = 1.0f;
    public boolean invertGyroX = false;
    public boolean invertGyroY = false;
    public boolean aimItemsOnly = true;

    // Handheld GUI & OSK settings
    public float oskScale = 1.0f;
    public boolean oskSplitMode = true;
    public boolean oskAutoOpenOnInput = true;
    public boolean fixTooltipClipping = true;

    // Debugging & Diagnostics
    public boolean enableDebugLogging = true;
    public boolean enableF3ControllerDebug = true;
    public boolean enablePerformanceTracking = true;
    public int performanceTrackIntervalSeconds = 5;

    // Action Sets: Map<ActionSetName, Map<ButtonName, ActionName>>
    public Map<String, Map<String, String>> actionSets = new HashMap<>();

    public static synchronized ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("steamdeck_essentials.json");
    }

    private static ModConfig load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null) {
                    config.validate();
                    return config;
                }
            } catch (Exception e) {
                SteamDeckEssentials.LOGGER.warn("Failed to load config, fallback to default", e);
            }
        }
        ModConfig config = new ModConfig();
        config.initDefaultActionSets();
        config.save();
        return config;
    }

    public synchronized void save() {
        Path path = getConfigPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.warn("Failed to save config", e);
        }
    }

    public ActionBinding getBinding(ActionSet set, DeckButton button) {
        if (actionSets == null) actionSets = new HashMap<>();
        Map<String, String> setMap = actionSets.get(set.name());
        if (setMap != null) {
            String serialized = setMap.get(button.name());
            if (serialized != null && !serialized.isBlank()) {
                return ActionBinding.deserialize(serialized);
            }
        }
        return new ActionBinding(getDefaultAction(set, button));
    }

    public DeckAction getAction(ActionSet set, DeckButton button) {
        return getBinding(set, button).getAction();
    }

    public void setBinding(ActionSet set, DeckButton button, ActionBinding binding) {
        if (actionSets == null) actionSets = new HashMap<>();
        Map<String, String> setMap = actionSets.computeIfAbsent(set.name(), k -> new HashMap<>());
        setMap.put(button.name(), binding.serialize());
        save();
    }

    public void setAction(ActionSet set, DeckButton button, DeckAction action) {
        setBinding(set, button, new ActionBinding(action));
    }

    public void resetActionSetToDefaults(ActionSet set) {
        if (actionSets == null) actionSets = new HashMap<>();
        Map<String, String> setMap = new HashMap<>();
        for (DeckButton btn : DeckButton.values()) {
            setMap.put(btn.name(), getDefaultAction(set, btn).name());
        }
        actionSets.put(set.name(), setMap);
        save();
    }

    public void initDefaultActionSets() {
        if (actionSets == null) actionSets = new HashMap<>();
        for (ActionSet set : ActionSet.values()) {
            if (!actionSets.containsKey(set.name()) || actionSets.get(set.name()).isEmpty()) {
                Map<String, String> map = new HashMap<>();
                for (DeckButton btn : DeckButton.values()) {
                    map.put(btn.name(), getDefaultAction(set, btn).name());
                }
                actionSets.put(set.name(), map);
            }
        }
    }

    public DeckAction getDefaultAction(ActionSet set, DeckButton button) {
        if (set == ActionSet.MENUS) {
            return switch (button) {
                case A, RT, RIGHT_TRACKPAD -> DeckAction.MOUSE_LEFT_CLICK;
                case X, LT -> DeckAction.MOUSE_RIGHT_CLICK;
                case Y -> DeckAction.MOUSE_QUICK_MOVE;
                case B, START, SELECT -> DeckAction.CLOSE_SCREEN;
                case LB, L4 -> DeckAction.HOTBAR_PREV;
                case RB, R4 -> DeckAction.HOTBAR_NEXT;
                default -> DeckAction.NONE;
            };
        } else if (set == ActionSet.AIMING) {
            return switch (button) {
                case A, R5 -> DeckAction.JUMP;
                case B -> DeckAction.DROP;
                case X -> DeckAction.SWAP_OFFHAND;
                case Y -> DeckAction.INVENTORY;
                case LB -> DeckAction.ZOOM;
                case RB -> DeckAction.PICK_BLOCK;
                case LT -> DeckAction.USE;
                case RT -> DeckAction.ATTACK;
                case L4 -> DeckAction.HOTBAR_PREV;
                case R4 -> DeckAction.HOTBAR_NEXT;
                case L5 -> DeckAction.SNEAK_TOGGLE;
                case L3 -> DeckAction.SPRINT;
                case R3 -> DeckAction.RADIAL_MENU_CYCLE;
                case RIGHT_TRACKPAD -> DeckAction.JUMP;
                case LEFT_TRACKPAD -> DeckAction.OPEN_CHAT;
                case DPAD_UP -> DeckAction.PICK_BLOCK;
                case DPAD_DOWN -> DeckAction.SNEAK_TOGGLE;
                case DPAD_LEFT -> DeckAction.TOGGLE_PERSPECTIVE;
                case DPAD_RIGHT -> DeckAction.OPEN_CHAT;
                case START -> DeckAction.PAUSE;
                case SELECT -> DeckAction.INVENTORY;
                case LEFT_STICK_TOUCH, RIGHT_STICK_TOUCH -> DeckAction.NONE;
            };
        } else {
            // IN_GAME (Default)
            return switch (button) {
                case A, R5 -> DeckAction.JUMP;
                case B -> DeckAction.DROP;
                case X -> DeckAction.SWAP_OFFHAND;
                case Y -> DeckAction.INVENTORY;
                case LB -> DeckAction.ZOOM;
                case RB -> DeckAction.PICK_BLOCK;
                case LT -> DeckAction.USE;
                case RT -> DeckAction.ATTACK;
                case L4 -> DeckAction.HOTBAR_PREV;
                case R4 -> DeckAction.HOTBAR_NEXT;
                case L5 -> DeckAction.SNEAK_TOGGLE;
                case L3 -> DeckAction.SPRINT;
                case R3 -> DeckAction.RADIAL_MENU_CYCLE;
                case RIGHT_TRACKPAD -> DeckAction.JUMP;
                case LEFT_TRACKPAD -> DeckAction.OPEN_CHAT;
                case DPAD_UP -> DeckAction.PICK_BLOCK;
                case DPAD_DOWN -> DeckAction.SNEAK_TOGGLE;
                case DPAD_LEFT -> DeckAction.TOGGLE_PERSPECTIVE;
                case DPAD_RIGHT -> DeckAction.OPEN_CHAT;
                case START -> DeckAction.PAUSE;
                case SELECT -> DeckAction.INVENTORY;
                case LEFT_STICK_TOUCH, RIGHT_STICK_TOUCH -> DeckAction.NONE;
            };
        }
    }

    private void validate() {
        if (this.menuFpsCap < 10) this.menuFpsCap = 10;
        if (this.unfocusedFpsCap < 1) this.unfocusedFpsCap = 5;
        if (this.batteryChunkThreads < 1) this.batteryChunkThreads = 1;
        if (this.oskScale < 0.5f) this.oskScale = 0.5f;
        if (this.oskScale > 2.0f) this.oskScale = 2.0f;
        // Migrate legacy GLFW ASCII codes (91, 93, 92, 47) to SDL3 scancodes (47, 48, 49, 56)
        if (this.steamBridgeKeyL4 == 91 && this.steamBridgeKeyR4 == 93
                && this.steamBridgeKeyL5 == 92 && this.steamBridgeKeyR5 == 47) {
            this.steamBridgeKeyL4 = 47;
            this.steamBridgeKeyR4 = 48;
            this.steamBridgeKeyL5 = 49;
            this.steamBridgeKeyR5 = 56;
        }
        initDefaultActionSets();
    }
}
