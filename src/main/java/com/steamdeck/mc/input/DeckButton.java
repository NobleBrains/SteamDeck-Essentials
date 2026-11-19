package com.steamdeck.mc.input;

/**
 * Enumeration of all physical buttons, paddles, and trackpad clicks on the Steam Deck.
 * Clean, compact display names tailored for handheld display geometry.
 */
public enum DeckButton {
    A("A-Taste"),
    B("B-Taste"),
    X("X-Taste"),
    Y("Y-Taste"),
    LB("L1 (Bumper)"),
    RB("R1 (Bumper)"),
    LT("L2 (Trigger)"),
    RT("R2 (Trigger)"),
    L3("L3 (Stick)"),
    R3("R3 (Stick)"),
    L4("L4 (Paddle)"),
    R4("R4 (Paddle)"),
    L5("L5 (Paddle)"),
    R5("R5 (Paddle)"),
    LEFT_TRACKPAD("Trackpad L"),
    RIGHT_TRACKPAD("Trackpad R"),
    DPAD_UP("D-Pad Oben"),
    DPAD_DOWN("D-Pad Unten"),
    DPAD_LEFT("D-Pad Links"),
    DPAD_RIGHT("D-Pad Rechts"),
    START("Start (Menü)"),
    SELECT("Select (Ansicht)"),
    LEFT_STICK_TOUCH("L-Stick Touch"),
    RIGHT_STICK_TOUCH("R-Stick Touch");

    private final String displayName;

    DeckButton(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
