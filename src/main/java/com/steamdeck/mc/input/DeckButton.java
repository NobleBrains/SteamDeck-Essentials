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
    RIGHT_STICK_TOUCH("R-Stick Touch"),
    // Virtual buttons: left trackpad split into a 3x3 grid (mode "GRID_3X3"), row by row from top left
    LPAD_GRID_1("Raster 1 (oben links)"),
    LPAD_GRID_2("Raster 2 (oben)"),
    LPAD_GRID_3("Raster 3 (oben rechts)"),
    LPAD_GRID_4("Raster 4 (links)"),
    LPAD_GRID_5("Raster 5 (Mitte)"),
    LPAD_GRID_6("Raster 6 (rechts)"),
    LPAD_GRID_7("Raster 7 (unten links)"),
    LPAD_GRID_8("Raster 8 (unten)"),
    LPAD_GRID_9("Raster 9 (unten rechts)");

    private static final DeckButton[] GRID = {
            LPAD_GRID_1, LPAD_GRID_2, LPAD_GRID_3, LPAD_GRID_4, LPAD_GRID_5,
            LPAD_GRID_6, LPAD_GRID_7, LPAD_GRID_8, LPAD_GRID_9
    };

    /** Grid cell 0..8 (row-major from the top left). */
    public static DeckButton gridCell(int index) {
        return GRID[index];
    }

    /** 0..8 for a grid cell, -1 for every other button. */
    public int gridIndex() {
        return ordinal() >= LPAD_GRID_1.ordinal() ? ordinal() - LPAD_GRID_1.ordinal() : -1;
    }

    private final String displayName;

    DeckButton(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
