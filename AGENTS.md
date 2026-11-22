# AGENTS.md – Entwickler- & Agenten-Leitfaden für „SteamDeck Essentials“

Dieses Dokument ist der zentrale Einstiegspunkt für Menschen und Coding-Agents. Es erklärt **Philosophie**, **Ordner- & Datei-Struktur**, **Architektur/Datenflüsse**, **Build-Befehle** und **offene/bekannte Legacy-Stellen**, sodass jeder sofort nahtlos weiterarbeiten kann.

---

## 1. Projekt-Philosophie & Leitprinzipien

Bevor ein neues Feature eingebaut wird, muss es zu diesen sechs Grundsätzen passen:

1. **Die Mod ist eine Brücke, kein Hack / kein Cheat („Vanilla-Parität“):**
   * Controller-Eingaben steuern das Spiel ausschließlich über Vanillas reguläre Eingabewege (`KeyMapping`, `KeyEvent`, `MouseButtonInfo`, `player.turn()`).
   * Keine spielerischen Vorteile gegenüber Maus/Tastatur: kein künstlicher Zoom ohne Item, kein Autoklicker (`hold_repeats` für Angriff/Benutzen/Mausklicks verboten), keine Aktionen durch offene Menüs hindurch.
   * Wenn eine Funktion mit Maus/Tastatur unmöglich ist, gehört sie **nicht** in diese Mod.

2. **Strikte Trennung von Handheld- und Desktop-Modus (`PlatformDetector` / `DeviceMode`):**
   * **Handheld-only:** FPS-Caps (Menü/AFK/Thermal-90Hz), OLED-Dimming, Low-Battery-Saver, Chunk-Thread-Limits, Akku-HUD, CSV-Telemetrie, Suspend-Save (`PrepareForSleep`), Steam-Layout-Auto-Install.
   * **Überall aktiv (Handheld & Desktop):** Controller-Engine, Per-Controller-Profile, Gyro, Haptik/Adaptive Triggers, Radial-Menü, Konfigurator, sowie kontextuelles OSK/Chat-Layout (`AUTO` nur, wenn zuletzt der Controller genutzt wurde).
   * Ein Desktop-PC darf niemals ungefragt gedrosselt oder optisch verändert werden.

3. **Nativ vor Steam Input – aber koexistenzfähig:**
   * Physische Controller (Steam Deck, Steam Controller 2026 „Triton“ & 2015, PS5 DualSense & Edge) werden bevorzugt direkt über **SDL3 (+ HIDAPI)** gelesen (`ControllerKind.priority()`).
   * Steam Input (`STEAM_VIRTUAL`) + Hardware-Bridge (`[ ] \ / F13 F14`) bleiben als vollwertiger Fallback erhalten.

4. **Kein Aussperren & niemals Nutzer-Layouts überschreiben:**
   * Bestehende Configs/Layouts werden beim Upgrade migriert (`profiles/user_default.json`), nie still überschrieben.
   * VDF-Installation legt vorher Backups an (`*.bak-steamdeck-essentials`).
   * Der Konfigurator (`ControllerLayoutScreen`) liest Controller-Tasten direkt, unabhängig vom `MENUS`-Action-Set.

5. **Frame-Synchronität & Non-Blocking I/O:**
   * Kamera, Gyro, Trackpad-Maus und OSK-Navigation laufen **pro Render-Frame** (`HudMixin` → `pollCameraFrame` / `GyroManager.update`, `ScreenEvents.afterExtract` → `pollFrame`), nicht nur im 20-Hz-Client-Tick (`ClientTickEvents.END_CLIENT_TICK`).
   * USB/Bluetooth-HID-Schreibzugriffe (`TritonTrackpadHaptics`, `LizardMode`) laufen auf Daemon-Threads, damit Bluetooth-Latenz nie einen Frame blockiert.

