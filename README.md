# SteamDeck Essentials (Minecraft 26.3 Fabric Mod)

Eine umfassende All-in-One-Optimierungsmod speziell für das Steam Deck (LCD & OLED) unter Minecraft 26.3 (SDL3) – mit Desktop-Modus für den PC und nativer Unterstützung des Steam Controllers (2026) ohne Steam.

---

## 🖥️ 0. Handheld- & Desktop-Modus (`com.steamdeck.mc.platform`)
Die Mod läuft in einem von zwei Modi:

| | **Handheld** | **Desktop** |
|---|---|---|
| FPS-Caps (Menü, AFK, Akku, 90/60-Hz-Thermal-Cap) | ✅ | ❌ Vanilla |
| AFK-Dimming, Low-Battery-Saver, Chunk-Thread-Limit | ✅ | ❌ |
| Akku-HUD (Pause/F3), CSV-Performance-Tracker | ✅ | ❌ |
| Suspend-Save (Welt speichern beim Standby) | ✅ | ❌ |
| Chat oben + invertiertes Scrollen (für die Bildschirmtastatur) | ✅ | ❌ Vanilla |
| OSK automatisch öffnen (manuell per `F12` immer möglich) | ✅ | ❌ |
| Steam-Input-Bridge-Tasten (`[ ] \ / F13 F14`) abfangen | ✅ | ❌ (optional: `steamInputBridgeOnDesktop`) |
| Steam-Layout automatisch installieren | ✅ | ❌ (Button im Tab `⚙️ Bridge`) |
| Controller-Engine, Action-Sets, Radial-Menü, Gyro, Haptik | ✅ | ✅ |

* **Automatische Erkennung (`deviceMode: "AUTO"`):** über die DMI-Kennungen in `/sys/class/dmi/id` (funktioniert auch in der Prism-Flatpak-Sandbox). Erkannt werden Steam Deck LCD (`Jupiter`, 60-Hz-Cap) und OLED (`Galileo`, 90-Hz-Cap), außerdem ROG Ally, Legion Go, MSI Claw, AYANEO, OneXPlayer, GPD Win und ZOTAC Zone. Fallback: Umgebungsvariable `SteamDeck=1` (SteamOS Game Mode). Laptops gelten als Desktop.
* **Manuell überschreiben:** Im Spiel unter `Pausemenü → 🎮 Controller → ⚡ Akku & Eco → Gerätemodus` (Auto → Handheld → Desktop) oder in der Config `"deviceMode": "HANDHELD"` / `"DESKTOP"`. Der Wechsel greift sofort, ohne Neustart.
* Der aktive Modus steht im Log (`[PLATFORM]`) und im F3-HUD.

## 🎮 0b. Steam Controller (2026) & Steam Deck ohne Steam
* SDL3 (in Minecraft eingebaut) kann Valve-Controller über seinen **HIDAPI-Treiber** direkt lesen. Die Mod aktiviert dafür `SDL_JOYSTICK_HIDAPI_STEAM=1` (abschaltbar mit `enableNativeSteamControllers`; eine selbst gesetzte Umgebungsvariable hat Vorrang).
* **Geräteauswahl:** native Valve-Controller (Steam Deck, Steam Controller 2026 `28de:1302–1305`, 2015) werden vor dem virtuellen Steam-Input-Gamepad und anderen Gamepads bevorzugt – auch wenn sie später eingesteckt werden.
* **Trackpad-Kamera:** Ohne Steam Input ist das rechte Trackpad keine Maus mehr. Bei nativen Valve-Controllern dreht es deshalb direkt die Kamera (`nativeTrackpadCamera`, Empfindlichkeit über „Trackpad Sensitivität“: 100 % = ein Wisch über das ganze Pad ≈ 90°).
* **Rechter Stick als Kamera:** Wenn das Radial-Menü aus ist, steuert der rechte Stick die Kamera pro Frame (`rightStickCameraSpeed`, Standard 180°/s, quadratische Kennlinie).
* **Diagnose:** Beim Verbinden schreibt die Mod Name, Vendor/Product-ID, Typ, Anzahl Touchpads, Gyro/Accel, Rumble und Zusatztasten (L4/R4/L5/R5, Touchpad, MISC) ins Log (`[INPUT] Connected SDL gamepad ...`).
* **Hinweis:** Läuft Steam im Hintergrund mit Desktop-Layout, kann Steam das Trackpad zusätzlich als Maus nutzen (doppelte Kamerabewegung) – dann `nativeTrackpadCamera` ausschalten oder Steam beenden. Linux ≥ 7.3 bringt zusätzlich den Kernel-Treiber `hid-steam` für den 2026-Controller mit.

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
* *Alle Funktionen dieses Abschnitts sind nur im **Handheld-Modus** aktiv.*
* **Low-Battery Eco-Saver:**
  * Automatischer Batterieschutz bei ≤ 20 % (60 FPS Cap) und ≤ 10 % Akku (45 FPS Cap) mit dezentem HUD-Hinweis.
* **Dynamic Chunk Threading:**
  * Begrenzt Hintergrund-Chunk-Compiler (`SectionRenderDispatcher`) auf 2 Threads im Akkubetrieb bzw. 3 Threads im Netzbetrieb für konstante 90/60 FPS ohne CPU-Lastspitzen.
* **Battery-Monitor, HUD & CSV-Telemetrie:**
  * Liest Akkustand, Watt-Verbrauch, Spannung und Restlaufzeit aus `/sys/class/power_supply/BAT0/`.
  * Anzeige oben links im Pause-Menü und im F3-Diagnose-HUD sowie optionaler CSV-Performance-Tracker (`steamdeck_perf_log.csv`).
