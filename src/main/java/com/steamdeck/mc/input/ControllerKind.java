package com.steamdeck.mc.input;

/**
 * Identifies the connected controller by its USB/Bluetooth vendor & product id.
 *
 * <p>"Native" controllers are read directly by SDL (HIDAPI/evdev driver) without Steam Input in
 * between. That is the case on a desktop without Steam, or when Steam Input is disabled for the
 * game. When Steam Input is active, Steam hides the physical device and games only see the
 * "Steam Virtual Gamepad".</p>
 */
public enum ControllerKind {
    STEAM_DECK("Steam Deck", true, true, 2),
    STEAM_CONTROLLER_2026("Steam Controller (2026)", true, true, 2),
    STEAM_CONTROLLER_2015("Steam Controller (2015)", true, true, 2),
    PS5_DUALSENSE("PS5 DualSense", false, true, 1),
    PS5_DUALSENSE_EDGE("PS5 DualSense Edge", false, true, 1),
    PS4_DUALSHOCK("PS4 DualShock 4", false, true, 1),
    XBOX("Xbox Controller", false, true, 0),
    SWITCH_PRO("Switch Pro Controller", false, true, 0),
    STEAM_VIRTUAL("Steam Input (virtuell)", false, false, 0),
    OTHER("Gamepad", false, false, 0),
    NONE("Kein Controller", false, false, 0);

    public static final int VALVE_VENDOR_ID = 0x28DE;
    public static final int SONY_VENDOR_ID = 0x054C;
    public static final int MICROSOFT_VENDOR_ID = 0x045E;
    public static final int NINTENDO_VENDOR_ID = 0x057E;

    private final String label;
    private final boolean nativeValve;
    private final boolean nativeDirect;
    private final int physicalTrackpads;

    ControllerKind(String label, boolean nativeValve, boolean nativeDirect, int physicalTrackpads) {
        this.label = label;
        this.nativeValve = nativeValve;
        this.nativeDirect = nativeDirect;
        this.physicalTrackpads = physicalTrackpads;
    }

    public String getLabel() {
        return label;
    }

    /** True if this is a physical Valve controller read directly (pressure pads, Valve HIDAPI). */
    public boolean isNativeValve() {
        return nativeValve;
    }

    /** True if this is a physical controller read directly by SDL without Steam Input in between. */
    public boolean isNativeController() {
        return nativeDirect;
    }

    /** True if this is a Sony PS5 DualSense or DualSense Edge. */
    public boolean isDualSense() {
        return this == PS5_DUALSENSE || this == PS5_DUALSENSE_EDGE;
    }

    /** True if this is a Sony PlayStation controller (PS5 DualSense / Edge or PS4 DualShock 4). */
    public boolean isPlayStation() {
        return isDualSense() || this == PS4_DUALSHOCK;
    }

    /** True if the controller has at least one SDL-readable capacitive touchpad. */
    public boolean hasNativeTouchpad() {
        return nativeDirect && physicalTrackpads > 0;
    }

    /** True if the controller has left + right physical trackpads (Valve Deck / Steam Controller). */
    public boolean hasDualTouchpads() {
        return nativeDirect && physicalTrackpads >= 2;
    }

    /** True if the controller supports DualSense adaptive trigger resistance via SDL_SendGamepadEffect. */
    public boolean hasAdaptiveTriggers() {
        return isDualSense();
    }

    public int getPhysicalTrackpads() {
        return physicalTrackpads;
    }

    /** Lower value = preferred when several gamepads are connected. */
    public int priority() {
        return switch (this) {
            case STEAM_DECK, STEAM_CONTROLLER_2026 -> 0;
            case PS5_DUALSENSE, PS5_DUALSENSE_EDGE, PS4_DUALSHOCK, XBOX, SWITCH_PRO, STEAM_CONTROLLER_2015 -> 1;
            case STEAM_VIRTUAL -> 2;
            case OTHER -> 3;
            case NONE -> 9;
        };
    }

    public static ControllerKind fromIds(int vendor, int product) {
        if (vendor == SONY_VENDOR_ID) {
            return switch (product) {
                case 0x0CE6 -> PS5_DUALSENSE;
                case 0x0DF2 -> PS5_DUALSENSE_EDGE;
                case 0x05C4, 0x09CC, 0x0BA0 -> PS4_DUALSHOCK;
                default -> OTHER;
            };
        }
        if (vendor == MICROSOFT_VENDOR_ID) {
            return switch (product) {
                case 0x028E, 0x028F, 0x02D1, 0x02DD, 0x02E0, 0x02E3, 0x02EA, 0x02FD,
                     0x0B00, 0x0B05, 0x0B12, 0x0B13, 0x0B20, 0x0B22 -> XBOX;
                default -> OTHER;
            };
        }
        if (vendor == NINTENDO_VENDOR_ID) {
            if (product == 0x2009) return SWITCH_PRO;
            return OTHER;
        }
        if (vendor != VALVE_VENDOR_ID) {
            return OTHER;
        }
        return switch (product) {
            case 0x1205 -> STEAM_DECK;
            // 0x1302 wired, 0x1303 Bluetooth, 0x1304 wireless puck/dongle, 0x1305 (seen in hid-steam ids)
            case 0x1302, 0x1303, 0x1304, 0x1305 -> STEAM_CONTROLLER_2026;
            // 0x1102 wired, 0x1142 wireless dongle, 0x1106 Bluetooth
            case 0x1102, 0x1142, 0x1106 -> STEAM_CONTROLLER_2015;
            case 0x11FF -> STEAM_VIRTUAL;
            default -> OTHER;
        };
    }
}