6. **Übersichtliche UI-Grenzen (kein Settings-Overload):**
   * Standard-Flows sollen ohne Konfigurationszwang intuitiv funktionieren (z. B. feste SteamOS-OSK-Hotkeys `A/X/Y/Start/B`, automatische Hardware- & Controller-Erkennung).
   * Neue Features (wie DualSense Adaptive Triggers) sind pro Feature togglebar, aber sinnvoll vorbelegt.

---

## 2. Ordner- & Datei-Struktur

### 2.1 Projekt-Root (`/var/home/lukhicken/AGY/Minecraft/Deck/SteamDeck-Essentials/`)

| Pfad | Inhalt & Zweck |
|---|---|
| `AGENTS.md` | **Diese Datei** – Architektur, Philosophie, Dateikarte, Build-Guide für Entwickler/Agents. |
| `README.md` | Benutzer- & Feature-Übersicht (deutsch). |
| `CHANGELOG.md` | Technische Entwicklungshistorie & Debugging-Leitfaden (F3, Log-Präfixe, Linux-Shell-Checks). |
| `To-Do.txt` | Erledigte Meilensteine `[x]` und Merkliste / offene Aufgaben `[ ]`. |
| `build.gradle`, `gradle.properties`, `settings.gradle` | Gradle-Build für Minecraft `26.3`, Fabric Loader `0.20.1`, Java 25. Liest Compile-Classpath direkt aus der lokalen Prism-Launcher-Instanz. |
| `gradlew`, `gradlew.bat`, `gradle/` | Gradle-Wrapper (Gradle 9.6.0). |
| `install-steam-layout.sh` | Standalone-Bash-Skript zur Installation der Steam-Input-`.vdf`-Vorlage (Alternative zum In-Game-Installer). |
| `libs/fabric-api/` | Lokale Fabric-API-JARs (Compile-Abhängigkeit, offline-fähig). |
| `docs/` | `FINDINGS.md` (Clean-Room-Doku zu Steam Input / HIDAPI / VDF / SC2026) & `steamdeck minecraft controls_0.vdf` (Workshop-Referenz-Layout). |
| `tools/` | `pixelize_svg.py` – konvertiert Controller-SVGs (über `magick` + Pillow) in 1-Bit-Pixel-Art-PNGs (`200 px` breit). |
| `src/stubs/java/` | Compile-only API-Stubs (`ModMenuApi`, `ConfigScreenFactory`), werden **nicht** in die Mod-JAR gepackt. |
| `src/main/resources/` | `fabric.mod.json`, `steamdeck_essentials.mixins.json`, Sprachdateien (`lang/de_de.json`, `en_us.json`), VDF-Vorlage, `icon.png`, Controller-Pixel-Art (`textures/gui/controller_{deck,triton,ps5}.png`). |
| `SteamDeckEssentials-*.jar` | Aktuelle gebaute Mod-JAR (vom `.gitignore` ausgeschlossen). |

### 2.2 Java-Quellcode (`src/main/java/com/steamdeck/mc/`)

#### Einstiegspunkt & Plattform
* `SteamDeckEssentials.java` – Fabric-`ClientModInitializer`: setzt SDL3-Hints (`SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS`, `SDL_HINT_JOYSTICK_HIDAPI_STEAM`), initialisiert SDL-Subsysteme (`GAMEPAD | JOYSTICK | SENSOR`), registriert Keybindings (`F12` OSK), Tick- & Screen-Events.
* `platform/PlatformDetector.java` – Einmalige Hardware-Erkennung via `/sys/class/dmi/id` (funktioniert in Flatpak-Sandbox) + `SteamDeck=1`-Fallback; liefert `panelRefreshHz()` (90 OLED / 60 LCD) und `isHandheld()`.
* `platform/DeviceMode.java` – Enum `AUTO`, `HANDHELD`, `DESKTOP`.

