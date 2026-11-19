package com.steamdeck.mc.input;

/**
 * Defines context-sensitive control profiles ("Action Sets") for the Steam Deck.
 */
public enum ActionSet {
    IN_GAME("🎮 Im Spiel", "Erkunden, Laufen, Bauen & Kämpfen"),
    MENUS("📦 Menüs & Inventar", "Kisten, Inventar, Amboss, Handelsmenüs"),
    AIMING("🎯 Zielen & Bogen", "Automatisches Gyro-Zielen bei Bogen/Fernrohr");

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
