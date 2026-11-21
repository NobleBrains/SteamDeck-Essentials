package com.steamdeck.mc.input;

/**
 * Defines context-sensitive control profiles ("Action Sets") for the Steam Deck.
 */
public enum ActionSet {
    IN_GAME("🎮 Im Spiel", "Erkunden, Laufen, Bauen & Kämpfen"),
    MENUS("📦 Menüs & Inventar", "Kisten, Inventar, Amboss, Handelsmenüs");
    // "Zielen" was removed: it only switched the gyro, which is now the gyro option "Nur beim Zielen".
    // Own sets with conditions (e.g. per server) may come later.

    private final String title;
    private final String description;

    ActionSet(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