#### Konfiguration & Profile (`com.steamdeck.mc.config`)
* `ModConfig.java` – Globale Einstellungen (`steamdeck_essentials.json`) + aktive Arbeitskopie des aktuell geladenen `ControllerProfile`. Verwaltet Profil-Migration (`user_default.json`), automatischen Profilwechsel (`activateProfileFor(ControllerKind)`) und Speichern (`save()`).
* `ControllerProfile.java` – Per-Controller-Profil-Modell + JSON-Persistenz unter `config/steamdeck_essentials/profiles/<id>.json`. Definiert Paritäts-Gruppen (`DUAL_PAD`, `SINGLE_PAD`, `STANDARD`) und Controller-spezifische Defaults.

#### Input-Engine & Gyro (`com.steamdeck.mc.input` & `com.steamdeck.mc.input.gyro`)
* `SteamDeckInputManager.java` – Kern-Engine (Singleton): SDL3-Gamepad/Joystick-Erkennung nach Priorität, 20-Hz-Button/Trigger/Stick/Pad-Polling (`poll`), Frame-synchrones Kamera-/Maus-/OSK-Polling (`pollCameraFrame`, `pollFrame`), Fokus-Freigabe, Steam-Input-Bridge, Radial-/Grid-/Menü-/Inventar-Steuerung, Controller-Akku-Abfrage.
* `ControllerKind.java` – Hardware-IDs & Fähigkeiten-Flags (`isNativeValve()`, `isNativeController()`, `isDualSense()`, `hasNativeTouchpad()`, `hasDualTouchpads()`, `hasAdaptiveTriggers()`, `priority()`). Unterstützt Valve (`0x28DE`: Deck `0x1205`, SC 2026 `0x1302–1305`, SC 2015 `0x1102/1142/1106`, Virtual `0x11FF`) und Sony (`0x054C`: DualSense `0x0CE6`, DualSense Edge `0x0DF2`).
* `ActionSet.java` – Kontext-Sets `IN_GAME` und `MENUS` (ehemaliges `AIMING` wurde entfernt).
* `DeckButton.java` – Physische Inputs + virtuelle 3×3-Pad-Zellen (`LPAD_GRID_1..9`).
* `DeckAction.java` & `ActionBinding.java` – Semantische Aktionen, Minecraft-`KeyMapping`-Bindungen (`MAP:<name>`), freie Tastatur-Scancodes (`KEY:<code>:<label>`), Flags (`|haptic`, `|repeat`).
* `DiscreteStickMapper.java` & `Direction8.java` – 8-Wege-Bewegung (`W/A/S/D` + optionales Sprinten) vom linken Stick.
* `ControllerPresets.java` – Eingebautes Preset „SteamDeck Minecraft Controls“ (Workshop-1:1-Umsetzung).
* `LizardMode.java` – Schaltet beim nativen Steam Controller 2026 per `SDLHIDAPI` die Firmware-Maus/Tastatur-Emulation während des Spiels ab und beim Verlassen/Fokusverlust wieder ein.
* `SteamLayoutInstaller.java` – Installiert die gebündelte `.vdf`-Vorlage in Steam (und auf Wunsch mit Backup als App-Layout).
* `gyro/GyroManager.java` – Koordiniert Gyro-Backends (`SDL2GyroProvider`, `IIOGyroProvider`, `DsuGyroProvider`), entleert alle Provider pro Frame und wendet `player.turn()` je nach `gyroActivation` (`AIM_ONLY`, `ALWAYS`, `TRACKPAD_TOUCH`, `RIGHT_STICK_TOUCH`) an.
* `gyro/SDL2GyroProvider.java` – SDL3 Gamepad-/Sensor-Gyro (inkl. `tritonGyroYawCorrection` für SC 2026).
* `gyro/IIOGyroProvider.java` – Linux `/sys/bus/iio/devices` Fallback.
* `gyro/DsuGyroProvider.java` – Cemuhook UDP `127.0.0.1:26760` Fallback.
* `gyro/GyroProvider.java`, `gyro/GyroData.java` – Interface & Winkel-Delta-Struktur.

