package com.steamdeck.mc.platform;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Detects whether the game runs on a handheld PC (Steam Deck and other known x86 handhelds)
 * and resolves the effective {@link DeviceMode}.
 *
 * <p>Detection uses the DMI/SMBIOS identifiers in {@code /sys/class/dmi/id}, which are readable
 * without root and also inside the Prism Launcher Flatpak sandbox. As a fallback, the
 * {@code SteamDeck=1} environment variable that SteamOS sets in Game Mode is honoured.
 * Detection runs once; the user can always override it with {@code deviceMode} in the config.</p>
 */
public final class PlatformDetector {
    private static final Path DMI = Path.of("/sys/class/dmi/id");

    /** Result of the one-time hardware detection. */
    public record Hardware(
            String vendor,
            String product,
            boolean steamDeck,
            boolean steamDeckOled,
            boolean knownHandheld,
            String label
    ) {
        /** Native panel refresh rate used as the thermal FPS cap (OLED 90 Hz, LCD 60 Hz). */
        public int panelRefreshHz() {
            if (steamDeck) {
                return steamDeckOled ? 90 : 60;
            }
            return 90;
        }
    }

    private static volatile Hardware hardware;

    private PlatformDetector() {}

    public static Hardware getHardware() {
        Hardware hw = hardware;
        if (hw == null) {
            synchronized (PlatformDetector.class) {
                hw = hardware;
                if (hw == null) {
                    hw = detect();
                    hardware = hw;
                    SteamDeckEssentials.LOGGER.info("[PLATFORM] Hardware: '{}' / '{}' -> {} (Handheld erkannt: {})",
                            hw.vendor(), hw.product(), hw.label(), hw.knownHandheld());
                }
            }
        }
        return hw;
    }

    /** The mode the mod is effectively running in (config override or detection). */
    public static DeviceMode getEffectiveMode() {
        DeviceMode configured = ModConfig.getInstance().getDeviceMode();
        if (configured != DeviceMode.AUTO) {
            return configured;
        }
        return getHardware().knownHandheld() ? DeviceMode.HANDHELD : DeviceMode.DESKTOP;
    }

    /** True if handheld-only features (power/eco engine, OSK auto-open, chat layout, ...) should run. */
    public static boolean isHandheld() {
        return getEffectiveMode() == DeviceMode.HANDHELD;
    }

    /** Short human readable status, e.g. "Handheld (Auto: Steam Deck OLED)". */
    public static String describe() {
        DeviceMode configured = ModConfig.getInstance().getDeviceMode();
        DeviceMode effective = getEffectiveMode();
        if (configured == DeviceMode.AUTO) {
            return effective.getLabel() + " (Auto: " + getHardware().label() + ")";
        }
        return effective.getLabel() + " (manuell)";
    }

    private static Hardware detect() {
        String sysVendor = readDmi("sys_vendor");
        String boardVendor = readDmi("board_vendor");
        String product = readDmi("product_name");
        String family = readDmi("product_family");
        String board = readDmi("board_name");

        String vendor = !sysVendor.isEmpty() ? sysVendor : boardVendor;
        String v = (sysVendor + " " + boardVendor).toLowerCase(Locale.ROOT);
        String p = (product + " " + family + " " + board).toLowerCase(Locale.ROOT);

        // Valve Steam Deck: "Jupiter" = LCD, "Galileo" = OLED
        if (v.contains("valve")) {
            if (p.contains("galileo")) {
                return new Hardware(vendor, product, true, true, true, "Steam Deck OLED");
            }
            if (p.contains("jupiter")) {
                return new Hardware(vendor, product, true, false, true, "Steam Deck LCD");
            }
        }

        // SteamOS Game Mode exports SteamDeck=1 (also covers unreadable DMI in odd sandboxes)
        if ("1".equals(System.getenv("SteamDeck"))) {
            return new Hardware(vendor, product, true, false, true, "Steam Deck (SteamOS-Umgebung)");
        }

        String otherHandheld = matchOtherHandheld(v, p);
        if (otherHandheld != null) {
            return new Hardware(vendor, product, false, false, true, otherHandheld);
        }

        String label = product.isEmpty() ? "Desktop/Laptop" : product;
        return new Hardware(vendor, product, false, false, false, label);
    }

    /** Known x86 handheld PCs besides the Steam Deck (SMBIOS product names). */
    private static String matchOtherHandheld(String vendor, String product) {
        if (vendor.contains("asus") && (product.contains("rog ally") || product.contains("rc71l") || product.contains("rc72l"))) {
            return "ASUS ROG Ally";
        }
        if (vendor.contains("lenovo") && (product.contains("legion go") || product.contains("83e1"))) {
            return "Lenovo Legion Go";
        }
        if (vendor.contains("micro-star") && product.contains("claw")) {
            return "MSI Claw";
        }
        if (vendor.contains("ayaneo") || vendor.contains("ayadevice")) {
            return "AYANEO";
        }
        if (vendor.contains("one-netbook") || product.contains("onexplayer")) {
            return "OneXPlayer";
        }
        if (vendor.contains("gpd") && (product.contains("win") || product.contains("g16"))) {
            return "GPD Win";
        }
        if (vendor.contains("zotac") && product.contains("zone")) {
            return "ZOTAC Zone";
        }
        return null;
    }

    private static String readDmi(String name) {
        try {
            Path file = DMI.resolve(name);
            if (Files.isReadable(file)) {
                return Files.readString(file).trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
