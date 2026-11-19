package com.steamdeck.mc.input;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Encapsulates a button binding: either a built-in semantic DeckAction,
 * or a raw keyboard key with an optional custom nickname (e.g. "Minimap" on key 'M').
 */
public class ActionBinding {
    private final DeckAction action;
    private final int customKeyCode;
    private final String customNickname;
    // Steam-Input-style activator settings: hold to repeat (delay/rate in ms, 0 = off) and a haptic tick on press
    private int repeatDelayMs = 0;
    private int repeatRateMs = 0;
    private boolean haptic = false;

    public ActionBinding(DeckAction action) {
        this.action = action != null ? action : DeckAction.NONE;
        this.customKeyCode = 0;
        this.customNickname = "";
    }

    public ActionBinding(int keyCode, String nickname) {
        this.action = DeckAction.CUSTOM_KEY;
        this.customKeyCode = keyCode;
        this.customNickname = (nickname != null && !nickname.isBlank()) ? nickname.trim() : "Taste " + getKeyLabel(keyCode);
    }

    /** Hold-to-repeat like Steam's "hold_repeats": first repeat after {@code delayMs}, then every {@code rateMs}. */
    public ActionBinding withRepeat(int delayMs, int rateMs) {
        this.repeatDelayMs = Math.max(0, delayMs);
        this.repeatRateMs = Math.max(0, rateMs);
        return this;
    }

    /** Short trackpad haptic tick on press (Steam "haptic_intensity"). */
    public ActionBinding withHaptic(boolean haptic) {
        this.haptic = haptic;
        return this;
    }

    public boolean hasRepeat() {
        return repeatRateMs > 0;
    }

    public int getRepeatDelayMs() {
        return repeatDelayMs;
    }

    public int getRepeatRateMs() {
        return repeatRateMs;
    }

    public boolean hasHaptic() {
        return haptic;
    }

    public DeckAction getAction() {
        return action;
    }

    public boolean isCustomKey() {
        return action == DeckAction.CUSTOM_KEY && customKeyCode > 0;
    }

    public int getCustomKeyCode() {
        return customKeyCode;
    }

    public String getCustomNickname() {
        return customNickname;
    }

    public String getDisplayLabel() {
        String label = isCustomKey() ? customNickname + " (" + getKeyLabel(customKeyCode) + ")" : action.getLabel();
        if (hasRepeat()) label += " ⟳";
        return label;
    }

    public String serialize() {
        String base = isCustomKey() ? "KEY:" + customKeyCode + ":" + customNickname.replace("|", "/") : action.name();
        if (hasRepeat()) base += "|repeat=" + repeatDelayMs + "," + repeatRateMs;
        if (haptic) base += "|haptic";
        return base;
    }

    public static ActionBinding deserialize(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return new ActionBinding(DeckAction.NONE);
        }
        // Optional activator settings: "ACTION|repeat=200,50|haptic"
        String[] options = serialized.split("\\|");
        ActionBinding binding = deserializeBase(options[0]);
        for (int i = 1; i < options.length; i++) {
            String opt = options[i].trim();
            if (opt.startsWith("repeat=")) {
                try {
                    String[] v = opt.substring(7).split(",");
                    binding.withRepeat(Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()));
                } catch (Exception ignored) {}
            } else if (opt.equals("haptic")) {
                binding.withHaptic(true);
            }
        }
        return binding;
    }

    private static ActionBinding deserializeBase(String serialized) {
        if (serialized.startsWith("KEY:")) {
            try {
                String[] parts = serialized.split(":", 3);
                int code = Integer.parseInt(parts[1]);
                String name = parts.length > 2 ? parts[2] : "";
                return new ActionBinding(code, name);
            } catch (Exception ignored) {}
        }
        try {
            DeckAction act = DeckAction.valueOf(serialized);
            return new ActionBinding(act);
        } catch (IllegalArgumentException e) {
            return new ActionBinding(DeckAction.NONE);
        }
    }

    public static String getKeyLabel(int keyCode) {
        if (keyCode <= 0) return "Keine";
        try {
            InputConstants.Key key = InputConstants.Type.KEYBOARD.getOrCreate(keyCode);
            return key.getDisplayName().getString();
        } catch (Exception e) {
            return String.valueOf(keyCode);
        }
    }
}
