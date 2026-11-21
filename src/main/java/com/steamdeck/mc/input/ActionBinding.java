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
    /** For KEY_MAPPING: the mapping's name, e.g. "key.jump" or "key.zoomify.zoom". */
    private final String mappingName;
    // Steam-Input-style activator settings: hold to repeat (delay/rate in ms, 0 = off) and a haptic tick on press
    private int repeatDelayMs = 0;
    private int repeatRateMs = 0;
    private boolean haptic = false;

    public ActionBinding(DeckAction action) {
        this.action = action != null && action != DeckAction.KEY_MAPPING ? action : DeckAction.NONE;
        this.customKeyCode = 0;
        this.customNickname = "";
        this.mappingName = "";
    }

    public ActionBinding(int keyCode, String nickname) {
        this.action = DeckAction.CUSTOM_KEY;
        this.customKeyCode = keyCode;
        this.customNickname = (nickname != null && !nickname.isBlank()) ? nickname.trim() : "Taste " + getKeyLabel(keyCode);
        this.mappingName = "";
    }

    private ActionBinding(String mappingName) {
        this.action = DeckAction.KEY_MAPPING;
        this.customKeyCode = 0;
        this.customNickname = "";
        this.mappingName = mappingName;
    }

    /** Presses a Minecraft (or other mod's) key mapping, whatever key it is bound to in the controls. */
    public static ActionBinding forMapping(String mappingName) {
        return new ActionBinding(mappingName);
    }

    public boolean isMapping() {
        return action == DeckAction.KEY_MAPPING && !mappingName.isEmpty();
    }

    public String getMappingName() {
        return mappingName;
    }

    /** Same target (action / key / mapping) without haptic and repeat settings. */
    public ActionBinding copyBase() {
        if (isCustomKey()) return new ActionBinding(customKeyCode, customNickname);
        if (isMapping()) return forMapping(mappingName);
        return new ActionBinding(action);
    }

    /**
     * Hold-to-repeat is only allowed where a held keyboard key repeats too. Attack, use and mouse clicks
     * (also mappings bound to a mouse button) would turn into an autoclicker.
     */
    public boolean allowsRepeat() {
        if (action.isClickAction()) return false;
        if (isMapping()) {
            if (mappingName.equals("key.attack") || mappingName.equals("key.use") || mappingName.equals("key.pickItem")) return false;
            try {
                net.minecraft.client.KeyMapping km = net.minecraft.client.KeyMapping.get(mappingName);
                if (km != null) {
                    InputConstants.Key key = ((com.steamdeck.mc.mixin.KeyMappingAccessor) km).getKey();
                    if (key != null && key.getType() == InputConstants.Type.MOUSE) return false;
                }
            } catch (Throwable ignored) {}
        }
        return true;
    }

    /** Translated name of the mapping (falls back to its raw name if the mod is not installed). */
    public String getMappingLabel() {
        try {
            return net.minecraft.network.chat.Component.translatable(mappingName).getString();
        } catch (Throwable t) {
            return mappingName;
        }
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
        return repeatRateMs > 0 && allowsRepeat();
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
        String label = isCustomKey() ? customNickname + " (" + getKeyLabel(customKeyCode) + ")"
                : (isMapping() ? getMappingLabel() : action.getLabel());
        if (hasRepeat()) label += " ⟳";
        return label;
    }

    public String serialize() {
        String base = isCustomKey() ? "KEY:" + customKeyCode + ":" + customNickname.replace("|", "/")
                : (isMapping() ? "MAP:" + mappingName.replace("|", "/") : action.name());
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
        if (serialized.startsWith("MAP:") && serialized.length() > 4) {
            return forMapping(serialized.substring(4));
        }
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
