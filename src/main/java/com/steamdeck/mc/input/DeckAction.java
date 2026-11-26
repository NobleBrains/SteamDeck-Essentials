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
    VOLUME_UP("Lautstärke +", "Minecraft-Gesamtlautstärke +5 %"),
    VOLUME_DOWN("Lautstärke −", "Minecraft-Gesamtlautstärke −5 %"),
    BRIGHTNESS_UP("Helligkeit +", "Minecraft-Helligkeit +5 % (max. 100 % wie in den Optionen)"),
    BRIGHTNESS_DOWN("Helligkeit −", "Minecraft-Helligkeit −5 %"),
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
    CUSTOM_KEY("⌨️ Eigene Taste", "Tastatur-Taste mit Name"),

    // Any key mapping from Minecraft's controls screen, including other mods (e.g. a zoom mod)
    KEY_MAPPING("Minecraft-/Mod-Belegung", "Belegung aus den Steuerungs-Optionen");

    /** Actions that are mouse clicks / attack / use: never hold-to-repeat (would be an autoclicker). */
    public boolean isClickAction() {
        return switch (this) {
            case ATTACK, USE, PICK_BLOCK, MOUSE_LEFT_CLICK, MOUSE_RIGHT_CLICK, MOUSE_MIDDLE_CLICK, MOUSE_QUICK_MOVE -> true;
            default -> false;
        };
    }

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

    /**
     * Label as shown for {@code set}. In menus many actions do something else than in-game (their key
     * goes to the open screen like on a keyboard), so the menu set names what they do there.
     */
    public String getLabel(ActionSet set) {
        if (set != ActionSet.MENUS) return label;
        return switch (this) {
            case SNEAK -> "Shift halten";
            case SPRINT -> "Strg halten";
            case ATTACK -> "Linksklick";
            case USE -> "Rechtsklick";
            case PICK_BLOCK -> "Mittelklick";
            case DROP -> "Item werfen (Q)";
            case INVENTORY -> "Inventar schließen (E)";
            case SWAP_OFFHAND -> "Mit Zweithand tauschen";
            case HOTBAR_PREV -> "Tab / Liste zurück";
            case HOTBAR_NEXT -> "Tab / Liste vor";
            case PAUSE -> "Menü schließen (Esc)";
            case HOTBAR_SLOT_1, HOTBAR_SLOT_2, HOTBAR_SLOT_3, HOTBAR_SLOT_4, HOTBAR_SLOT_5,
                 HOTBAR_SLOT_6, HOTBAR_SLOT_7, HOTBAR_SLOT_8, HOTBAR_SLOT_9 -> "Nach Slot " + (hotbarSlot() + 1) + " tauschen";
            case SNEAK_TOGGLE, TOGGLE_DEBUG, RADIAL_MENU_CYCLE -> label + " (im Menü aus)";
            default -> label;
        };
    }

    /** Help text for {@code set}, see {@link #getLabel(ActionSet)}. */
    public String getDescription(ActionSet set) {
        if (set != ActionSet.MENUS) return description;
        return switch (this) {
            case SNEAK -> "Shift: Klick = Stapel schnell verschieben, Doppelklick = alle gleichen Items";
            case SPRINT -> "Strg: mit Item werfen (Q) den ganzen Stapel werfen";
            case ATTACK -> "Linksklick: Stapel nehmen/ablegen, gedrückt ziehen = verteilen";
            case USE -> "Rechtsklick: halben Stapel nehmen / ein Item ablegen";
            case PICK_BLOCK -> "Mittelklick: im Kreativmodus vollen Stapel nehmen";
            case DROP -> "Q über einem Slot: ein Item werfen, mit Strg den ganzen Stapel";
            case INVENTORY -> "E: Inventar oder Kiste schließen";
            case SWAP_OFFHAND -> "F über einem Slot: Item mit der Zweithand tauschen";
            case HOTBAR_PREV -> "Kreativ: vorheriger Tab, sonst Liste scrollen";
            case HOTBAR_NEXT -> "Kreativ: nächster Tab, sonst Liste scrollen";
            case PAUSE -> "Escape: schließt das Menü";
            case HOTBAR_SLOT_1, HOTBAR_SLOT_2, HOTBAR_SLOT_3, HOTBAR_SLOT_4, HOTBAR_SLOT_5,
                 HOTBAR_SLOT_6, HOTBAR_SLOT_7, HOTBAR_SLOT_8, HOTBAR_SLOT_9 ->
                    "Zahlentaste " + (hotbarSlot() + 1) + ": Item unter dem Zeiger mit Hotbar-Slot " + (hotbarSlot() + 1) + " tauschen";
            case SNEAK_TOGGLE -> "Umschalten wirkt nur im Spiel; im Menü Schleichen (Shift halten) nehmen";
            case TOGGLE_DEBUG, RADIAL_MENU_CYCLE -> "Wirkt nur im Spiel";
            default -> description;
        };
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
