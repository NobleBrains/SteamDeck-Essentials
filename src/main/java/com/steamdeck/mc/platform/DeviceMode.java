package com.steamdeck.mc.platform;

/**
 * User-selectable operating mode of the mod.
 * <ul>
 *     <li>{@link #AUTO}: decided by {@link PlatformDetector} (DMI hardware IDs, SteamOS environment).</li>
 *     <li>{@link #HANDHELD}: forces all handheld features (battery/eco engine, FPS caps, OSK auto-open,
 *     chat-at-top layout, Steam Input key bridge, suspend-save, ...).</li>
 *     <li>{@link #DESKTOP}: all handheld power/performance features are off; controller support stays on.</li>
 * </ul>
 */
public enum DeviceMode {
    AUTO("Automatisch"),
    HANDHELD("Handheld"),
    DESKTOP("Desktop");

    private final String label;

    DeviceMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public DeviceMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static DeviceMode parse(String value) {
        if (value != null) {
            for (DeviceMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
        }
        return AUTO;
    }
}