#### Power, Akku, Haptik & Adaptive Triggers (`com.steamdeck.mc.power`)
* `PowerManager.java` – Handheld-FPS-Management (Menü-Cap, AC vs. Akku, Thermal-90Hz-Guard, Low-Battery-Saver, AFK-Drosselung & OLED-Dimming).
* `BatteryReader.java`, `BatteryInfo.java` – Liest `/sys/class/power_supply/BAT0/`.
* `DynamicChunkThreading.java` – Drosselt Background-Chunk-Compiler (2 Akku / 3 Netz / 1 AFK).
* `SuspendHook.java` – Überwacht `org.freedesktop.login1.Manager` (`PrepareForSleep`) + Zeit-Watchdog für Auto-Pause & Singleplayer-Save vor Standby.
* `PerformanceTracker.java` – Optionale CSV-Telemetrie (`steamdeck_perf_log.csv`).
* `HapticFeedbackManager.java` – Routet Hotbar-, UI- und Trackpad-Klick-Haptik (Triton-Aktuatoren oder SDL-Rumble).
* `TritonTrackpadHaptics.java` – Direkte HID-Output-Reports (`0x82`) auf `/dev/hidraw*` für die Trackpad-Aktuatoren des SC 2026.
* `DualSenseAdaptiveTriggers.java` – Steuert L2/R2-Gegendruck des PS5 DualSense über `SDLGamepad.SDL_SendGamepadEffect` (Block-Härte, Bogen-Spannung, Schild-Block, Werkzeug-Haltbarkeits-Warnung, Basis-Klick/Widerstand).

#### GUI, HUD & OSK (`com.steamdeck.mc.gui`)
* `ControllerLayoutScreen.java` – Haupt-Konfigurator im Steam-Input-Stil (Controller-spezifisches Layout, Live-Highlight, Profil-Modal zum Wechseln/Kopieren/Löschen, Raster-Editor, Aktions-Picker).
* `ControllerArt.java` – Pixel-Art- und Fallback-Vektor-Zeichnung (`HANDHELD`, `PAD`, `PS5`), Live-Touchpad-Fingerpunkt, 11×11-Glyphen (inkl. PlayStation `✕ ○ □ △`).
* `GyroCube.java` – 3D-Gyro/Accel-Testquader im Konfigurator.
* `SteamDeckConfigScreen.java` – „Weitere Einstellungen“ (Tabs: Eco/Akku, Hardware/Bridge, Trackpads & DualSense-Trigger).
* `VirtualKeyboard.java` – Bildschirmtastatur (Voll- & Split-Modus, SteamOS-Dual-Pad-Fadenkreuze, universelle diskrete Joystick-Tastenauswahl blau/orange, DualSense-Touchpad-Maus, `TAB`, Umlaute).
* `RadialMenuOverlay.java` – 3-Ebenen-Radial-Menü auf dem rechten Stick (Hotbar 1–9, F-Tasten, F3+-Tools).
* `TrackpadGridOverlay.java` – 3×3-HUD-Overlay mit Item-Icons für das linke Trackpad.
* `TrackpadDebugOverlay.java` – Live-Sample-Rate/Jitter-Anzeige fürs rechte Trackpad.
* `BatteryHudRenderer.java` – Akku-HUD (Pausemenü/F3) und Inventar-Button-Legende.
* `ControllerToast.java` – Verbindungs-/Trennungs- & Low-Battery-Toast unten rechts.
* `LevelOsd.java` – Lautstärke-/Helligkeits-Balken oben in der Mitte.
* `ChatLayout.java` – Positioniert Chat oben (für OSK) oder unten (`AUTO`, `TOP`, `BOTTOM`).
* `ModMenuIntegration.java` – Öffnet `ControllerLayoutScreen` über ModMenu.