* **Suspend/Sleep-Hook:**
  * Erkennt SteamOS Sleep über `org.freedesktop.login1.Manager` (`PrepareForSleep`) und Watchdog; pausiert und speichert das Singleplayer-Spiel sofort.

---

## 🎮 2. Input-, Action-Set- & Controller-Engine (`com.steamdeck.mc.input`)
* **3 Kontext-basierte Action-Sets (`IN_GAME`, `MENUS`, `AIMING`):**
  * Automatischer Wechsel zwischen Gameplay, Inventar/Menüs und Bogen-/Fernrohr-Zielen.
  * Frei konfigurierbar über das In-Game-Dashboard (`Pausemenü -> 🎮 Controller`), inklusive **eigener Tastatur-Tasten mit Spitznamen** (SDL3-Scancodes & Presets für Minimap, VoiceChat, Wegpunkte, etc.).
* **Vollständige Menü- & Inventar-Navigation per Controller:**
  * **Menüs & Pausemenü:** Navigation per linkem Stick oder D-Pad (inkl. Auto-Repeat), Bestätigung mit jedem auf `Mausklick Links` (`A`, `R2`) belegten Button.
  * **Inventar Slot-Snapping (`AbstractContainerScreen`):** In Kisten, Öfen, Werkbänken und im Inventar springen D-Pad und linker Stick präzise von Slot zu Slot, begleitet von einer dynamischen Button-Legende am unteren Bildschirmrand.
  * **Gamemode-Switcher (`F3+F4`):** Direktes Umschalten per D-Pad/Stick und Bestätigen mit `A`.
* **3-Ebenen Radial-Menü auf dem rechten Joystick (Umschalten per `R3`):**
  * **Ebene 1 (`🎒 Hotbar 1–9`):** 9 Segmente mit 3D-Item-Icons, Haltbarkeit und sofortiger Live-Auswahl.
  * **Ebene 2 (`🖥️ F-Tasten`):** `F1`, `F2` (Screenshot), `F3`, `F3+2` (FPS/TPS-Graph), `F5`, `F8`, `F3+F4`, `F3+F6`.
  * **Ebene 3 (`🔧 F3+ Utilities`):** `F3+G` (Chunk-Grid), `F3+B` (Hitboxen), `F3+H` (Tooltips), `F3+A`, `F3+N`, `F3+D`, `F3+C`, `F3+T`.
* **Steam Input Hardware-Bridge & 1-Klick-Layout-Installer:**
  * Volle Unterstützung für alle 4 Rücktasten (**L4, R4, L5, R5**), beide **Trackpad-Klicks** und kapazitive **Stick-Touch-Sensoren** (`L-Stick Touch`, `R-Stick Touch`).
  * Beim Spielstart wird die mitgelieferte `controller_neptune_steamdeck_essentials.vdf` nur als **Vorlage** installiert (in Steam unter „Vorlagen“ wählbar). Eigene Layouts bleiben unangetastet.
  * Der 1-Klick-Button im Tab `⚙️ Bridge` setzt das Layout zusätzlich als aktives Layout für Prism Launcher / Minecraft. Ein vorhandenes eigenes Layout wird vorher gesichert (`controller_neptune.vdf.bak-steamdeck-essentials`, weitere Sicherungen mit Zeitstempel).
* **Direktionale LRA-Haptik & Gameplay-Haptik:**
  * Seitengetrennte Haptik-Impulse beim Hotbar-Scrollen (inkl. Auto-Repeat beim Halten), beim Bogen-/Armbrust-Spannen und bei niedriger Werkzeug-Haltbarkeit (< 10 %).

---

## 🎯 3. Frame-synchrones 90-Hz Contextual Gyro-Aiming (`com.steamdeck.mc.input.gyro`)
* **Multi-Provider-Architektur (60/90 Hz Render-Frame-synchron):**
  * Linux IIO (`/sys/bus/iio/devices/iio:device*`)
  * Native SDL3 Gamepad- & Sensor-API
  * Universal DSU / Cemuhook Motion Server (`127.0.0.1:26760` mit verlustfreier Paket-Akkumulation)
* **Zustandsbasiertes Triggern:**
  * Aktiv beim Spannen/Nutzen von Bogen (`BOW`), Armbrust (`CROSSBOW`), Dreizack (`TRIDENT`) oder Fernrohr (`SPYGLASS`).
  * Schaltet während des Zielens automatisch das Radial-Menü auf dem rechten Stick zugunsten von Präzisions-Zielen (2 % Deadzone) um.

---

## ⌨️ 4. On-Screen Keyboard & Handheld GUI (`com.steamdeck.mc.gui`)
* **SteamOS Dual-Cursor Trackpad- & Stick-Tippen (60/90 Hz):**
  * Gleichzeitiges Tippen mit beiden Daumen über zwei Fadenkreuze (Türkis links, Amber rechts) und Auslösen per `LT`/`LB`/`Trackpad L` bzw. `RT`/`RB`/`Trackpad R`.
  * Integrierte **`TAB`-Taste** (für Befehls-Autovervollständigung) sowie **deutsche Umlaute** (`ä`, `ö`, `ü`, `ß` im `?123`-Modus auf `A`, `O`, `U`, `S`).
* **Smart Auto-Open & Handheld-Tooltip-Fix:**
  * Öffnet sich automatisch im Chat, bleibt im Kreativ-Inventar dezent als `KB`-Button eingeklappt und verhindert abgeschnittene Tooltips bei 1280×800 (16:10).

---

## ⚙️ Konfiguration
Gespeichert unter `.minecraft/config/steamdeck_essentials.json`.
Werte können im Spiel oder via JSON editiert werden.
