package com.steamdeck.mc.input;

/**
 * Enumeration of all assignable in-game, menu, and mouse actions.
 */
public enum DeckAction {
    NONE("Keine Aktion", "Taste macht nichts"),
    
    // Core Game Actions
    ATTACK("Angriff / Abbau", "Linksklick im Spiel"),
    USE("Benutzen / Bauen", "Rechtsklick im Spiel"),
    JUMP("Springen", "Leertaste"),
    SNEAK("Schleichen", "Shift halten"),
    SNEAK_TOGGLE("Sneak (Toggle)", "Schleichen an/aus"),
    SPRINT("Sprinten", "L-Strg"),
    ZOOM("Zoom", "C / Zoom-Taste"),
    PICK_BLOCK("Block wählen", "Mittelklick"),
    DROP("Item werfen", "Q"),
    INVENTORY("Inventar", "E"),
    SWAP_OFFHAND("Zweite Hand", "F"),
    HOTBAR_PREV("Hotbar Zurück", "Vorheriger Slot"),
    HOTBAR_NEXT("Hotbar Vor", "Nächster Slot"),
    TOGGLE_PERSPECTIVE("Perspektive", "F5"),
    TOGGLE_DEBUG("Debug (F3)", "F3 Overlay"),
    OPEN_CHAT("Chat öffnen", "T"),
    PAUSE("Pause (Esc)", "Escape / Pause"),
    RADIAL_MENU_CYCLE("Radial-Menü +1", "Nächstes Radial-Menü"),
    HOTBAR_SLOT_1("Hotbar-Slot 1", "Slot 1 direkt wählen"),
    HOTBAR_SLOT_2("Hotbar-Slot 2", "Slot 2 direkt wählen"),
    HOTBAR_SLOT_3("Hotbar-Slot 3", "Slot 3 direkt wählen"),
    HOTBAR_SLOT_4("Hotbar-Slot 4", "Slot 4 direkt wählen"),
    HOTBAR_SLOT_5("Hotbar-Slot 5", "Slot 5 direkt wählen"),
    HOTBAR_SLOT_6("Hotbar-Slot 6", "Slot 6 direkt wählen"),
    HOTBAR_SLOT_7("Hotbar-Slot 7", "Slot 7 direkt wählen"),
    HOTBAR_SLOT_8("Hotbar-Slot 8", "Slot 8 direkt wählen"),
    HOTBAR_SLOT_9("Hotbar-Slot 9", "Slot 9 direkt wählen"),

    // Mouse & Screen Actions (Available everywhere)
    MOUSE_LEFT_CLICK("Mausklick Links", "Linksklick (Menü / Spiel)"),
    MOUSE_RIGHT_CLICK("Mausklick Rechts", "Rechtsklick (Menü / Spiel)"),
    MOUSE_MIDDLE_CLICK("Mausklick Mitte", "Mittelklick (Menü / Spiel)"),
    MOUSE_QUICK_MOVE("Schnell-Transfer", "Shift+Klick in Kisten"),
    MOUSE_SCROLL_UP("Mausrad hoch", "Scrollen / Hotbar zurück"),
    MOUSE_SCROLL_DOWN("Mausrad runter", "Scrollen / Hotbar vor"),
    CLOSE_SCREEN("Menü schließen", "Zurück / Schließen"),

    // Custom Keyboard Key
    CUSTOM_KEY("⌨️ Eigene Taste", "Tastatur-Taste mit Name");

    private final String label;
    private final String description;

    DeckAction(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** 0..8 for HOTBAR_SLOT_1..9, -1 otherwise. */
    public int hotbarSlot() {
        int i = ordinal() - HOTBAR_SLOT_1.ordinal();
        return i >= 0 && i < 9 ? i : -1;
    }

    public static DeckAction hotbarSlot(int slot) {
        return values()[HOTBAR_SLOT_1.ordinal() + slot];
    }
}
