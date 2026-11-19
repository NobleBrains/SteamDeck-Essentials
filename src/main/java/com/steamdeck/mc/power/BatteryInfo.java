package com.steamdeck.mc.power;

import java.util.Locale;

/**
 * Snapshot of current battery metrics parsed from Linux sysfs power_supply.
 */
public record BatteryInfo(
        int percent,
        double powerWatts,
        double voltageVolts,
        boolean isCharging,
        boolean isDischarging,
        long remainingMinutes,
        boolean isAvailable
) {
    public static final BatteryInfo UNAVAILABLE = new BatteryInfo(0, 0.0, 0.0, false, false, -1, false);

    /**
     * Formatted string showing estimated remaining playtime or charging status.
     * Example: "~3h 15m" or "Charging (18.4W)"
     */
    public String getFormattedPlaytime() {
        if (!isAvailable) {
            return "N/A";
        }
        if (isCharging) {
            return "Charging";
        }
        if (remainingMinutes < 0) {
            return "Calculating...";
        }
        long hours = remainingMinutes / 60;
        long minutes = remainingMinutes % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "~%dh %02dm", hours, minutes);
        } else {
            return String.format(Locale.ROOT, "~%dm", minutes);
        }
    }

    /**
     * Compact HUD representation, e.g.:
     * "BAT: 74% • 11.2W • ~3h 15m"
     */
    public String toHudString() {
        if (!isAvailable) {
            return "BAT: N/A";
        }
        String statusIcon = isCharging ? "⚡" : (percent <= 20 ? "🪫" : "🔋");
        if (isDischarging && powerWatts > 0.0) {
            return String.format(Locale.ROOT, "%s %d%% • %.1fW • %s", statusIcon, percent, powerWatts, getFormattedPlaytime());
        } else if (isCharging) {
            return String.format(Locale.ROOT, "%s %d%% (⚡ %.1fW)", statusIcon, percent, powerWatts);
        } else {
            return String.format(Locale.ROOT, "%s %d%%", statusIcon, percent);
        }
    }
}
