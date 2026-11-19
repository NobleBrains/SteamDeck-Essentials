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
}
