package com.steamdeck.mc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.input.ActionSet;
import com.steamdeck.mc.input.ControllerKind;
import com.steamdeck.mc.input.DeckButton;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-controller input & binding profile persisted under
 * {@code config/steamdeck_essentials/profiles/<id>.json}.
 *
 * <p>Controllers with matching parity ({@link #parityGroupFor(ControllerKind)}) are 1:1 compatible.
 * Loading a profile on a controller with fewer buttons preserves all unmapped bindings in the file
 * while ignoring unsupported physical buttons at runtime.</p>
 */
public class ControllerProfile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String id = "default";
    public String name = "Standard";
    public String parityGroup = "DUAL_PAD";

    // Action Sets
    public Map<String, Map<String, String>> actionSets = new HashMap<>();

    // Sticks & Movement
    public float stickDeadzone = 0.25f;
    public boolean stickDeadzoneAuto = true;
    public boolean enable8WayStickMapping = true;
    public boolean sprintOnForward = false;
    public boolean enableRightStickRadialMenu = true;
    public float rightStickCameraSpeed = 180.0f;

    // Trackpads & Haptics
    public float trackpadSensitivity = 1.0f;
    public boolean enableHotbarHaptics = true;
    public String leftTrackpadClickMode = "PRESSURE";
    public String rightTrackpadClickMode = "PRESSURE";
    public String leftTrackpadMode = "BUTTON";
    public float leftTrackpadClickPressure = 0.125f;
    public float rightTrackpadClickPressure = 0.125f;
    public int leftTrackpadHapticLevel = 3;
    public int rightTrackpadHapticLevel = 3;
    public boolean enableTrackpadClickHaptics = true;
    public boolean nativeTrackpadCamera = true;
    public String centerTrackpadMode = "CAMERA";
    public boolean centerTrackpadMenuMouse = true;
    public boolean trackpadInterpolation = false;
    public boolean gridTouchCancel = true;

    // Triggers (analog & DualSense adaptive)
    public float leftTriggerThreshold = 0.30f;
    public float rightTriggerThreshold = 0.30f;
    public String dualSenseTriggerMode = "WEAPON_CLICK";
    public float dualSenseTriggerResistance = 0.65f;
    public boolean adaptiveTriggerBlockHardness = true;
    public boolean adaptiveTriggerBowTension = true;
    public boolean adaptiveTriggerShield = true;
    public boolean adaptiveTriggerLowDurability = true;

    // Gyro
    public boolean enableGyroAiming = true;
    public float gyroSensitivityX = 1.0f;
    public float gyroSensitivityY = 1.0f;
    public boolean invertGyroX = false;
    public boolean invertGyroY = false;
    public String gyroActivation = "AIM_ONLY";

    public static String parityGroupFor(ControllerKind kind) {
        if (kind == null) return "DUAL_PAD";
        if (kind.isDualSense()) return "SINGLE_PAD";
        if (kind == ControllerKind.OTHER) return "STANDARD";
        return "DUAL_PAD"; // STEAM_DECK, STEAM_CONTROLLER_2026, STEAM_CONTROLLER_2015, STEAM_VIRTUAL
    }

    public static String parityLabel(String group) {
        return switch (group) {
            case "DUAL_PAD" -> "Steam Deck / Steam Controller";
            case "SINGLE_PAD" -> "PS5 DualSense";
            default -> "Standard-Gamepad";
        };
    }

    public boolean isExactParity(ControllerKind kind) {
        return parityGroupFor(kind).equals(this.parityGroup);
    }

    public void copyFromConfig(ModConfig c) {
        this.actionSets = deepCopyActionSets(c.actionSets);
        this.stickDeadzone = c.stickDeadzone;
        this.stickDeadzoneAuto = c.stickDeadzoneAuto;
        this.enable8WayStickMapping = c.enable8WayStickMapping;
        this.sprintOnForward = c.sprintOnForward;
        this.enableRightStickRadialMenu = c.enableRightStickRadialMenu;
        this.rightStickCameraSpeed = c.rightStickCameraSpeed;
        this.trackpadSensitivity = c.trackpadSensitivity;
        this.enableHotbarHaptics = c.enableHotbarHaptics;
        this.leftTrackpadClickMode = c.leftTrackpadClickMode;
        this.rightTrackpadClickMode = c.rightTrackpadClickMode;
        this.leftTrackpadMode = c.leftTrackpadMode;
        this.leftTrackpadClickPressure = c.leftTrackpadClickPressure;
        this.rightTrackpadClickPressure = c.rightTrackpadClickPressure;
        this.leftTrackpadHapticLevel = c.leftTrackpadHapticLevel;
        this.rightTrackpadHapticLevel = c.rightTrackpadHapticLevel;
        this.enableTrackpadClickHaptics = c.enableTrackpadClickHaptics;
        this.nativeTrackpadCamera = c.nativeTrackpadCamera;
        this.centerTrackpadMode = c.centerTrackpadMode;
        this.centerTrackpadMenuMouse = c.centerTrackpadMenuMouse;
        this.trackpadInterpolation = c.trackpadInterpolation;
        this.gridTouchCancel = c.gridTouchCancel;
        this.leftTriggerThreshold = c.leftTriggerThreshold;
        this.rightTriggerThreshold = c.rightTriggerThreshold;
        this.dualSenseTriggerMode = c.dualSenseTriggerMode;
        this.dualSenseTriggerResistance = c.dualSenseTriggerResistance;
        this.adaptiveTriggerBlockHardness = c.adaptiveTriggerBlockHardness;
        this.adaptiveTriggerBowTension = c.adaptiveTriggerBowTension;
        this.adaptiveTriggerShield = c.adaptiveTriggerShield;
        this.adaptiveTriggerLowDurability = c.adaptiveTriggerLowDurability;
        this.enableGyroAiming = c.enableGyroAiming;
        this.gyroSensitivityX = c.gyroSensitivityX;
        this.gyroSensitivityY = c.gyroSensitivityY;
        this.invertGyroX = c.invertGyroX;
        this.invertGyroY = c.invertGyroY;
        this.gyroActivation = c.gyroActivation;
    }

    public void applyToConfig(ModConfig c) {
        c.actionSets = deepCopyActionSets(this.actionSets);
        c.stickDeadzone = this.stickDeadzone;
        c.stickDeadzoneAuto = this.stickDeadzoneAuto;
        c.enable8WayStickMapping = this.enable8WayStickMapping;
        c.sprintOnForward = this.sprintOnForward;
        c.enableRightStickRadialMenu = this.enableRightStickRadialMenu;
        c.rightStickCameraSpeed = this.rightStickCameraSpeed;
        c.trackpadSensitivity = this.trackpadSensitivity;
        c.enableHotbarHaptics = this.enableHotbarHaptics;
        c.leftTrackpadClickMode = this.leftTrackpadClickMode;
        c.rightTrackpadClickMode = this.rightTrackpadClickMode;
        c.leftTrackpadMode = this.leftTrackpadMode;
        c.leftTrackpadClickPressure = this.leftTrackpadClickPressure;
        c.rightTrackpadClickPressure = this.rightTrackpadClickPressure;
        c.leftTrackpadHapticLevel = this.leftTrackpadHapticLevel;
        c.rightTrackpadHapticLevel = this.rightTrackpadHapticLevel;
        c.enableTrackpadClickHaptics = this.enableTrackpadClickHaptics;
        c.nativeTrackpadCamera = this.nativeTrackpadCamera;
        c.centerTrackpadMode = this.centerTrackpadMode;
        c.centerTrackpadMenuMouse = this.centerTrackpadMenuMouse;
        c.trackpadInterpolation = this.trackpadInterpolation;
        c.gridTouchCancel = this.gridTouchCancel;
        c.leftTriggerThreshold = this.leftTriggerThreshold;
        c.rightTriggerThreshold = this.rightTriggerThreshold;
        c.dualSenseTriggerMode = this.dualSenseTriggerMode;
        c.dualSenseTriggerResistance = this.dualSenseTriggerResistance;
        c.adaptiveTriggerBlockHardness = this.adaptiveTriggerBlockHardness;
        c.adaptiveTriggerBowTension = this.adaptiveTriggerBowTension;
        c.adaptiveTriggerShield = this.adaptiveTriggerShield;
        c.adaptiveTriggerLowDurability = this.adaptiveTriggerLowDurability;
        c.enableGyroAiming = this.enableGyroAiming;
        c.gyroSensitivityX = this.gyroSensitivityX;
        c.gyroSensitivityY = this.gyroSensitivityY;
        c.invertGyroX = this.invertGyroX;
        c.invertGyroY = this.invertGyroY;
        c.gyroActivation = this.gyroActivation;
        c.initDefaultActionSets();
    }

    public static ControllerProfile createDefaultFor(ControllerKind kind, ModConfig defaultsSource) {
        String group = parityGroupFor(kind);
        ControllerProfile p = new ControllerProfile();
        if ("SINGLE_PAD".equals(group)) {
            p.id = "ps5_default";
            p.name = "PS5 DualSense Standard";
            p.parityGroup = "SINGLE_PAD";
            p.enableRightStickRadialMenu = false; // Right stick = camera on DualSense by default
            p.centerTrackpadMode = "CAMERA";
            p.centerTrackpadMenuMouse = true;
        } else if ("STANDARD".equals(group)) {
            p.id = "gamepad_default";
            p.name = "Gamepad Standard";
            p.parityGroup = "STANDARD";
            p.enableRightStickRadialMenu = false; // Right stick = camera on standard gamepads
        } else {
            p.id = "deck_default";
            p.name = "Steam Deck Standard";
            p.parityGroup = "DUAL_PAD";
            p.enableRightStickRadialMenu = true;
        }
        p.actionSets = new HashMap<>();
        for (ActionSet set : ActionSet.values()) {
            Map<String, String> map = new HashMap<>();
            for (DeckButton btn : DeckButton.values()) {
                map.put(btn.name(), defaultsSource.getDefaultAction(set, btn).name());
            }
            p.actionSets.put(set.name(), map);
        }
        return p;
    }

    private static Map<String, Map<String, String>> deepCopyActionSets(Map<String, Map<String, String>> src) {
        Map<String, Map<String, String>> copy = new HashMap<>();
        if (src == null) return copy;
        for (var e : src.entrySet()) {
            copy.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return copy;
    }

    public static Path getProfilesDir() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("steamdeck_essentials")
                .resolve("profiles");
    }

    public static String sanitizeId(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "profile" : s;
    }

    public static ControllerProfile load(String id) {
        if (id == null || id.isBlank()) return null;
        Path path = getProfilesDir().resolve(sanitizeId(id) + ".json");
        if (!Files.exists(path)) return null;
        try (Reader reader = Files.newBufferedReader(path)) {
            ControllerProfile p = GSON.fromJson(reader, ControllerProfile.class);
            if (p != null) {
                if (p.id == null || p.id.isBlank()) p.id = sanitizeId(id);
                if (p.name == null || p.name.isBlank()) p.name = p.id;
                if (p.parityGroup == null || p.parityGroup.isBlank()) p.parityGroup = "DUAL_PAD";
                if (p.actionSets == null) p.actionSets = new HashMap<>();
                return p;
            }
        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.warn("[PROFILE] Failed to load profile '{}'", id, e);
        }
        return null;
    }

    public static void save(ControllerProfile profile) {
        if (profile == null) return;
        profile.id = sanitizeId(profile.id);
        Path dir = getProfilesDir();
        try {
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(dir.resolve(profile.id + ".json"))) {
                GSON.toJson(profile, writer);
            }
        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.warn("[PROFILE] Failed to save profile '{}'", profile.id, e);
        }
    }

    public static boolean delete(String id) {
        if (id == null || id.isBlank()) return false;
        try {
            return Files.deleteIfExists(getProfilesDir().resolve(sanitizeId(id) + ".json"));
        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.warn("[PROFILE] Failed to delete profile '{}'", id, e);
            return false;
        }
    }

    public static List<ControllerProfile> listAll() {
        List<ControllerProfile> out = new ArrayList<>();
        Path dir = getProfilesDir();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                String fname = file.getFileName().toString();
                String id = fname.substring(0, fname.length() - 5);
                ControllerProfile p = load(id);
                if (p != null) out.add(p);
            }
        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.warn("[PROFILE] Failed to list profiles", e);
        }
        out.sort(Comparator.comparing(p -> p.name.toLowerCase(Locale.ROOT)));
        return out;
    }
}
