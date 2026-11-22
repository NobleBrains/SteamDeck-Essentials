# SteamDeck Essentials (Minecraft 26.3 Fabric Mod)

Eine umfassende All-in-One-Optimierungs- und Controller-Mod für **Steam Deck (LCD & OLED)**, **Steam Controller (2026 & 2015)** und **PS5 DualSense / DualSense Edge** unter Minecraft 26.3 (SDL3) – mit sauberer Trennung von Handheld- und Desktop-Modus sowie nativer Controller-Unterstützung ohne Steam.

> **Für Entwickler & Coding-Agents:** Siehe [AGENTS.md](AGENTS.md) für Projekt-Philosophie, vollständige Ordner-/Datei-Landkarte, Architektur-Datenflüsse und Build-Befehle, sowie [docs/FINDINGS.md](docs/FINDINGS.md) für Hardware-/SDL3-/HID-Details und [CHANGELOG.md](CHANGELOG.md) für die Änderungshistorie.

---

## 🖥️ 0. Handheld- & Desktop-Modus (`com.steamdeck.mc.platform`)
Die Mod läuft in einem von zwei Modi:

| | **Handheld** | **Desktop** |
|---|---|---|
| FPS-Caps (Menü, AFK, Akku, 90/60-Hz-Thermal-Cap) | ✅ | ❌ Vanilla |
| AFK-Dimming, Low-Battery-Saver, Chunk-Thread-Limit | ✅ | ❌ |
| Akku-HUD (Pause/F3), CSV-Performance-Tracker | ✅ | ❌ |
| Suspend-Save (Welt speichern beim Standby) | ✅ | ❌ |
| Chat oben + invertiertes Scrollen (auto bei Controller-Nutzung) | ✅ | ✅ (nur bei `chatLayout: AUTO` + Controller) |
| OSK automatisch öffnen (manuell per `F12` immer möglich) | ✅ | ✅ (bei Controller-Chat-Öffnung) |
| Steam-Input-Bridge-Tasten (`[ ] \ / F13 F14`) abfangen | ✅ | ❌ (optional: `steamInputBridgeOnDesktop`) |
| Steam-Layout automatisch installieren | ✅ | ❌ (Button im Tab `⚙️ Bridge`) |
| Controller-Engine, Profile, Radial-Menü, Gyro, Haptik/Trigger | ✅ | ✅ |

* **Automatische Erkennung (`deviceMode: "AUTO"`):** über die DMI-Kennungen in `/sys/class/dmi/id` (funktioniert auch in der Prism-Flatpak-Sandbox). Erkannt werden Steam Deck LCD (`Jupiter`, 60-Hz-Cap) und OLED (`Galileo`, 90-Hz-Cap), außerdem ROG Ally, Legion Go, MSI Claw, AYANEO, OneXPlayer, GPD Win und ZOTAC Zone. Fallback: Umgebungsvariable `SteamDeck=1` (SteamOS Game Mode). Laptops gelten als Desktop.
* **Manuell überschreiben:** Im Spiel unter `Pausemenü → 🎮 Controller → ☰ Weitere Einstellungen → ⚡ Akku & Eco → Gerätemodus` (Auto → Handheld → Desktop) oder in der Config `"deviceMode": "HANDHELD"` / `"DESKTOP"`. Der Wechsel greift sofort, ohne Neustart.

## 🎮 0b. Native Controller ohne Steam (Steam Deck, Steam Controller 2026 & PS5 DualSense)
* **Steam Deck & Steam Controller (2026 „Triton“ / 2015):** SDL3 liest Valve-Controller über seinen HIDAPI-Treiber direkt (`SDL_JOYSTICK_HIDAPI_STEAM=1`, abschaltbar mit `enableNativeSteamControllers`). Trackpad-Kamera, 3×3-Raster, Druck-Klick, Trackpad-Aktuator-Haptik (`0x82`) und automatisches Abschalten des Firmware-Lizard-Modes laufen nativ.
* **PS5 DualSense & DualSense Edge (`054c:0ce6` / `054c:0df2`):**
  * **Zentral-Touchpad:** in Menüs standardmäßig als Mauszeiger aktiv; im Spiel frei konfigurierbar (`Kamera`, `Nur Taste` oder `Aus`).
  * **Analoge Trigger & Adaptive Triggers (L2/R2):** einstellbare Auslöseschwellen (0–100 % mit Live-Balken) sowie echter Gegendruck über SDL3 `SDL_SendGamepadEffect` – einzeln togglebar für **Block-Härte beim Abbauen**, **Bogen-/Armbrust-/Dreizack-Spannung**, **Schild-Block**, **Werkzeug-Haltbarkeits-Warnung (< 10 %)** und Basis-Klickpunkt/Dauerwiderstand.
  * **Eigene PS5-Ansicht:** Pixel-Art-Umriss, Live-Fingerpunkt auf dem Touchpad und PlayStation-Glyphen (`✕ ○ □ △`).
* **Per-Controller-Profile & Parität (`config/steamdeck_essentials/profiles/*.json`):**
  * Jeder Controller-Typ speichert und lädt sein eigenes aktives Layout (z. B. Steam Deck mit `R-Stick = Radial-Menü`, PS5 DualSense mit `R-Stick = Kamera`).
  * Bestehende Nutzer-Layouts werden beim ersten Start automatisch nach `user_default.json` migriert und nie überschrieben.
  * Controller mit gleicher Parität (`Steam Deck` ↔ `Steam Controller 2026/2015`) teilen Layouts 1:1; beim Laden zwischen unterschiedlichen Controllern bleiben gemeinsame Tasten erhalten, während fehlende Tasten ignoriert werden.