#### Mixins (`com.steamdeck.mc.mixin`)
* `HudMixin` – Frame-sync Kamera/Gyro + HUD-Overlays.
* `LevelRendererMixin` – Chunk-Thread-Wrapper.
* `FramerateLimitTrackerMixin` – Erzwingt PowerManager-FPS-Cap.
* `PauseScreenMixin` – Fügt `🎮 Controller`-Button und Akku-HUD ins Pausemenü ein.
* `DebugScreenOverlayMixin` – F3-Telemetrie-Zeile.
* `ChatScreenMixin`, `ChatComponentMixin`, `CommandSuggestionsMixin`, `EditBoxMixin` – Chat-oben-Layout, invertiertes Scrollen, Smart-OSK-Trigger.
* `TooltipMixin` – Verhindert abgeschnittene Tooltips auf 1280×800.
* `KeyboardHandlerMixin` – Filtert Mod-synthetisierte Tasten aus der „echte Tastatur benutzt“-Erkennung für `chatLayout: AUTO`.
* `AbstractContainerScreenAccessor`, `GameModeSwitcherScreenAccessor`, `KeyboardHandlerAccessor`, `KeyMappingAccessor` – Accessors für Slot-Snapping, F3+F4-Navigation und Input-Injection.

### 2.3 Laufzeit-Dateien (im Spielordner `.minecraft/`)
* `config/steamdeck_essentials.json` – Globale Einstellungen + `activeProfiles` (Zuordnung `ControllerKind -> Profil-ID`).
* `config/steamdeck_essentials/profiles/*.json` – Gespeicherte Controller-Layouts (`user_default.json`, `ps5_default.json`, eigene Kopien).
* `steamdeck_perf_log.csv` – Optionale CSV-Telemetrie (Handheld-Modus).

---

## 3. Wichtige Datenflüsse (Kurzreferenz)

* **Controller-Wechsel & Profile:** `SteamDeckInputManager.ensureGamepadConnected()` → erkennt `ControllerKind` per USB/BT VID:PID → `ModConfig.activateProfileFor(kind)` sichert altes Profil und lädt passendes Profil (bei Parität 1:1 kompatibel, fehlende Buttons werden ignoriert).
* **Frame- vs. Tick-Loop:**
  * `ClientTickEvents.END_CLIENT_TICK` (20 Hz): `SteamDeckInputManager.poll()` (Buttons, analoge Trigger, Sticks, DualSense Adaptive Triggers, Haptik, Menü/Slot-Navigation), `ControllerToast`, `ChatLayout`, `LevelOsd`, `PerformanceTracker`.
  * `HudMixin` (`@TAIL` pro Frame): `pollCameraFrame()` (rechter Stick + Trackpad-Kamera), `GyroManager.update()`, HUD-Overlays.
  * `ScreenEvents.afterExtract` (pro Frame in Menüs): `pollFrame()` (Trackpad-Maus / OSK-Tastatur / diskrete Joystick-OSK-Navigation), `VirtualKeyboard.render()`.

---

## 4. Build, Test & Deployment

* **JDK:** Java 25 (`/home/lukhicken/.jdks/jdk-25+36`).
* **Compile-Classpath:** Wird in `build.gradle` automatisch aus `~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher` für Minecraft `26.3` und LWJGL `3.4.1` gelesen (bei Bedarf mit `-PprismDir=...` überschreibbar).
* **Bauen (offline möglich):**
  ```bash
  JAVA_HOME=/home/lukhicken/.jdks/jdk-25+36 ./gradlew build --offline
  ```
  Ergebnis: `SteamDeckEssentials-<mod_version>+mc26.3.jar` im Projekt-Root.
* **Direkt in Prism Launcher installieren (Standard-Instanz `26.3`):**
  ```bash
  JAVA_HOME=/home/lukhicken/.jdks/jdk-25+36 ./gradlew installToPrism -PprismInstance=26.3
  ```
* **Controller-Pixel-Art neu generieren:** siehe Kommentar in `tools/pixelize_svg.py` (erfordert `magick` + Python `Pillow`).

---

## 5. Wartungshinweise

* Neue Features sollten immer gegen die Leitprinzipien in Abschnitt 1 geprüft werden (Vanilla-Parität, Handheld-vs-Desktop-Trennung, Per-Controller-Profile).
* Offene Ideen und Merklisten-Punkte stehen in `To-Do.txt`.
