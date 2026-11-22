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
import java.util.List;
import java.util.Map;

/**
 * Mod configuration persisted as JSON in the config directory.
 * Houses settings for Action Sets, Power/Eco, Input/Controller, Contextual Gyro, and Debugging.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE;

    // Device mode: "AUTO" (detect handheld hardware), "HANDHELD" or "DESKTOP".
    // In DESKTOP mode all handheld power/performance features below are inactive.
    public String deviceMode = "AUTO";

    // Power & Eco settings (only active in HANDHELD mode)
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
    /** Popup when a controller connects / disconnects / runs low on battery (like Steam). */
    public boolean showControllerToasts = true;

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
    // Auto: Steam Input's per-controller default (Steam Controller 2026: 12.5 %, Steam Deck: 25 %, else stickDeadzone)
    public boolean stickDeadzoneAuto = true;
    // On-screen keyboard: like SteamOS "TrackPadTypingInputScale" (1.0 .. 2.0); >1 reaches the outer keys earlier
    public float oskTrackpadScale = 1.0f;
    // Config format version (for one-time migrations)
    public int configVersion = 0;
    public boolean enable8WayStickMapping = true;
    public float trackpadSensitivity = 1.0f; // Native trackpad camera: 1.0 = one full swipe across the pad turns ~90 degrees
    public boolean enableHotbarHaptics = true; // Haptic rumble on trackpad per hotbar scroll
    // Trackpad click detection (like Steam Input "click pressure"): "HARDWARE" = the pad's own click,
    // "PRESSURE" = click as soon as the measured pressure reaches the threshold below (0..1, per pad)
    public String trackpadClickMode = null; // legacy (1.2.0), migrated to the per-pad values below
    // Default "PRESSURE": the firmware click bit misses about half the presses while the thumb stays
    // on the pad (SteamlessController measurement, FINDINGS 10.1)
    public String leftTrackpadClickMode = "PRESSURE";
    public String rightTrackpadClickMode = "PRESSURE";
    // Left trackpad: "BUTTON" = one button (Trackpad L), "DPAD_MOVE" = click-D-Pad that walks (like Steam's D-Pad mode)
    public String leftTrackpadMode = "BUTTON";
    // Pushing forward (stick or pad D-Pad) also holds sprint (Steam layouts often bind "Ctrl + W" to north)
    public boolean sprintOnForward = false;
    // Steam Input Soft_Press default activation_threshold 4096/32768 = 12.5 % (= firmware click point)
    public float leftTrackpadClickPressure = 0.125f;
    public float rightTrackpadClickPressure = 0.125f;
    // Trackpad haptic intensity per pad: 0 = off, 1 = very weak ... 3 = normal ... 5 = very strong
    public int leftTrackpadHapticLevel = 3;
    public int rightTrackpadHapticLevel = 3;
    public boolean enableTrackpadClickHaptics = true; // Click feel on trackpad press/release (native Valve controllers, no Steam Input)
    public boolean enableRightStickRadialMenu = true; // 3-page Radial Menu on Right Joystick
    public float rightStickCameraSpeed = 180.0f; // Degrees per second at full right-stick deflection (when the radial menu is off)

    // Native controller support without Steam (Steam Controller 2026 / 2015, Steam Deck, PS5 DualSense)
    public boolean enableNativeSteamControllers = true; // Enables SDL's HIDAPI driver for Steam Controllers
    public boolean nativeTrackpadCamera = true; // Right trackpad turns the camera when Steam Input is NOT active
    // Single center touchpad (e.g. PS5 DualSense): "CAMERA", "BUTTON", "OFF"
    public String centerTrackpadMode = "CAMERA";
    public boolean centerTrackpadMenuMouse = true; // Center touchpad acts as mouse cursor in menus by default
    public float leftTriggerThreshold = 0.30f; // Analog trigger pull threshold (0.05..0.95)
    public float rightTriggerThreshold = 0.30f;
    // DualSense adaptive triggers: "WEAPON_CLICK", "RESISTANCE", "OFF"
    public String dualSenseTriggerMode = "WEAPON_CLICK";
    public float dualSenseTriggerResistance = 0.65f; // Resistance force (0.10..1.00)
    public boolean adaptiveTriggerBlockHardness = true; // R2 resistance scales with mined block hardness
    public boolean adaptiveTriggerBowTension = true; // R2 progressive tension when drawing bow/crossbow/trident
    public boolean adaptiveTriggerShield = true; // L2 firm resistance when blocking with a shield
    public boolean adaptiveTriggerLowDurability = true; // R2 heavy click warning when held tool < 10% durability
    public boolean steamInputBridgeOnDesktop = false; // Intercept the Steam Input bridge keys ([ ] \ / F13 F14) in DESKTOP mode too

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
    /** Fixes the mirrored left/right gyro of the Steam Controller 2026 under SDL (on by default). */
    public boolean tritonGyroYawCorrection = true;
    /** Right trackpad pointer/camera glides between pad samples (smooth at any frame rate). */
    public boolean trackpadInterpolation = false;
    /** Switch off the Steam Controller's keyboard/mouse emulation (B = Escape, pad = mouse) while in game. */
    public boolean disableLizardMode = true;
    /** Ignore the controller while Minecraft is not the focused window (desktop gets it back). */
    public boolean releaseControllerWhenUnfocused = true;
    /** Chat position: AUTO (top while the controller is used, for the on-screen keyboard), TOP, BOTTOM. */
    public String chatLayout = "AUTO";
    /** Configurator drawing: pixel version of Steam's controller outlines (false = own simple drawing). */
    public boolean originalControllerArt = true;
    /** 3x3 grid: ignore touches on the top row that come from the thumb resting on the left stick. */
    public boolean gridTouchCancel = true;
    /** Shows the live pad sample rate / frame rate line (debugging). */
    public boolean trackpadDebugOverlay = false;
    public boolean aimItemsOnly = true; // legacy, see gyroActivation
    // When the gyro turns the camera: "AIM_ONLY" (drawing bow/crossbow/trident/spyglass), "ALWAYS",
    // "TRACKPAD_TOUCH" (finger on either trackpad), "RIGHT_STICK_TOUCH" (capacitive right stick)
    public String gyroActivation = "AIM_ONLY";

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

    // Action Sets: Map<ActionSetName, Map<ButtonName, ActionName>> (live working copy of activeProfile)
    public Map<String, Map<String, String>> actionSets = new HashMap<>();

    // Per-controller active profile IDs: Map<ControllerKind.name(), profileId>
    public Map<String, String> activeProfiles = new HashMap<>();

    private transient ControllerProfile activeProfile;
    private transient com.steamdeck.mc.input.ControllerKind activeProfileKind = com.steamdeck.mc.input.ControllerKind.STEAM_DECK;

    public com.steamdeck.mc.platform.DeviceMode getDeviceMode() {
        return com.steamdeck.mc.platform.DeviceMode.parse(this.deviceMode);
    }

    public void setDeviceMode(com.steamdeck.mc.platform.DeviceMode mode) {
        this.deviceMode = mode.name();
        save();
    }

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
                    config.migrateOrLoadInitialProfile();
                    config.save(); // writes newly added options into existing config files
                    return config;
                }
            } catch (Exception e) {
                SteamDeckEssentials.LOGGER.warn("Failed to load config, fallback to default", e);
            }
        }
        ModConfig config = new ModConfig();
        config.configVersion = 2;
        config.initDefaultActionSets();
        config.migrateOrLoadInitialProfile();
        config.save();
        return config;
    }

    /**
     * Preserves any existing user-configured layout on first upgrade by saving it into
     * {@code profiles/user_default.json} for Valve controllers, without overwriting it.
     */
    private void migrateOrLoadInitialProfile() {
        if (this.activeProfiles == null) this.activeProfiles = new HashMap<>();
        if (this.activeProfiles.isEmpty()) {
            ControllerProfile migrated = new ControllerProfile();
            migrated.id = "user_default";
            migrated.name = "Mein Layout";
            migrated.parityGroup = "DUAL_PAD";
            migrated.copyFromConfig(this);
            ControllerProfile.save(migrated);
            this.activeProfiles.put(com.steamdeck.mc.input.ControllerKind.STEAM_DECK.name(), migrated.id);
            this.activeProfiles.put(com.steamdeck.mc.input.ControllerKind.STEAM_CONTROLLER_2026.name(), migrated.id);
            this.activeProfiles.put(com.steamdeck.mc.input.ControllerKind.STEAM_CONTROLLER_2015.name(), migrated.id);
            this.activeProfiles.put(com.steamdeck.mc.input.ControllerKind.STEAM_VIRTUAL.name(), migrated.id);
            this.activeProfile = migrated;
            this.activeProfileKind = com.steamdeck.mc.input.ControllerKind.STEAM_DECK;
            SteamDeckEssentials.LOGGER.info("[PROFILE] Migrated existing layout into profile '{}'", migrated.name);
        } else {
            activateProfileFor(com.steamdeck.mc.input.ControllerKind.STEAM_DECK);
        }
    }

    public synchronized ControllerProfile getActiveProfile() {
        return activeProfile;
    }

    public synchronized com.steamdeck.mc.input.ControllerKind getActiveProfileKind() {
        return activeProfileKind;
    }

    /**
     * Activates the saved profile for {@code kind} (creating a controller-appropriate default on first
     * sight of that controller type). Saves the previous profile before switching.
     */
    public synchronized void activateProfileFor(com.steamdeck.mc.input.ControllerKind kind) {
        if (kind == null || kind == com.steamdeck.mc.input.ControllerKind.NONE) return;
        if (this.activeProfile != null && kind == this.activeProfileKind) return;
        if (this.activeProfile != null) {
            this.activeProfile.copyFromConfig(this);
            ControllerProfile.save(this.activeProfile);
        }
        if (this.activeProfiles == null) this.activeProfiles = new HashMap<>();

        String profileId = this.activeProfiles.get(kind.name());
        ControllerProfile loaded = ControllerProfile.load(profileId);
        if (loaded == null) {
            // Controllers with full parity share the default ID if one already exists
            String defaultId = switch (ControllerProfile.parityGroupFor(kind)) {
                case "SINGLE_PAD" -> "ps5_default";
                case "STANDARD" -> "gamepad_default";
                default -> this.activeProfiles.getOrDefault(
                        com.steamdeck.mc.input.ControllerKind.STEAM_DECK.name(), "deck_default");
            };
            loaded = ControllerProfile.load(defaultId);
            if (loaded == null) {
                loaded = ControllerProfile.createDefaultFor(kind, this);
                ControllerProfile.save(loaded);
            }
            this.activeProfiles.put(kind.name(), loaded.id);
        }
        this.activeProfile = loaded;
        this.activeProfileKind = kind;
        loaded.applyToConfig(this);
        SteamDeckEssentials.LOGGER.info("[PROFILE] Activated '{}' ({}) for {}",
                loaded.name, loaded.id, kind.getLabel());
        saveConfigOnly();
    }

    /** Switches the active profile for {@code kind} to an existing profile ID. */
    public synchronized boolean switchProfile(com.steamdeck.mc.input.ControllerKind kind, String profileId) {
        if (kind == null || kind == com.steamdeck.mc.input.ControllerKind.NONE) {
            kind = this.activeProfileKind;
        }
        ControllerProfile target = ControllerProfile.load(profileId);
        if (target == null) return false;
        if (this.activeProfile != null) {
            this.activeProfile.copyFromConfig(this);
            ControllerProfile.save(this.activeProfile);
        }
        if (this.activeProfiles == null) this.activeProfiles = new HashMap<>();
        this.activeProfiles.put(kind.name(), target.id);
        this.activeProfile = target;
        this.activeProfileKind = kind;
        target.applyToConfig(this);
        saveConfigOnly();
        return true;
    }

    /** Saves a copy of the current layout under a new profile name and activates it. */
    public synchronized ControllerProfile duplicateActiveProfile(com.steamdeck.mc.input.ControllerKind kind, String newName) {
        if (kind == null || kind == com.steamdeck.mc.input.ControllerKind.NONE) {
            kind = this.activeProfileKind;
        }
        String baseId = ControllerProfile.sanitizeId(newName);
        String id = baseId;
        int n = 2;
        while (ControllerProfile.load(id) != null) {
            id = baseId + "_" + n++;
        }
        ControllerProfile copy = new ControllerProfile();
        copy.id = id;
        copy.name = newName;
        copy.parityGroup = ControllerProfile.parityGroupFor(kind);
        copy.copyFromConfig(this);
        ControllerProfile.save(copy);
        if (this.activeProfiles == null) this.activeProfiles = new HashMap<>();
        this.activeProfiles.put(kind.name(), copy.id);
        this.activeProfile = copy;
        this.activeProfileKind = kind;
        saveConfigOnly();
        return copy;
    }

    /** Deletes a profile by ID (refuses if it is the last remaining profile). */
    public synchronized boolean deleteProfile(String profileId) {
        List<ControllerProfile> all = ControllerProfile.listAll();
        if (all.size() <= 1) return false;
        if (!ControllerProfile.delete(profileId)) return false;
        if (this.activeProfiles != null) {
            this.activeProfiles.entrySet().removeIf(e -> profileId.equals(e.getValue()));
        }
        if (this.activeProfile != null && profileId.equals(this.activeProfile.id)) {
            ControllerProfile fallback = ControllerProfile.listAll().get(0);
            this.activeProfile = fallback;
            if (this.activeProfiles != null) {
                this.activeProfiles.put(this.activeProfileKind.name(), fallback.id);
            }
            fallback.applyToConfig(this);
        }
        saveConfigOnly();
        return true;
    }

    public synchronized void save() {
        if (this.activeProfile != null) {
            this.activeProfile.copyFromConfig(this);
            ControllerProfile.save(this.activeProfile);
        }
        saveConfigOnly();
    }

    private void saveConfigOnly() {
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
        } else {
            // IN_GAME (Default)
            return switch (button) {
                case A, R5 -> DeckAction.JUMP;
                case B -> DeckAction.DROP;
                case X -> DeckAction.SWAP_OFFHAND;
                case Y -> DeckAction.INVENTORY;
                case LB -> DeckAction.NONE; // was the (non-vanilla) zoom
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
                // 3x3 grid on the left trackpad: hotbar slots 1-9
                default -> button.gridIndex() >= 0 ? DeckAction.hotbarSlot(button.gridIndex()) : DeckAction.NONE;
            };
        }
    }

    private void validate() {
        this.deviceMode = com.steamdeck.mc.platform.DeviceMode.parse(this.deviceMode).name();
        if (this.menuFpsCap < 10) this.menuFpsCap = 10;
        if (this.acMenuFpsCap < 10) this.acMenuFpsCap = 10;
        if (this.acChunkThreads < 1) this.acChunkThreads = 1;
        if (this.minAfkFps < 5) this.minAfkFps = 5;
        if (this.acMinAfkFps < 5) this.acMinAfkFps = 5;
        this.maxAfkThrottle = clamp(this.maxAfkThrottle, 0.0f, 0.95f);
        this.acMaxAfkThrottle = clamp(this.acMaxAfkThrottle, 0.0f, 0.95f);
        this.maxAfkDimAlpha = clamp(this.maxAfkDimAlpha, 0.0f, 0.95f);
        this.acMaxAfkDimAlpha = clamp(this.acMaxAfkDimAlpha, 0.0f, 0.95f);
        this.stickDeadzone = clamp(this.stickDeadzone, 0.0f, 0.9f);
        this.gyroSensitivityX = clamp(this.gyroSensitivityX, 0.0f, 10.0f);
        this.gyroSensitivityY = clamp(this.gyroSensitivityY, 0.0f, 10.0f);
        this.rightStickCameraSpeed = clamp(this.rightStickCameraSpeed, 10.0f, 1000.0f);
        this.trackpadSensitivity = clamp(this.trackpadSensitivity, 0.1f, 10.0f);
        if ("PRESSURE".equals(this.trackpadClickMode)) {
            this.leftTrackpadClickMode = "PRESSURE";
            this.rightTrackpadClickMode = "PRESSURE";
        }
        this.trackpadClickMode = null;
        if (!"PRESSURE".equals(this.leftTrackpadClickMode)) this.leftTrackpadClickMode = "HARDWARE";
        if (!"PRESSURE".equals(this.rightTrackpadClickMode)) this.rightTrackpadClickMode = "HARDWARE";
        if (!"DPAD_MOVE".equals(this.leftTrackpadMode) && !"GRID_3X3".equals(this.leftTrackpadMode)) {
            this.leftTrackpadMode = "BUTTON";
        }
        if (!java.util.Set.of("AUTO", "TOP", "BOTTOM").contains(this.chatLayout)) this.chatLayout = "AUTO";
        if (!java.util.Set.of("AIM_ONLY", "ALWAYS", "TRACKPAD_TOUCH", "RIGHT_STICK_TOUCH").contains(this.gyroActivation)) {
            this.gyroActivation = "AIM_ONLY";
        }
        if (!this.aimItemsOnly && "AIM_ONLY".equals(this.gyroActivation)) {
            this.gyroActivation = "ALWAYS";
            this.aimItemsOnly = true;
        }
        if (this.configVersion < 2) {
            // 1.4.1: pressure-based clicks and Steam's 12.5 % threshold become the default.
            // Old built-in defaults (45 % / 30 % from the workshop preset) were far above real presses.
            if (this.leftTrackpadClickPressure == 0.45f || this.leftTrackpadClickPressure == 0.30f) this.leftTrackpadClickPressure = 0.125f;
            if (this.rightTrackpadClickPressure == 0.45f || this.rightTrackpadClickPressure == 0.30f) this.rightTrackpadClickPressure = 0.125f;
            this.leftTrackpadClickMode = "PRESSURE";
            this.rightTrackpadClickMode = "PRESSURE";
            this.configVersion = 2;
        }
        this.oskTrackpadScale = clamp(this.oskTrackpadScale, 1.0f, 2.0f);
        this.leftTrackpadClickPressure = clamp(this.leftTrackpadClickPressure, 0.01f, 1.0f);
        this.rightTrackpadClickPressure = clamp(this.rightTrackpadClickPressure, 0.01f, 1.0f);
        this.leftTrackpadHapticLevel = Math.max(0, Math.min(5, this.leftTrackpadHapticLevel));
        this.rightTrackpadHapticLevel = Math.max(0, Math.min(5, this.rightTrackpadHapticLevel));
        if (!java.util.Set.of("CAMERA", "BUTTON", "OFF").contains(this.centerTrackpadMode)) {
            this.centerTrackpadMode = "CAMERA";
        }
        this.leftTriggerThreshold = clamp(this.leftTriggerThreshold, 0.05f, 0.95f);
        this.rightTriggerThreshold = clamp(this.rightTriggerThreshold, 0.05f, 0.95f);
        if (!java.util.Set.of("WEAPON_CLICK", "RESISTANCE", "OFF").contains(this.dualSenseTriggerMode)) {
            this.dualSenseTriggerMode = "WEAPON_CLICK";
        }
        this.dualSenseTriggerResistance = clamp(this.dualSenseTriggerResistance, 0.10f, 1.0f);
        if (this.performanceTrackIntervalSeconds < 1) this.performanceTrackIntervalSeconds = 1;
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

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
