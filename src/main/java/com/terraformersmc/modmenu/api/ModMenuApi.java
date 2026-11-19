package com.terraformersmc.modmenu.api;

/**
 * Official ModMenu API interface for Fabric.
 * Enables ModMenu to automatically attach a "Configure" button to SteamDeck Essentials.
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }
}
