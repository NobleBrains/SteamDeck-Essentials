# SteamDeck Essentials – Changelog & Debugging Guide

Dieses Dokument führt alle technischen Änderungen mit ausführlicher Begründung auf und dient Entwicklern und Testern als Leitfaden zur Diagnose und Fehlerbehebung.

---

## 📋 Inhaltsverzeichnis
1. [Version 1.0.0 – Meilensteine & Änderungen](#version-100)
   - [Initiales Fundament](#initiales-fundament)
   - [Migration auf SDL3](#migration-auf-sdl3)
   - [SDL3-Subsystem-Initialisierung & Keybind-Klicks](#sdl3-subsystem-initialisierung--keybind-klicks)
   - [Gamescope & Fullscreen-Fix](#gamescope--fullscreen-fix)
   - [OSK-Verfeinerung & Quick-Bar-Entfernung](#osk-verfeinerung--quick-bar-entfernung)
   - [Action-Sets, ModMenu & Visuelles Controller-Dashboard](#action-sets-modmenu--visuelles-controller-dashboard)
2. [🔍 Debugging-Maßnahmen & Diagnose-Werkzeuge](#debugging-maßnahmen--diagnose-werkzeuge)
   - [Echtzeit-Telemetrie im F3-Bildschirm](#echtzeit-telemetrie-im-f3-bildschirm)
   - [Strukturierte Log-Präfixe](#strukturierte-log-präfixe)
   - [Hardware- & Sensor-Prüfung über Linux-Shell](#hardware---sensor-prüfung-über-linux-shell)

---

## 🚀 Version 1.0.0

### Action-Set Menü-Linksklick-Fix (`MOUSE_LEFT_CLICK`), Inventar-Slot-Snapping & 1-Klick Steam-Input-Installer
* **Änderung:** Behebung des Menü-Klick-Bugs in [`SteamDeckInputManager.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/SteamDeckInputManager.java) und [`SteamDeckConfigScreen.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java), Korrektur aller SDL3-Scancodes in [`ModConfig.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/config/ModConfig.java), Hinzufügen von [`SteamLayoutInstaller.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/SteamLayoutInstaller.java) und [`AbstractContainerScreenAccessor.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/mixin/AbstractContainerScreenAccessor.java).
  * **Ursache & Lösung des `MOUSE_LEFT_CLICK`-Bugs in Menüs:**
    1. In Minecraft 26.3 prüft `AbstractWidget.isMouseOver()` nur den gecachten Render-Wert `isHovered`. Wenn per Stick/D-Pad zu einem Button navigiert wurde (`screen.getFocused() != null`), klickte `MOUSE_LEFT_CLICK` bisher an die alte Mauszeiger-Position ins Leere. Nun löst `MOUSE_LEFT_CLICK` auf jedem beliebigen Button zuverlässig das fokussierte Menü-Element aus, sofern der Mauszeiger nicht aktiv über einem anderen Widget schwebt.
    2. In `SteamDeckConfigScreen.warpCursorToGui()` wird neben `SDL_WarpMouseInWindow` nun auch direkt `client.mouseHandler.onMove(...)` aufgerufen, damit die Mauskoordinaten unter Gamescope/Wayland ohne Frame-Verzögerung synchron sind.
    3. `DeckButton.RIGHT_TRACKPAD` wird nun im SDL3-Gamepad-Polling abgefragt und `MOUSE_QUICK_MOVE` übergibt die korrekte SDL3-Konstante `InputConstants.MOD_SHIFT` (`3`).
  * **Korrektur der SDL3-Scancodes (Bridge & Presets):**
    * `KeyEvent.key()` liefert in Minecraft 26.3 SDL3-Scancodes (USB HID) statt alter GLFW/ASCII-Codes. Die Standard-Bridge-Tasten wurden auf `47` (`[`), `48` (`]`), `49` (`\`), `56` (`/`) sowie `104` (`F13`) und `105` (`F14`) aktualisiert (inkl. automatischer Migration bestehender Configs) und die 8 Schnell-Presets (`M=16`, `B=5`, `V=25`, `C=6`, `L=15`, `P=19`, `T=23`) angepasst.
  * **1-Klick Steam-Input-Installer & aktualisiertes VDF-Template:**
    * [`controller_neptune_steamdeck_essentials.vdf`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/resources/assets/steamdeck_essentials/controller_neptune_steamdeck_essentials.vdf) belegt alle 4 Rücktasten (`L4`, `R4`, `L5`, `R5`) direkt mit den passenden Bridge-Tasten und das rechte Trackpad mit Maus + Linksklick.
    * `SteamLayoutInstaller` kopiert die Vorlage beim Start sowie per Klick auf den neuen Button im Tab `⚙️ Bridge` direkt in die Steam-Ordner.
  * **Inventar Slot-Snapping & Controller-Button-Legende:**
    * In Kisten, Öfen, Werkbänken und Inventaren (`AbstractContainerScreen`) springen D-Pad und linker Stick direkt zum nächsten aktiven Slot in Pfeilrichtung. Unten links zeigt [`BatteryHudRenderer.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/gui/BatteryHudRenderer.java) eine dynamische Button-Legende; im Pause-Menü sitzt die Batterie-Pill nun oben links ohne Überlappung mit dem `🎮 Controller`-Button.
  * **60/90-Hz Gyro & OSK Dual-Cursor, `TAB`-Taste, Umlaute, Gameplay-Haptik & Low-Battery-Saver:**
    * Gyro-Updates und OSK-Fadenkreuze laufen nun pro Render-Frame (60/90 Hz statt 20 Hz Client-Tick), das OSK enthält wieder eine `TAB`-Taste sowie `ä`, `ö`, `ü`, `ß` (im `?123`-Modus), und bei ≤ 20 % / ≤ 10 % Akku schaltet sich automatisch der Low-Battery Eco-Saver ein.

### SDL3-Mausklick-Fix in Menüs & Vollständige Menü-/Gamemode-Navigation per D-Pad / Linkem Stick + `A`
* **Änderung:** Korrektur der Maustasten-IDs in [`SteamDeckInputManager.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/SteamDeckInputManager.java) sowie Implementierung der Controller-Menünavigation in [`SteamDeckInputManager.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/SteamDeckInputManager.java), [`SteamDeckConfigScreen.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java) und [`GameModeSwitcherScreenAccessor.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/mixin/GameModeSwitcherScreenAccessor.java).
  * **Ursache & Diagnose des Menü-Klick-Bugs (`R2` / `A` / `X` / `L2`):**
    * Durch Bytecode-Inspektion (`javap -c`) von `net.minecraft.client.MouseHandler.onButton`, `AbstractWidget.isValidClickButton` und `AbstractContainerScreen.getContainerClickButton` in Minecraft 26.3 zeigte sich: Mit dem Wechsel von GLFW auf **SDL3** sind Maustasten nicht mehr 0-basiert (`0 = Links`, `1 = Rechts`), sondern nutzen die **1-basierten SDL3-Konstanten** (`SDL_BUTTON_LEFT = 1`, `SDL_BUTTON_MIDDLE = 2`, `SDL_BUTTON_RIGHT = 3`).
    * Die Übergabe von `0` in `new MouseButtonInfo(0, 0)` wurde von SDL3 als *„keine/ungültige Taste"* verworfen. Mit `SDLMouse.SDL_BUTTON_LEFT (1)`, `SDLMouse.SDL_BUTTON_RIGHT (3)` und `SDLMouse.SDL_BUTTON_MIDDLE (2)` funktionieren Linksklick (`R2`/`A`), Rechtsklick (`L2`/`X`) und Schnell-Transfer (`Y`, `SDL_BUTTON_LEFT` + Shift-Modifier `1`) nun in allen Inventaren und Menüs sofort.
  * **Gamemode-Umschalter (`F3+F4`) per D-Pad / Linkem Stick + `A`:**
    * Im `GameModeSwitcherScreen` kann mit dem **D-Pad** oder dem **linken Joystick** (inkl. Auto-Repeat beim Halten und Haptik-Tick) zwischen Kreativ, Überleben, Abenteuer und Zuschauer gewechselt werden. Ein Druck auf **`A`** bestätigt die Auswahl (`switchToHoveredGameMode`) und schließt das Fenster.
  * **Menünavigation (Pausemenü, Optionen & Untermenüs) per D-Pad / Linkem Stick + `A`:**
    * In allen Menüs (Pausemenü, Optionen, Untermenüs sowie im `SteamDeckConfigScreen`) bewegt der **linke Joystick** oder das **D-Pad** den Fokus zwischen Buttons, Slidern und Listen (mit Auto-Repeat nach 340 ms). Ein Druck auf **`A`** bestätigt den fokussierten Menüpunkt, während `R2` (oder `A` bei aktiver Mausbewegung) weiterhin direkt an der Mauszeiger-Position klickt.

### 3-Ebenen Radial-Menü auf dem rechten Joystick (Umschalten per R3)
* **Änderung:** Implementierung von [`RadialMenuOverlay.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/gui/RadialMenuOverlay.java), [`KeyboardHandlerAccessor.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/mixin/KeyboardHandlerAccessor.java) und Integration in [`SteamDeckInputManager.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/SteamDeckInputManager.java) sowie [`HudMixin.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/mixin/HudMixin.java).
  * **Begründung:** Da die Kamera im normalen Gameplay über das rechte Trackpad gesteuert wird, ist der rechte Joystick frei für ein schnelles, mehrschichtiges Auswahlrad.
  * **Funktionsweise:**
    * **Auslenken des rechten Sticks:** Öffnet das Radial-Menü mit Live-Hervorhebung und taktilem Haptik-Tick bei jedem Segmentwechsel. Beim Loslassen des Sticks wird die gewählte Funktion ausgeführt.
    * **R3 (Rechter Stick-Klick):** Schaltet nahtlos zwischen den **3 Radial-Menü-Ebenen** um (sowohl bei geöffnetem als auch bei geschlossenem Rad, inkl. kurzem HUD-Toast):
      1. **`[1/3] 🎒 Hotbar 1–9`:** 9 Segmente im Kreis mit echten 3D-Item-Icons, Stack-Größen, Haltbarkeitsbalken und verzögerungsfreier Live-Auswahl von Slot 1 bis 9.
      2. **`[2/3] 🖥️ F-Tasten (F1–F8)`:** `F1` (HUD ein/aus), `F2` (Screenshot), `F3` (Debug-HUD), `F3+2` (FPS/TPS-Graph), `F5` (Perspektive), `F8` (Kino-Kamera), `F3+F4` (Spielmodus-Umschalter), `F3+F6` (Debug-Menü).
      3. **`[3/3] 🔧 F3+ Utilities`:** `F3+G` (Chunk-Grenzen), `F3+B` (Hitboxen), `F3+H` (Erweiterte Item-Tooltips), `F3+A` (Chunks neu laden), `F3+N` (Spectator ↔ Kreativ), `F3+D` (Chat leeren), `F3+C` (Koordinaten/TP-Befehl kopieren), `F3+T` (Ressourcenpacks neu laden).

### Intelligenter Netzbetrieb (AC) vs. Akkubetrieb & Lüfter-Leisemodus (90/60 FPS Target)
* **Änderung:** Differenzierte Stromspar- und Thermalkontrolle in [`PowerManager.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/power/PowerManager.java) und [`DynamicChunkThreading.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/power/DynamicChunkThreading.java).
  * **Begründung:** Im Netzbetrieb (am Ladekabel) sollen Komfort-Einschränkungen wie starkes Dimmen oder frühes FPS-Drosseln zurückgefahren werden, während die Performance-Optimierungen aktiv bleiben müssen, um 90 FPS (OLED) bzw. 60 FPS (LCD) konstant zu halten und das Gebläse (Lüftergeräusch) minimal zu halten.
  * **Umsetzung:**
    * **Menü-FPS:** 60 FPS im Netzbetrieb (statt 30 FPS im Akkubetrieb).
    * **AFK-Drosselung im Netzbetrieb:** Startet erst nach 4 Minuten (statt 2 Min.), senkt sanfter um 5 %/Min. und drosselt maximal um 50 % auf mindestens 40 FPS (statt 15 FPS).
    * **OLED-Dimming im Netzbetrieb:** Startet erst nach 5 Minuten (statt 3 Min.) und dunkelt maximal um dezente 30 % (statt 65 %) ab.
    * **Chunk-Threading & Thermal-Cap für leises Gebläse:** `DynamicChunkThreading` bleibt auch im Netzbetrieb aktiv und nutzt dort kontrollierte **3 Worker-Threads** (Akkubetrieb: 2 Threads, AFK: 1 Thread) mit reduzierter Priorität, ergänzt durch den 90-Hz-Thermal-Cap. Das verhindert 100-%-CPU/GPU-Lastspitzen, hält das 90/60-FPS-Target stabil und sorgt für einen flüsterleisen Lüfter.

### Gyro-Fix (Kontextabhängig beim aktiven Bogen-/Fernrohr-Zielen)
* **Änderung:** Überarbeitung von [`GyroManager.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/gyro/GyroManager.java), [`DsuGyroProvider.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/gyro/DsuGyroProvider.java) und [`SDL2GyroProvider.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/gyro/SDL2GyroProvider.java).
  * **Begründung:** `DsuGyroProvider` meldete beim Start fälschlicherweise immer Verfügbarkeit, wodurch `SDL2GyroProvider` übersprungen wurde. Zudem wurde das Zusammenspiel mit Steam Input optimiert.
  * **Umsetzung:** Alle Gyro-Backends werden parallel abgefragt; zusätzlich schaltet der rechte Stick beim aktiven Spannen eines Bogens/Fernrohrs (`player.isUsingItem()`) automatisch vom Radial-Menü auf hochpräzises Zielen (2 % Deadzone) um.

### Direktionales Haptik-Feedback beim Hotbar-Wechsel (Trackpads)
* **Änderung:** Implementierung von [`HapticFeedbackManager.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/power/HapticFeedbackManager.java) mit direkter Ansteuerung der LRA-Aktuatoren über `SDLGamepad.SDL_RumbleGamepad` und `SDLJoystick.SDL_RumbleJoystick`.
  * **Begründung:** Der Spieler wünschte taktiles Feedback beim Durchschalten der Hotbar-Slots, wobei genau das Trackpad rüttelt, in dessen Richtung geschaltet wird.
  * **Umsetzung:**
    * Wechsel nach links (`HOTBAR_PREV`): Ein präziser 35ms-Impuls auf dem linken LRA-Aktuator des linken Trackpads (`low_frequency` Rumble).
    * Wechsel nach rechts (`HOTBAR_NEXT`): Ein präziser 35ms-Impuls auf dem rechten LRA-Aktuator des rechten Trackpads (`high_frequency` Rumble).
    * Natürliches Scroll-Verhalten: Ein kurzes Antippen schaltet exakt 1 Slot weiter. Bleibt die Taste gedrückt, wird nach einer kurzen natürlichen Pause (250 ms) kontinuierlich alle 90 ms im gewählten Takt weitergeschaltet.
    * Konfigurierbar im Mod-Menü (Tab „⚡ Akku & Eco“).

### SteamOS Dual-Cursor Trackpad-Tippen (OSK)
* **Änderung:** Vollwertige Dual-Maus-/Dual-Cursor-Steuerung für die On-Screen-Tastatur ([`VirtualKeyboard.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/gui/VirtualKeyboard.java)).
  * **Begründung:** Wie bei der nativen SteamOS-Bildschirmtastatur soll schnelles, flüssiges Tippen mit beiden Daumen möglich sein, ohne die Hände vom Deck zu nehmen oder das Display zu berühren.
  * **Umsetzung:**
    * Linker Stick / Linkes Trackpad steuert ein türkisfarbenes Fadenkreuz (`#00FFCC`) über die linke Tastaturhälfte.
    * Rechter Stick / Rechtes Trackpad steuert ein bernsteinfarbenes Fadenkreuz (`#FFB300`) über die rechte Tastaturhälfte.
    * Tasten unter den Fadenkreuzen werden visuell hervorgehoben.
    * **Auslöser:** Klick auf das linke Trackpad, LT oder LB tippt sofort die Taste unter dem linken Cursor (inkl. linkem Haptik-Klick). Klick auf das rechte Trackpad, RT oder RB tippt die Taste unter dem rechten Cursor (inkl. rechtem Haptik-Klick).

### Kapazitiver Stick-Touch (L-Stick Touch & R-Stick Touch)
* **Änderung:** Hinzufügen der Knöpfe `LEFT_STICK_TOUCH` und `RIGHT_STICK_TOUCH` zu [`DeckButton.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/DeckButton.java) und Integration in die Hardware-Bridge.
  * **Begründung:** Die Joysticks des Steam Decks besitzen kapazitive Berührungssensoren (Stick Touch). Diese können in Steam Input frei belegt werden (z. B. auf Tasten oder Gamepad-Misc-Buttons) und stehen nun in Minecraft als vollwertige Eingaben zur Verfügung (z. B. für Gyro-Aktivierung, Zoom, Sneak oder freie Tastaturbelegungen).
  * **Konfiguration:** Im Tab „⚙️ Bridge“ kann jeder Stick-Touch einer Steam-Taste zugewiesen oder per Klick direkt angelernt werden.

### UI-Layout-Optimierung & Sub-Tabs im Aktionsauswahl-Modal
* **Änderung:** Behebung der visuellen Überlappungen und unzugänglichen Optionen im Menü ([`SteamDeckConfigScreen.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java)):
  * **Dynamische Zeilenskalierung:** Zeilenhöhe und -abstände passen sich automatisch der Displayhöhe an, sodass alle 12 Buttons auf jeder Seite ohne Überlappung mit den Fußzeilen-Buttons dargestellt werden.
  * **Aktions-Modal mit 2 Sub-Tabs:** Aufteilung in `🎮 Spiel-Aktionen` (17 Einträge) und `🖱️ Maus & Spezial` (Mausklick links, rechts, mitte, Shift-Klick, Bildschirm schließen, Freie Taste). Dadurch sind Mausklicks und Spezialaktionen uneingeschränkt auswählbar.
  * **2-Spalten-Bridge-Dashboard:** L4/R4, L5/R5 und L-Touch/R-Touch sind übersichtlich links und rechts angeordnet und passen perfekt auf den 800p-Bildschirm.

### Dynamische AFK-Drosselung & OLED Screen-Dimming
* **Änderung:** Implementierung des intelligenten AFK-Stromsparmoduls in [`PowerManager.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/power/PowerManager.java), [`FramerateLimitTrackerMixin.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/mixin/FramerateLimitTrackerMixin.java) und [`HudMixin.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/mixin/HudMixin.java).
  * **Begründung:** Wenn der Spieler das Steam Deck beiseitelegt oder kurz AFK geht, soll der Akku geschont werden, ohne das Spiel pausieren zu müssen.
  * **Dynamischer Ablauf:** Nach 2 Minuten Inaktivität sinkt das FPS-Limit stufenlos und vollkommen ruckelfrei um 10 % pro Minute, bis maximal 80 % Drosselung erreicht sind (z. B. 90 → ~18 FPS).
  * **OLED Screen-Dimming:** Nach 3 Minuten Inaktivität dunkelt sich der Bildschirm sanft um bis zu 65 % ab (spart besonders beim OLED-Display massiv Watt ein) und zeigt einen dezenten Hinweis (`💤 AFK Stromsparmodus (-XX%) • Taste drücken`).
  * **Hintergrund-Idle:** [`DynamicChunkThreading.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/power/DynamicChunkThreading.java) drosselt Chunk-Arbeiter während AFK auf 1 Thread, damit CPU-Kerne in tiefere C-States schlafen gehen können.
  * **Partikel & Wetter:** Bleiben auf Wunsch des Spielers völlig unangetastet für ein ungestörtes visuelles Erlebnis.
  * **Sofortiges Erwachen:** Jegliche Eingabe (Stick, Button, Touch, Mauszeiger, Tastatur) stellt die volle Leistung (100 % FPS) und Helligkeit sofort in Bruchteilen einer Millisekunde wieder her.

### Volle Tastenunabhängigkeit, Native Zoom & Trackpad-Klicks
* **Änderung:** Vollständige Entkopplung und Einbindung aller 4 Rücktasten (**L4, R4, L5, R5**), beider **Trackpad-Klicks** (`LEFT_TRACKPAD`, `RIGHT_TRACKPAD`) sowie beider **Stick-Klicks** (`L3`, `R3`) in [`DeckButton.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/DeckButton.java) und [`SteamDeckConfigScreen.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java).
  * **Begründung:** Bisher waren L1/L4 und R1/R4 durch Doppelbelegung gekoppelt. Der Spieler benötigt 100 % freie Kontrolle über jeden einzelnen physischen Knopf des Decks.
* **Änderung:** Neue native 4x-Zoom-Aktion (`DeckAction.ZOOM`) über [`AbstractClientPlayerMixin.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/mixin/AbstractClientPlayerMixin.java).
  * **Begründung:** Bietet auf Knopfdruck (standardmäßig auf L1) stufenlosen, sauberen Handheld-Zoom – ganz ohne zusätzliche Zoom-Mods installieren zu müssen.
* **Standardbelegung auf Traumkonfiguration aktualisiert:**
  * **L1:** `ZOOM` (Zoom / C-Taste)
  * **R1:** `PICK_BLOCK` (Block wählen / Mittelklick)
  * **L4:** `HOTBAR_PREV` (Hotbar links)
  * **R4:** `HOTBAR_NEXT` (Hotbar rechts)
  * **L5:** `SNEAK_TOGGLE` (Schleichen umschalten)
  * **R5:** `JUMP` (Springen)
  * **L3:** `SPRINT` (Sprinten)
  * **R3 / Trackpad-Klick:** `JUMP` (Springen)

### Action-Sets, ModMenu & Visuelles Controller-Dashboard
* **Änderung:** Implementierung der ModMenu-Schnittstelle (`com.terraformersmc.modmenu.api.ModMenuApi`), eines Pause-Menü-Buttons (`[ 🎮 Controller ]`) und eines visuellen Konfigurationsbildschirms ([`SteamDeckConfigScreen`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java)).
  * **Begründung:** Bisher mussten Belegungen und Einstellungen in `steamdeck_essentials.json` editiert werden. Der neue Bildschirm ermöglicht intuitive Tastenbelegung direkt am Handheld-Touchscreen oder mit Controller-Navigation.
* **Änderung:** Einführung von 3 intelligenten, kontextbasierten **Action-Sets** ([`ActionSet.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/input/ActionSet.java)):
  1. `IN_GAME` (Erkunden, Laufen, Bauen)
  2. `MENUS` (Inventar, Kisten, Amboss – mit Mausemulation über R2/A für Linksklick, L2/X für Rechtsklick und Y für Shift-Schnellverschieben)
  3. `AIMING` (Bogen, Armbrust, Dreizack, Fernrohr – mit automatischem Gyro-Zielen)
  * **Begründung:** In Menüs machen Bewegungs-Tasten keinen Sinn; dort werden Klicks und Schnellverschieben benötigt. Beim Spannen von Bogen/Fernrohr soll automatisch das Gyro-Zielen aktiviert werden, während es beim normalen Laufen ruht.

### OSK-Verfeinerung & Quick-Bar-Entfernung
* **Änderung:** Entfernung der Quick-Command-Leiste (`/home`, `/spawn`, `TAB`, `Enter`) über der Bildschirmtastatur ([`VirtualKeyboard.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/gui/VirtualKeyboard.java)).
  * **Begründung:** Die Leiste war unhandlich und verdeckte zu viel Sichtfeld auf dem 16:10-Bildschirm (1280×800).
* **Änderung:** Automatisches Aufklappen des OSK auf [`ChatScreen`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/SteamDeckEssentials.java) beschränkt; Menüs mit Suchleisten (wie das Kreativ-Inventar) öffnen stattdessen einen diskreten `KB`-Button in der Ecke.
  * **Begründung:** Im Kreativ-Inventar will man meistens Items auswählen und nicht sofort tippen; das automatische Aufklappen blockierte die Item-Gitter.

### Gamescope & Fullscreen-Fix
* **Änderung:** Drosselungslogik in [`PowerManager.java`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/power/PowerManager.java) entkoppelt von `isWindowActive()` / Fokusverlust und stattdessen an `isIconified()` gebunden.
  * **Begründung:** Im SteamOS Gaming Mode (Gamescope/Wayland) meldet SDL3 bei „Exclusive Fullscreen“ fälschlicherweise Fokusverlust. Minecrafts `FramerateLimitTracker` und die Mod hatten die Bildrate daraufhin fälschlicherweise auf 10 FPS gedrosselt. Durch Deaktivieren von Fullscreen im Spiel skaliert Gamescope das Bild nativ ohne FPS-Einbruch.

### SDL3-Subsystem-Initialisierung & Keybind-Klicks
* **Änderung:** Expliziter Aufruf von `SDLInit.SDL_InitSubSystem(SDL_INIT_GAMEPAD | SDL_INIT_JOYSTICK | SDL_INIT_SENSOR)` und `SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS`.
  * **Begründung:** Minecraft 26.3 initialisiert über LWJGL/SDL3 ausschließlich `SDL_INIT_VIDEO`. Ohne Initialisierung der Gamepad-Subsysteme meldete SDL3 `SDL_HasGamepad() == false` und ignorierte Controller-Eingaben komplett.
* **Änderung:** Erstellung des [`KeyMappingAccessor`](file:///var/home/lukhicken/AGY/Minecraft/SteamDeck-Essentials/src/main/java/com/steamdeck/mc/mixin/KeyMappingAccessor.java) für atomare `clickCount++`-Registrierung.
  * **Begründung:** Viele Vanilla-Aktionen (Schlagen, Springen, Block platzieren, Inventar) prüfen `KeyMapping.consumeClick()`. Ein bloßes `setDown(true)` ohne Klickzähler-Inkrementierung führte dazu, dass Aktionen ignoriert wurden.

### Migration auf SDL3
* **Änderung:** Vollständiger Austausch von GLFW-Klassen gegen LWJGL SDL3 (`org.lwjgl.sdl.*`).
  * **Begründung:** Minecraft 26.3 hat GLFW zugunsten der modernen SDL3-Plattformbibliothek abgelöst; alte GLFW-Aufrufe führten zu `NoClassDefFoundError`.

---

## 🔍 Debugging-Maßnahmen & Diagnose-Werkzeuge

Um Probleme mit Controllern, Sensoren oder Performance in Sekundenschnelle aufzuspüren, sind folgende Werkzeuge in die Mod integriert:

### 1. Echtzeit-Telemetrie im F3-Bildschirm
Wenn im Spiel **F3** gedrückt wird (oder über Controller D-Pad Links bzw. X je nach Belegung), erscheint unten rechts ein dedizierter SteamDeck-Diagnose-Kasten:
* **Batteriestatus:** Prozent, Watt-Verbrauch, Ladezustand, verbleibende Laufzeit.
* **Controller-Status:** Erkannter SDL3-Gerätename (z. B. `Microsoft X-Box 360 pad` oder `Steam Virtual Gamepad`), Handle-ID und aktives Action-Set (`IN_GAME`, `MENUS`, `AIMING`).
* **Sensor-Status:** Aktiver Gyro-Provider (z. B. `Linux IIO`, `SDL Native Sensor` oder `Kein Sensor`).

### 2. Strukturierte Log-Präfixe in `latest.log`
Alle Ausgaben der Mod sind im Minecraft-Log kategorisiert:
* `[SteamDeck Essentials] [INPUT]`: Gamepad-/Joystick-Verbindungsstatus, gefundene IDs, Fallbacks.
* `[SteamDeck Essentials] [GYRO]`: Initialisierung und Sensordaten-Status.
* `[SteamDeck Essentials] [POWER]`: Akku-Status, Drosselungsentscheidungen, Suspend/Resume-Events.

### 3. Shell-Befehle zur Überprüfung auf dem Steam Deck (Desktop-Modus)
Falls ein Controller oder Sensor nicht reagiert:
```bash
# 1. Prüfen, ob Steam den virtuellen Controller erzeugt hat
cat /proc/bus/input/devices | grep -i -E "steam|xbox|gamepad"

# 2. Prüfen, ob Linux IIO Gyro-Sensoren vorhanden sind
ls -la /sys/bus/iio/devices/

# 3. Batterie-Sensoren direkt im Sysfs auslesen
cat /sys/class/power_supply/BAT0/status
cat /sys/class/power_supply/BAT0/capacity
```

---

## [1.0.0+mc26.3] - 2026-10-30

### Hinzugefügt: Semantische Aktionen & Beliebige Tasten mit Spitznamen
* **Begründung:** Bisherige Controller-Mods binden Tasten starr an interne Keybinds von Minecraft. Spieler möchten jedoch universelle Aktionen (z. B. "Springen", "Sneak umschalten", "Hotbar scrollen") unabhängig von Tastatur-Belegungen nutzen, sowie beliebige Tasten von anderen Mods (z. B. `M` für Minimap, `V` für VoiceChat, `P` für Mod-Menü) auf beliebige Controller-Buttons legen.
* **Neues System `ActionBinding`:** Unterstützt sowohl native semantische Aktionen als auch freie Tastatur-Tasten (`KEY:<code >:<name>`).
* **Interaktives Custom-Key Modal:**
  * Im Controller-Menü kann bei jedem Button "Tastatur-Taste (mit Name)" gewählt werden.
  * Auswahl durch direktes Drücken der Taste ("Taste drücken...") oder per Klick auf praktische Presets (Minimap `M`, Wegpunkte `B`, VoiceChat `V`, Rucksack `B`, Zoom `C`, Quests `L`, Mod-Menü `P`, Chat `T`).

### Hinzugefügt: Steam Input Hardware-Bridge (L4, R4, L5, R5)
* **Begründung:** Der Standard-Xbox-Treiber von SteamOS überträgt die vier rückseitigen Paddles (L4/R4/L5/R5) nicht nativ als Xbox-Paddles an das Spiel. In Steam Input werden sie daher üblicherweise auf Tastaturtasten gelegt (z. B. `[` für L4, `]` für R4, `\` für L5, `/` für R5). Ohne eine Bridge in Minecraft führen diese Tasten entweder ins Leere oder tippen Zeichen in Textfelder.
* **`KeyboardHandlerMixin` & Bridge-System:**
  * Fängt Tastatureingaben für die konfigurierten Bridge-Tasten am Fenstereingang ab und leitet sie direkt als `DeckButton.L4`, `DeckButton.R4`, `DeckButton.L5` und `DeckButton.R5` an das aktive Action-Set weiter.
  * Verhindert das Tippen der Bridge-Zeichen in den Chat oder in Menü-Textfelder (`ci.cancel()`).
  * Im Tab "⚙️ Bridge" kann jede Rücktaste visuell per Tastendruck neu zugeordnet werden.

### Hinzugefügt: Performance Tracking & Akku-Telemetrie-Logger
* **Begründung:** Um die Energieeffizienz auf dem Steam Deck wissenschaftlich fundiert zu optimieren, muss erfasst werden, in welchen Spielzuständen (Chunk-Generierung beim Erkunden, Verweilen in der Basis, Menüs, Kämpfen oder AFK) wie viel Leistung (Watt, FPS, RAM) verbraucht wird.
* **Automatische Kategorisierung in 5 Spielzustände:**
  1. `Moving/Exploring` (Sprinten, Fliegen, schnelles Fortbewegen mit Chunkloading)
  2. `Stationary/Base` (Stationäres Bauen/Stehen in geladenen Chunks)
  3. `In Menu` (Inventar, Pausemenü, Kisten, Öfen)
  4. `Combat/Aiming` (Item-Nutzung, Angriffe, Bogenschießen)
  5. `AFK Throttled` (Aktive stetige FPS- & CPU-Drosselung)
* **Log-Datei:** Schreibt alle 5 Sekunden Metriken nach `.minecraft/steamdeck_perf_log.csv`:
  `timestamp, category, fps, target_fps, battery_percent, power_watts, voltage_volts, battery_status, loaded_chunks, ram_used_mb`.
* Jederzeit an- und ausschaltbar über das Mod-Menü (Tab "⚡ Akku & Eco").

### Verbessert: Fünf-Tab-Architektur im Controller-Dashboard
* **Begründung:** Durch die vielen neuen Einstellungsmöglichkeiten (Bridge, Akku-Drosselung, Performance-Tracker, Action-Sets) wurde die bisherige Hardware-Seite zu voll für das 16:10 Display des Steam Decks.
* **Neue Struktur:**
  * `🎮 Spiel`: Vollständige Tastenbelegung für das normale Gameplay.
  * `📦 Menüs`: Belegungen für Inventar, Kisten und GUIs.
  * `🎯 Zielen`: Belegungen für Fernkampf und Bogen/Armbrust.
  * `⚡ Akku & Eco`: AFK-Drosselung, OLED-Dimming, Chunk-Threading, Performance-Logger.
  * `⚙️ Bridge`: Gyro, 8-Wege Anticheat-Stick, Deadzone, Trackpad-Sensitivität, Rücktasten-Bridge.

### Behoben: Layout-Überlappungen & Vollständige Mausklick-Aktionsauswahl
* **Begründung:** 
  1. Auf den hochskalierten Handheld-Displays (GUI Scale 3/4) überlappten die langen Button-Beschreibungen (z. B. *"Rücktaste L4 (Oben links)"*) mit den Aktions-Buttons und dem Status-Text.
  2. Im Aktions-Auswahlfenster waren Mausklicks und benutzerdefinierte Tasten abgeschnitten, da die Liste am unteren Fensterrand stoppte. Zudem funktionierten Menü-Mausklicks im regulären Spielmodus nicht.
* **Änderungen:**
  * **Kompakte Button-Badges:** Kürzere, standardisierte Namen (z. B. `L1 (Bumper)`, `L4 (Paddle)`, `Trackpad L`).
  * **Feste Spaltengeometrie & Text-Clipping:** Button-Name und Aktions-Pill sind strikt getrennt; überlange Spitznamen werden mit `plainSubstrByWidth` und `…` automatisch begrenzt.
  * **Status-Text verlegt:** Der aktive Controller-Status steht nun oben rechts im Header und kollidiert nicht mehr mit den Tastenreihen.
  * **Universelle Mausklicks:** `Mausklick Links`, `Mausklick Rechts` und `Mausklick Mitte` können jetzt auf **jeden Button in jedem Action-Set** gelegt werden. Im Menü steuern sie die GUI; im Spiel ohne Menü führen sie Angriff, Platzieren und Block-Wählen aus.
  * **Kategorisiertes Auswahl-Modal:** Das Aktionsfenster ist in zwei Reiter unterteilt:
    * `🎮 Spiel-Aktionen` (17 Gameplay-Aktionen)
    * `🖱️ Maus & Spezial` (Mausklicks Links/Rechts/Mitte, Schnell-Transfer, Menü schließen, Eigene Taste, Keine Aktion)
  * Nichts wird mehr abgeschnitten – alle Optionen sind direkt und barrierefrei erreichbar.

### Hinzugefügt & Verbessert: Hotbar Auto-Repeat, AFK-Overlay-Bereinigung & Hauptmenü-Erkennung
* **Hotbar Auto-Repeat:** Beim Gedrückthalten einer Hotbar-Taste (L4, R4, LB, RB, etc.) wechselt die Auswahl sofort um 1 Slot. Nach einer natürlichen Pause (380 ms) läuft der Selektor kontinuierlich mit 140 ms pro Slot durch, bis die Taste losgelassen wird.
* **AFK Text-Anzeige entfernt:** Der Text `"💤 AFK Stromsparmodus (-X%) • Taste drücken zum Aufwecken"` wurde vom Bildschirm entfernt. Das stufenlose, akkuschonende OLED-Screen-Dimming bleibt vollständig aktiv, verdeckt aber keine HUD-Elemente mehr.
* **Hauptmenü als Action-Set `MENUS`:** Minecraft TitleScreen, Weltenauswahl, Serverliste und Ladebildschirme werden nun absolut verlässlich als `ActionSet.MENUS` eingestuft (`client.level == null || client.player == null`).
