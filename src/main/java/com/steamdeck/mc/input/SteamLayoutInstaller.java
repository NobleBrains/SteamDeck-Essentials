package com.steamdeck.mc.input;

import com.steamdeck.mc.SteamDeckEssentials;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs the bundled Steam Input controller template (controller_neptune_steamdeck_essentials.vdf)
 * directly into Steam's controller templates and shortcut configuration folders.
 */
public class SteamLayoutInstaller {
    private static final String RESOURCE_PATH = "/assets/steamdeck_essentials/controller_neptune_steamdeck_essentials.vdf";
    private static final String TEMPLATE_FILENAME = "controller_neptune_steamdeck_essentials.vdf";

    private static String lastStatusMessage = "Bereit zur Installation";
    private static boolean lastInstallSuccess = false;

    public static String getLastStatusMessage() {
        return lastStatusMessage;
    }

    public static boolean isLastInstallSuccess() {
        return lastInstallSuccess;
    }

    /**
     * Checks if the template is already installed in any recognized Steam directory.
     */
    public static boolean isLayoutInstalled() {
        for (Path steamDir : findSteamDirectories()) {
            Path templateFile = steamDir.resolve("controller_base/templates").resolve(TEMPLATE_FILENAME);
            if (Files.exists(templateFile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Installs or updates the VDF template in all detected Steam directories.
     *
     * @param includeAppConfigs also write the layout as the active per-app config for Prism Launcher /
     *        Minecraft ({@code Steam Controller Configs/<user>/config/<app>/controller_neptune.vdf}).
     *        That replaces the user's own layout for these apps, so it is only done on an explicit
     *        request (button in the Bridge tab), never by the automatic install at game start.
     */
    public static synchronized boolean installLayout(boolean includeAppConfigs) {
        try (InputStream in = SteamLayoutInstaller.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                lastStatusMessage = "Fehler: VDF-Ressource nicht in Mod-JAR gefunden!";
                lastInstallSuccess = false;
                return false;
            }
            byte[] vdfBytes = in.readAllBytes();

            List<Path> steamDirs = findSteamDirectories();
            if (steamDirs.isEmpty()) {
                lastStatusMessage = "Steam-Ordner nicht gefunden (Flatpak-Sandbox?)";
                lastInstallSuccess = false;
                return false;
            }

            int installedCount = 0;
            for (Path steamDir : steamDirs) {
                try {
                    Path templatesDir = steamDir.resolve("controller_base/templates");
                    Files.createDirectories(templatesDir);
                    Path targetTemplate = templatesDir.resolve(TEMPLATE_FILENAME);
                    writeIfChanged(targetTemplate, vdfBytes, false);
                    installedCount++;

                    if (!includeAppConfigs) continue;

                    // Also configure user shortcut directories (Prism Launcher / Minecraft)
                    Path controllerConfigsRoot = steamDir.resolve("steamapps/common/Steam Controller Configs");
                    if (Files.isDirectory(controllerConfigsRoot)) {
                        try (DirectoryStream<Path> userDirs = Files.newDirectoryStream(controllerConfigsRoot)) {
                            for (Path userDir : userDirs) {
                                Path configDir = userDir.resolve("config");
                                if (Files.isDirectory(configDir)) {
                                    for (String appName : new String[]{"prism launcher", "prismlauncher", "minecraft"}) {
                                        Path appCfgDir = configDir.resolve(appName);
                                        Files.createDirectories(appCfgDir);
                                        // The user's own layout for this app may live here: keep a backup
                                        writeIfChanged(appCfgDir.resolve("controller_neptune.vdf"), vdfBytes, true);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    SteamDeckEssentials.LOGGER.debug("Could not write to Steam path {}", steamDir, e);
                }
            }

            if (installedCount > 0) {
                lastInstallSuccess = true;
                lastStatusMessage = "✓ Steam-Layout installiert! (In Steam unter Vorlagen wählbar)";
                SteamDeckEssentials.LOGGER.info("[INPUT] Successfully installed Steam Input layout to {} Steam folder(s).", installedCount);
                return true;
            } else {
                lastInstallSuccess = false;
                lastStatusMessage = "Keine Schreibrechte auf ~/.local/share/Steam";
                return false;
            }
        } catch (Exception e) {
            lastInstallSuccess = false;
            lastStatusMessage = "Fehler: " + e.getMessage();
            SteamDeckEssentials.LOGGER.warn("[INPUT] Failed to install Steam Input layout", e);
            return false;
        }
    }

    /**
     * Writes the file only if its content differs. With {@code backup}, an existing file that is not
     * ours (an older version of our own layout does not count) is backed up every time before it is
     * replaced: first to {@code <name>.bak-steamdeck-essentials}, later backups get a timestamp so
     * an earlier backup is never overwritten.
     */
    private static void writeIfChanged(Path target, byte[] content, boolean backup) throws java.io.IOException {
        if (Files.exists(target)) {
            byte[] existing = Files.readAllBytes(target);
            if (java.util.Arrays.equals(existing, content)) {
                return;
            }
            if (backup && !isOwnLayout(existing)) {
                Path bak = target.resolveSibling(target.getFileName() + ".bak-steamdeck-essentials");
                if (Files.exists(bak)) {
                    bak = target.resolveSibling(target.getFileName() + ".bak-steamdeck-essentials-"
                            + (System.currentTimeMillis() / 1000L));
                }
                Files.copy(target, bak);
                SteamDeckEssentials.LOGGER.info("[INPUT] Backed up existing Steam layout to {}", bak);
            }
        }
        Files.write(target, content);
    }

    /** Our bundled template carries this title; files with it were written by the mod. */
    private static final String OWN_TITLE = "\"SteamDeck Essentials (Minecraft)\"";

    private static boolean isOwnLayout(byte[] vdf) {
        return new String(vdf, java.nio.charset.StandardCharsets.UTF_8).contains(OWN_TITLE);
    }

    private static List<Path> findSteamDirectories() {
        List<Path> result = new ArrayList<>();
        String home = System.getProperty("user.home", "/home/deck");

        String[] candidates = {
                home + "/.local/share/Steam",
                home + "/.steam/steam",
                "/home/deck/.local/share/Steam",
                "/home/deck/.steam/steam",
                "/var/home/deck/.local/share/Steam"
        };

        for (String candidate : candidates) {
            try {
                Path p = Path.of(candidate);
                if (Files.isDirectory(p) && !result.contains(p.toRealPath())) {
                    result.add(p.toRealPath());
                }
            } catch (Exception ignored) {}
        }
        return result;
    }
}
