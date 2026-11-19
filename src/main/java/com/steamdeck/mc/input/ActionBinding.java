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
        if (isCustomKey()) {
            return customNickname + " (" + getKeyLabel(customKeyCode) + ")";
        }
        return action.getLabel();
    }

    public String serialize() {
        if (isCustomKey()) {
            return "KEY:" + customKeyCode + ":" + customNickname;
        }
        return action.name();
    }

    public static ActionBinding deserialize(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return new ActionBinding(DeckAction.NONE);
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