---

## ⚡ 1. Power & Eco-Engine (`com.steamdeck.mc.power`)
* **Intelligenter Akku- vs. Netzbetrieb (AC) & 90-Hz-Thermal-Cap:**
  * **Menü- & Inventar-Cap:** 30 FPS im Akkubetrieb (`menuFpsCap`), flüssige 60 FPS im Netzbetrieb (`acMenuFpsCap`).
  * **Unfokussiert / Minimiert:** Automatische Drosselung auf 5–10 FPS (`unfocusedFpsCap`), kompatibel mit SteamOS Gamescope/Wayland.
  * **90-Hz-Thermal-Fan-Guard:** Begrenzt unnötige >90 FPS auf die 90 Hz des OLED-Displays, damit der Lüfter auch am Ladekabel flüsterleise bleibt.
* **Dynamische AFK-Drosselung & OLED-Screen-Dimming:**
  * Stufenloses Absenken der FPS bei Inaktivität (Akku: ab 2 Min. bis -80 % / 15 FPS; Netzbetrieb: ab 4 Min. bis -50 % / 40 FPS).
  * Sanftes OLED-Screen-Dimming (65 % im Akkubetrieb, 30 % im Netzbetrieb) und Reduktion der Hintergrund-Chunk-Threads auf 1 Idle-Worker.
  * Sofortiges Aufwachen (0 ms Latenz) bei jeder Stick-, Button-, Trackpad-, Touch- oder Tastatureingabe.
* **Low-Battery Eco-Saver:** Automatischer Batterieschutz bei ≤ 20 % (60 FPS Cap) und ≤ 10 % Akku (45 FPS Cap).
* **Dynamic Chunk Threading:** Begrenzt Hintergrund-Chunk-Compiler auf 2 Threads (Akku) bzw. 3 Threads (AC).
* **Battery-Monitor, HUD & CSV-Telemetrie:** Liest `/sys/class/power_supply/BAT0/` (Pausemenü & F3) und schreibt optional `steamdeck_perf_log.csv`.
* **Suspend/Sleep-Hook:** Erkennt SteamOS Sleep über `org.freedesktop.login1.Manager` (`PrepareForSleep`) und Watchdog; pausiert und speichert Singleplayer-Welten sofort.

---

## 🕹️ 2. Controller-Konfigurator (`Pausemenü → 🎮 Controller`)
* **Ansicht wie Steam Input:** Pixel-Art-Zeichnung für Steam Deck, Steam Controller und PS5 DualSense mit Live-Tasten-Highlight, Touchpad-Fingerpunkt und 3D-Gyro-Testquader.
* **Action-Sets** (`Im Spiel`, `Menüs & Inventar`) mit `L1/R1` wechseln. `A` belegt, `X` leert, `Y` wechselt die Ansicht, `Start/Menü` öffnet weitere Einstellungen, `Select/Create` öffnet die **Profil-Verwaltung** (Wechseln, Kopie speichern, Löschen), `B` geht zurück.
* **Linkes Trackpad als Raster 3×3** (Dual-Pad-Controller): neun zusätzliche, pro Action-Set belegbare Felder mit Item-Icon-Overlay und Stick-Schutz für die obere Reihe (`gridTouchCancel`).
* **Controller-Toast** unten rechts beim Verbinden/Trennen inkl. Controller-Akkustand und Low-Battery-Warnung.

## 🎯 3. Frame-synchrones 60/90-Hz Gyro-Aiming (`com.steamdeck.mc.input.gyro`)
* Multi-Provider (`SDL2GyroProvider`, `IIOGyroProvider`, `DsuGyroProvider`), umschaltbar zwischen *Nur beim Zielen*, *Immer*, *Trackpad berührt* und *R-Stick berührt*.

## ⌨️ 4. On-Screen Keyboard & Handheld GUI (`com.steamdeck.mc.gui`)
* **SteamOS Dual-Pad Fadenkreuze** auf Steam Deck / Steam Controller, **diskrete blaue/orange Joystick-Tastenauswahl** (`L3/L2/L1` & `R3/R2/R1` / `A`) auf allen Controllern, sowie normale **Touchpad-Maus** auf dem PS5 DualSense.
* Feste SteamOS-Hotkeys (`A` Tippen, `X` Löschen, `Y` Leerzeichen, `Start` Senden, `B` Tastatur aus), `TAB`-Taste, deutsche Umlaute (`ä`, `ö`, `ü`, `ß` unter `?123`), Smart-Auto-Open und Handheld-Tooltip-Fix.

---

## 🛠️ Entwicklung & Build
```bash
JAVA_HOME=/home/lukhicken/.jdks/jdk-25+36 ./gradlew build --offline
JAVA_HOME=/home/lukhicken/.jdks/jdk-25+36 ./gradlew installToPrism -PprismInstance=26.3
```

## ⚙️ Konfiguration
* Globale Config: `.minecraft/config/steamdeck_essentials.json`
* Per-Controller-Profile: `.minecraft/config/steamdeck_essentials/profiles/*.json`
