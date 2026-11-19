package com.steamdeck.mc.input;

/**
 * Identifies the connected controller by its USB/Bluetooth vendor & product id.
 *
 * <p>"Native" Valve controllers are read directly by SDL (HIDAPI driver) without Steam Input in
 * between. That is the case on a desktop without Steam, or when Steam Input is disabled for the
 * game. When Steam Input is active, Steam hides the physical device and games only see the
 * "Steam Virtual Gamepad".</p>
 */
public enum ControllerKind {
    STEAM_DECK("Steam Deck", true, 2),
    STEAM_CONTROLLER_2026("Steam Controller (2026)", true, 2),
    STEAM_CONTROLLER_2015("Steam Controller (2015)", true, 2),
    STEAM_VIRTUAL("Steam Input (virtuell)", false, 0),
    OTHER("Gamepad", false, 0),
    NONE("Kein Controller", false, 0);

    public static final int VALVE_VENDOR_ID = 0x28DE;

    private final String label;
    private final boolean nativeValve;
    private final int physicalTrackpads;

    ControllerKind(String label, boolean nativeValve, int physicalTrackpads) {
        this.label = label;
        this.nativeValve = nativeValve;
        this.physicalTrackpads = physicalTrackpads;
    }

    public String getLabel() {
        return label;
    }

    /** True if this is a physical Valve controller read directly (no Steam Input in between). */
    public boolean isNativeValve() {
        return nativeValve;
    }

    public int getPhysicalTrackpads() {
        return physicalTrackpads;
    }

    /** Lower value = preferred when several gamepads are connected. */
    public int priority() {
        return switch (this) {
            case STEAM_DECK, STEAM_CONTROLLER_2026 -> 0;
            case STEAM_CONTROLLER_2015 -> 1;
            case STEAM_VIRTUAL -> 2;
            case OTHER -> 3;
            case NONE -> 9;
        };
    }

    public static ControllerKind fromIds(int vendor, int product) {
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
