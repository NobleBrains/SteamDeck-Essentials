# SteamDeck Essentials – Changelog & Debugging Guide

Dieses Dokument führt alle technischen Änderungen mit ausführlicher Begründung auf und dient Entwicklern und Testern als Leitfaden zur Diagnose und Fehlerbehebung.

---

## 📋 Inhaltsverzeichnis
1. [Version 1.1.0 – Handheld/Desktop-Modus, Steam Controller 2026 & Bugfixes](#version-110)
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

## 🚀 Version 1.4.2

Bugfixes:
* **Keine hängenden Tasten mehr beim Trennen des Controllers.** Wenn der Controller beim Trennen gerade eine Taste hielt, wurde sie nie losgelassen. Das galt auch beim automatischen Wechsel auf einen bevorzugten Controller. Beispiel: Der Steam Controller 2026 verliert per BLE kurz die Verbindung, während RT gedrückt ist. Dann baute die Figur endlos weiter ab, und Springen, Sprinten oder eigene Tasten blieben genauso hängen. Jetzt werden beim Trennen bzw. Wechsel alle gehaltenen Tasten sauber losgelassen. Hold-Repeat, Hotbar-Scroll, Zoom, Shift im Inventar und Trackpad-Zustände werden dabei zurückgesetzt.
* **Low-Battery-Saver erhöht die FPS nicht mehr.** Unter 20 % bzw. 10 % Akku galt bisher fest 60 bzw. 45 FPS, auch wenn der Spieler ein niedrigeres Limit (z. B. 30 oder 40 FPS) eingestellt hatte. Jetzt gilt das niedrigere der beiden Limits. Der Hinweis-Toast zeigt den tatsächlichen Wert.
* **Der Steam-Layout-Installer überschreibt keine eigenen Layouts mehr automatisch.** Beim Spielstart wird nur noch die Vorlage in `controller_base/templates` installiert. Das aktive Layout für Prism Launcher / Minecraft (`Steam Controller Configs/<user>/config/<app>/controller_neptune.vdf`) wird nur noch über den Button im Tab `⚙️ Bridge` gesetzt. Fremde Layouts werden dabei jedes Mal gesichert, frühere Sicherungen werden nicht überschrieben (Zeitstempel im Namen). Ältere Versionen des eigenen Layouts werden ohne Sicherung ersetzt.

---

## 🚀 Version 1.4.1

Standardwerte und Verhalten nach den Steam-Input-Fakten aus [`docs/FINDINGS.md`](docs/FINDINGS.md):
* **Klick über Druck als Standard, 12,5 %** (Steam-Vorgabe für `Soft_Press`, 4096/32768, liegt am Klickpunkt der Firmware). Grund: Das Klick-Bit der Firmware verpasst ca. die Hälfte der Drücke, wenn der Daumen auf dem Pad liegt. Bestehende Configs werden einmalig umgestellt (`configVersion` 2). Alte Standard-Schwellen von 45 % bzw. 30 % werden zu 12,5 %. Unterhalb von 20 % lässt sich die Schwelle in 0,5-%-Schritten einstellen.
* **Haptik-Stärke wie Steam:** Gain-Byte = `dB − 12 + 3·Intensität`. Klick beim Drücken: Click, Intensität 3. Loslassen: Tick, 2, −3 dB. Tastenwechsel und Radial-Menü: Tick, 1, 0 dB. Hotbar: Tick, 2. Die Stufen pro Pad verschieben das weiter um ±6 dB.
* **Stick-Deadzone „Auto“** wie Steam pro Controller: Steam Controller 2026 12,5 %, Steam Deck 25 %. Wer +/- drückt, schaltet auf einen manuellen Wert um.
* **Bildschirmtastatur wie SteamOS:** Im Vollbild-Layout deckt jedes Pad 55 % der Breite ab (10 % Überlappung in der Mitte). Die Abbildung ist linear ohne Glättung und Rand (`oskTrackpadScale` 1–2 wie „TrackPadTypingInputScale“). Die Taste bleibt aktiv, solange der Punkt in ihr liegt. Der Zeiger verschwindet erst 100 ms nach dem Abheben.
* **Akku des Controllers** (z. B. Steam Controller 2026 per Funk) im Pausemenü, oben links. Die Werte kommen von SDL (Report `0x43`).
* Workshop-Preset: beide Pads klicken über Druck mit 12,5 %, Deadzone auf Auto.

---

## 🚀 Version 1.4.0

### Trackpad-Filter nach dem Vorbild von Steamify / mc-deck-native-controls
* Ausgewertet wurden **Steamify 1.3.0** (Fabric, MC 26.3), **mc-deck-native-controls** (Forge) und **SteamlessController**. Steamify und mc-deck-native-controls nutzen beide die Filter von Jibb Smart (gyrowiki). Die Mod setzt das jetzt auf der Finger-Geschwindigkeit um (Pad-Breiten/s):
  * **Einschwingzeit:** Die ersten 40 ms nach dem Aufsetzen werden ignoriert.
  * **Gestufte Glättung:** Über 0,6/s wird nicht geglättet, unter 0,15/s voll mit einem Exponentialfilter (τ = 50 ms), dazwischen fließend.
  * **Tightening:** Unter 0,05/s wird die Bewegung proportional verkleinert, eine weiche Totzone ohne harte Kante.
  * **Neu:** Zu Beginn eines Klicks friert die Bewegung 60 ms ein, damit sich der Zeiger beim Drücken nicht vom Ziel verschiebt.
* Ersetzt den eigenen Filter aus 1.3.1 mit harter Totzone.

### Bildschirmtastatur mit beiden Trackpads (wie in SteamOS)
* Linkes Pad = linke Tastaturhälfte, rechtes Pad = rechte Hälfte, mit **absoluter Zuordnung**: Wo der Finger liegt, ist das Fadenkreuz. Das funktioniert auch im Vollbild-Layout (jedes Pad deckt eine Hälfte ab).
* Tastenwahl mit Hysterese (kein Flackern auf Tastengrenzen), in Lücken wird die nächstliegende Taste gewählt, Haptik-Tick beim Tastenwechsel.
* Getippt wird per **Pad-Klick** (Hardware oder Druckschwelle) oder wie bisher mit LT/RT bzw. LB/RB. Die Sticks bewegen die Fadenkreuze weiterhin, als Fallback für Controller ohne Trackpads.
* Solange die Tastatur offen ist, bewegt das rechte Pad nicht den Mauszeiger, und „linkes Pad als D-Pad“ ist in Menüs aus.

### Klickdruck
* Die Live-Anzeige zeigt jetzt den Zahlenwert. Die Schwelle lässt sich unter 10 % in 1-%-Schritten einstellen. Laut SteamlessController-Messungen liegt ein ruhender Daumen bei ca. 1 % der Rohskala und ein Druck ab ca. 5 %.

---

## 🚀 Version 1.3.1

* **Mauszeiger mit Display-Skalierung (z. B. 140 %):** Minecraft 26.3 führt die Mausposition in SDL-Fensterpunkten (`Window.getScreenWidth`). Die Mod rechnete dagegen mit Framebuffer-Pixeln (`getWidth`). Folgen: Der Trackpad-Zeiger lief unsichtbar über den Rand hinaus und blieb dort „hängen“. Slot-Springen im Inventar und die Controller-Navigation im Config-Screen landeten an falschen Stellen. Jetzt wird überall in Fensterpunkten gerechnet.
* **Kein Zittern mehr bei ruhendem Finger:** adaptive Glättung (stark bei winzigen, langsamen Bewegungen, praktisch keine bei schnellen Wischern) und eine kleine Totzone gegen Sensorrauschen. Gilt für Trackpad-Zeiger und Trackpad-Kamera.
* **Mausbeschleunigung** für den Trackpad-Zeiger wie im Maus-Modus von Steam: langsam heißt präzise, ein schneller Wisch kommt bis zu ca. 2 Bildschirmbreiten weit.
* **Steam-Input-Bridge** wird automatisch abgeschaltet, sobald ein nativer Valve-Controller aktiv ist, denn die Rücktasten kommen dann direkt. Im Bridge-Tab steht der Status.

---

## 🚀 Version 1.3.0

### Preset „SteamDeck Minecraft Controls“ (Workshop 3624927898), 1:1 aus Steam Input
* [`input/ControllerPresets.java`](src/main/java/com/steamdeck/mc/input/ControllerPresets.java) überträgt [`docs/steamdeck minecraft controls_0.vdf`](docs/) Gruppe für Gruppe. Laden kannst du es über den Button **📋 Workshop-Layout laden** in den Tabs Spiel/Menüs/Zielen (mit Bestätigungsklick).
* Dafür neu in der Mod (Steam-Input-Funktionen):
  * **Activator-Settings pro Belegung:** Halten zum Wiederholen (`hold_repeats`, Verzögerung/Rate) und Haptik-Tick (`haptic_intensity`), gespeichert als `AKTION|repeat=200,50|haptic`.
  * **Aktionen Mausrad hoch/runter:** In Menüs wird gescrollt, im Spiel wechselt der Hotbar-Slot wie mit dem Mausrad.
  * **Linkes Trackpad als D-Pad zum Laufen** (`leftTrackpadMode: DPAD_MOVE`, mit Klick).
  * **Vorwärts = Sprinten** (`sprintOnForward`, entspricht „Strg + W“ auf Nord).
  * **Klick-Erkennung pro Pad** (`left/rightTrackpadClickMode`), z. B. rechts mit Soft Press und 30 %.
  * **Gyro-Aktivierung** (`gyroActivation`): Nur beim Zielen / Immer / Trackpad berührt / R-Stick berührt. Im Bridge-Tab durchschaltbar.
  * **Gehaltenes Schleichen in Menüs** macht aus Mausklicks Shift-Klicks.

---

## 🚀 Version 1.2.0

### Trackpads: Klickdruck & Haptik-Stärke (Steam-Input-Parität)
* **Neuer Tab `🖐 Pads`** im Controller-Dashboard (die Tabs heißen jetzt `⚡ Eco` und `🖐 Pads`, damit sechs Tabs nebeneinander passen).
* **Klick-Erkennung:** `Hardware-Klick` (Standard) oder `Druckschwelle`. Bei der Druckschwelle gilt das Pad als gedrückt, sobald der gemessene Druck den Schwellwert erreicht. Den Schwellwert stellst du pro Pad in 5-%-Schritten ein. Losgelassen wird erst unter 80 % der Schwelle, damit der Klick an der Grenze nicht flackert. Ein voller Hardware-Klick zählt immer.
* **Live-Druckanzeige** pro Pad mit roter Markierung für die Schwelle. Der Balken wird grün, sobald ein Klick ausgelöst ist.
* **Haptik-Stärke pro Pad:** Aus / Sehr schwach / Schwach / Normal / Stark / Sehr stark (±6 dB pro Stufe). Beim Verstellen gibt es sofort einen Probe-Impuls.
* Am Controller: Auf Wert-Zeilen ändert D-Pad links/rechts den Wert direkt.
* Config: `trackpadClickMode`, `left/rightTrackpadClickPressure`, `left/rightTrackpadHapticLevel`.

---

## 🚀 Version 1.1.3

* **Echte Trackpad-Haptik beim Steam Controller (2026):** Neue Klasse [`power/TritonTrackpadHaptics.java`](src/main/java/com/steamdeck/mc/power/TritonTrackpadHaptics.java). Sie steuert die Aktuatoren *in* den Trackpads direkt über den HID-Output-Report `0x82` `[0x82, Seite 0 = L / 1 = R, Befehl 1 = Tick / 2 = Klick, Gain dB]` (Protokoll laut SteamlessController, MIT). Geschrieben wird auf den hidraw-Knoten, den SDL meldet, als Fallback wird er per sysfs gesucht. Über SDL-Rumble erreicht man nur die Griffmotoren.
* Pad-Klick beim Drücken, leiser Tick beim Loslassen. Hotbar-Wechsel, Menü-Navigation und Radial-Menü nutzen jetzt ebenfalls die Pad-Aktuatoren. Bogen-Spannung und Haltbarkeitswarnung bleiben auf den Griffmotoren.
* Bei anderen Controllern bleibt es beim bisherigen Rumble.

---

## 🚀 Version 1.1.2

* **Trackpad-Klick-Haptik:** Ein kurzer, kräftiger Impuls beim Drücken und ein leichterer beim Loslassen, jeweils auf der Seite des gedrückten Pads. Das gilt nur für native Valve-Controller, denn mit Steam Input erzeugt Steam diese Haptik selbst. Abschaltbar über `enableTrackpadClickHaptics`.

---

## 🚀 Version 1.1.1

### Steam Controller (2026) & Steam Deck: korrekte SDL-Tastenbelegung
* Belegung laut SDL 3.4 (libsdl-org/SDL #15601 / #15612): `TOUCHPAD` = Klick linkes Pad, `MISC2` = Klick rechtes Pad, `MISC3` = Touch linker Stick, `MISC4` = Touch rechter Stick, `MISC1` = QAM, `MISC5/6` = Grip-Sensoren.
* Vorher war „Trackpad R“ auf `MISC3` gelegt (also auf den Touch des linken Sticks). Dadurch löste das Berühren des linken Sticks einen Sprung aus, während der Klick aufs rechte Pad gar nichts tat. Die Stick-Touch-Belegung lag auf QAM bzw. auf dem Klick rechts.
* **Neu:** Bei nativen Valve-Controllern steuert das rechte Trackpad in Menüs und Inventaren den Mauszeiger, ein Klick aufs rechte Pad ist Linksklick (Action-Set `MENUS`).
* Die Diagnose im Log listet jetzt alle Zusatztasten mit ihrer Bedeutung auf.

---

## 🚀 Version 1.1.0

### Handheld- & Desktop-Modus
* **Neu:** [`platform/PlatformDetector.java`](src/main/java/com/steamdeck/mc/platform/PlatformDetector.java) und [`platform/DeviceMode.java`](src/main/java/com/steamdeck/mc/platform/DeviceMode.java). Erkennung über DMI (`Jupiter`/`Galileo` u. a.), manuell überschreibbar (`deviceMode`, Config-Screen Tab `⚡ Akku & Eco`).
* Im **Desktop-Modus** sind abgeschaltet: alle FPS-Caps, AFK-Drosselung & Dimming, Low-Battery-Saver, Chunk-Thread-Limit, Akku-HUD, CSV-Tracker, Suspend-Hook, Chat-oben-Layout & invertiertes Chat-Scrollen, OSK-Auto-Öffnen, Abfangen der Bridge-Tasten und die automatische Steam-Layout-Installation.
* Thermal-Cap richtet sich nach dem Panel: 90 Hz (OLED) bzw. 60 Hz (LCD) statt immer 90.

### Steam Controller (2026) ohne Steam
* SDL-HIDAPI-Treiber für Steam Controller wird aktiviert, native Valve-Controller werden bevorzugt (auch bei späterem Einstecken), ausführliche Controller-Diagnose im Log.
* Trackpad-Kamera für native Valve-Controller (Steam Input nicht aktiv).
* Rechter-Stick-Kamera & Präzisionszielen laufen jetzt pro Frame mit echter Geschwindigkeit (°/s). Vorher: fester Schritt im 20-Hz-Tick, max. ~10°/s.

### Bugfixes
* **Suspend-Save im falschen Thread:** `saveAllChunks` lief auf dem Render-Thread (nicht thread-sicher, Risiko für Weltschäden). Jetzt `server.execute(saveEverything)`. Der Watchdog vergleicht Wand- und Monotonuhr, damit GC-Pausen oder Hänger keinen falschen „Suspend“ mehr auslösen. Der `busctl`-Prozess wird beim Stoppen beendet.
* **DSU-Gyro funktionierte nie:** Die Abo-Anfrage hatte Server-Magic, keinen Nachrichtentyp und keine CRC32, und die Gyro-Werte wurden von Offset 56/60 gelesen (Touch-Daten). Jetzt korrektes Protokoll, Gyro bei 88/92/96 und Integration über die Motion-Timestamps.
* **Gyro-Sprung beim Zielen:** Bewegung, die sich angesammelt hatte, während man nicht zielte, wurde beim Spannen des Bogens auf einmal angewendet. Jetzt werden alle Provider jeden Frame geleert.
* **Gyro hing von der Framerate ab:** Bei 90 FPS drehte es ~1,5× so schnell wie bei 60 FPS. Jetzt Zeit-Integration (Sensitivität 1,0 = 1:1 echte Drehung). Außerdem wurde der falsche Sensortyp gelesen, wenn nur `GYRO_L/R` verfügbar war.
* **`DynamicChunkThreading.shutdown()`** schaltete Minecrafts globalen `Util.backgroundExecutor()` mit ab.
* **Speicherleck:** `SDL_GetGamepads/Joysticks/Sensors` wurden nie freigegeben (alle 1,5 s, solange kein Controller verbunden war).
* **`/`-Taste am PC blockiert:** Die Bridge fing `[ ] \ /` immer ab, sodass `/` den Befehls-Chat nicht mehr öffnete.
* **ModMenu-Stubs im JAR:** Die nachgebauten `com.terraformersmc.modmenu.api`-Klassen wurden mit ausgeliefert und konnten das echte ModMenu überschatten. Sie liegen jetzt in einem eigenen `stubs`-sourceSet, das nur zum Kompilieren dient.
* **Build-Classpath:** Statt aller Prism-Libraries (1.21.11, 26.2, 26.4, drei LWJGL-Versionen) wird nur noch exakt das verwendet, was laut Prism-Metadaten zu MC 26.3 gehört.
* **Steam-Layout-Installer** überschrieb vorhandene eigene Layouts ohne Backup. Jetzt wird einmalig `*.bak-steamdeck-essentials` angelegt, und unveränderte Dateien werden nicht neu geschrieben (auch in `install-steam-layout.sh`).
* **CSV-Tracker:** Zahlen werden mit `Locale.ROOT` formatiert (eine deutsche Locale schrieb `8,86` und zerlegte damit die Spalten).
* Controller-Navigation im Config-Screen traf die Tab-Leiste nicht genau (andere Geometrie als beim Zeichnen).
* Config: Werte werden beim Laden begrenzt, neue Optionen werden in bestehende Configs geschrieben.

---

## 🚀 Version 1.0.0

### Action-Set Menü-Linksklick-Fix (`MOUSE_LEFT_CLICK`), Inventar-Slot-Snapping & 1-Klick Steam-Input-Installer
* **Änderung:** Behebung des Menü-Klick-Bugs in [`SteamDeckInputManager.java`](src/main/java/com/steamdeck/mc/input/SteamDeckInputManager.java) und [`SteamDeckConfigScreen.java`](src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java), Korrektur aller SDL3-Scancodes in [`ModConfig.java`](src/main/java/com/steamdeck/mc/config/ModConfig.java), Hinzufügen von [`SteamLayoutInstaller.java`](src/main/java/com/steamdeck/mc/input/SteamLayoutInstaller.java) und [`AbstractContainerScreenAccessor.java`](src/main/java/com/steamdeck/mc/mixin/AbstractContainerScreenAccessor.java).
  * **Ursache & Lösung des `MOUSE_LEFT_CLICK`-Bugs in Menüs:**
    1. In Minecraft 26.3 prüft `AbstractWidget.isMouseOver()` nur den gecachten Render-Wert `isHovered`. Wenn per Stick/D-Pad zu einem Button navigiert wurde (`screen.getFocused() != null`), klickte `MOUSE_LEFT_CLICK` bisher an die alte Mauszeiger-Position ins Leere. Nun löst `MOUSE_LEFT_CLICK` auf jedem beliebigen Button zuverlässig das fokussierte Menü-Element aus, sofern der Mauszeiger nicht aktiv über einem anderen Widget schwebt.
    2. In `SteamDeckConfigScreen.warpCursorToGui()` wird neben `SDL_WarpMouseInWindow` nun auch direkt `client.mouseHandler.onMove(...)` aufgerufen, damit die Mauskoordinaten unter Gamescope/Wayland ohne Frame-Verzögerung synchron sind.
    3. `DeckButton.RIGHT_TRACKPAD` wird nun im SDL3-Gamepad-Polling abgefragt und `MOUSE_QUICK_MOVE` übergibt die korrekte SDL3-Konstante `InputConstants.MOD_SHIFT` (`3`).
  * **Korrektur der SDL3-Scancodes (Bridge & Presets):**
    * `KeyEvent.key()` liefert in Minecraft 26.3 SDL3-Scancodes (USB HID) statt alter GLFW/ASCII-Codes. Die Standard-Bridge-Tasten wurden auf `47` (`[`), `48` (`]`), `49` (`\`), `56` (`/`) sowie `104` (`F13`) und `105` (`F14`) aktualisiert (inkl. automatischer Migration bestehender Configs) und die 8 Schnell-Presets (`M=16`, `B=5`, `V=25`, `C=6`, `L=15`, `P=19`, `T=23`) angepasst.
  * **1-Klick Steam-Input-Installer & aktualisiertes VDF-Template:**
    * [`controller_neptune_steamdeck_essentials.vdf`](src/main/resources/assets/steamdeck_essentials/controller_neptune_steamdeck_essentials.vdf) belegt alle 4 Rücktasten (`L4`, `R4`, `L5`, `R5`) direkt mit den passenden Bridge-Tasten und das rechte Trackpad mit Maus + Linksklick.
    * `SteamLayoutInstaller` kopiert die Vorlage beim Start sowie per Klick auf den neuen Button im Tab `⚙️ Bridge` direkt in die Steam-Ordner.
  * **Inventar Slot-Snapping & Controller-Button-Legende:**
    * In Kisten, Öfen, Werkbänken und Inventaren (`AbstractContainerScreen`) springen D-Pad und linker Stick direkt zum nächsten aktiven Slot in Pfeilrichtung. Unten links zeigt [`BatteryHudRenderer.java`](src/main/java/com/steamdeck/mc/gui/BatteryHudRenderer.java) eine dynamische Button-Legende; im Pause-Menü sitzt die Batterie-Pill nun oben links ohne Überlappung mit dem `🎮 Controller`-Button.
  * **60/90-Hz Gyro & OSK Dual-Cursor, `TAB`-Taste, Umlaute, Gameplay-Haptik & Low-Battery-Saver:**
    * Gyro-Updates und OSK-Fadenkreuze laufen nun pro Render-Frame (60/90 Hz statt 20 Hz Client-Tick), das OSK enthält wieder eine `TAB`-Taste sowie `ä`, `ö`, `ü`, `ß` (im `?123`-Modus), und bei ≤ 20 % / ≤ 10 % Akku schaltet sich automatisch der Low-Battery Eco-Saver ein.

### SDL3-Mausklick-Fix in Menüs & Vollständige Menü-/Gamemode-Navigation per D-Pad / Linkem Stick + `A`
* **Änderung:** Korrektur der Maustasten-IDs in [`SteamDeckInputManager.java`](src/main/java/com/steamdeck/mc/input/SteamDeckInputManager.java) sowie Implementierung der Controller-Menünavigation in [`SteamDeckInputManager.java`](src/main/java/com/steamdeck/mc/input/SteamDeckInputManager.java), [`SteamDeckConfigScreen.java`](src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java) und [`GameModeSwitcherScreenAccessor.java`](src/main/java/com/steamdeck/mc/mixin/GameModeSwitcherScreenAccessor.java).
  * **Ursache & Diagnose des Menü-Klick-Bugs (`R2` / `A` / `X` / `L2`):**
    * Durch Bytecode-Inspektion (`javap -c`) von `net.minecraft.client.MouseHandler.onButton`, `AbstractWidget.isValidClickButton` und `AbstractContainerScreen.getContainerClickButton` in Minecraft 26.3 zeigte sich: Mit dem Wechsel von GLFW auf **SDL3** sind Maustasten nicht mehr 0-basiert (`0 = Links`, `1 = Rechts`), sondern nutzen die **1-basierten SDL3-Konstanten** (`SDL_BUTTON_LEFT = 1`, `SDL_BUTTON_MIDDLE = 2`, `SDL_BUTTON_RIGHT = 3`).
    * Die Übergabe von `0` in `new MouseButtonInfo(0, 0)` wurde von SDL3 als *„keine/ungültige Taste"* verworfen. Mit `SDLMouse.SDL_BUTTON_LEFT (1)`, `SDLMouse.SDL_BUTTON_RIGHT (3)` und `SDLMouse.SDL_BUTTON_MIDDLE (2)` funktionieren Linksklick (`R2`/`A`), Rechtsklick (`L2`/`X`) und Schnell-Transfer (`Y`, `SDL_BUTTON_LEFT` + Shift-Modifier `1`) nun in allen Inventaren und Menüs sofort.
  * **Gamemode-Umschalter (`F3+F4`) per D-Pad / Linkem Stick + `A`:**
    * Im `GameModeSwitcherScreen` kann mit dem **D-Pad** oder dem **linken Joystick** (inkl. Auto-Repeat beim Halten und Haptik-Tick) zwischen Kreativ, Überleben, Abenteuer und Zuschauer gewechselt werden. Ein Druck auf **`A`** bestätigt die Auswahl (`switchToHoveredGameMode`) und schließt das Fenster.
  * **Menünavigation (Pausemenü, Optionen & Untermenüs) per D-Pad / Linkem Stick + `A`:**
    * In allen Menüs (Pausemenü, Optionen, Untermenüs sowie im `SteamDeckConfigScreen`) bewegt der **linke Joystick** oder das **D-Pad** den Fokus zwischen Buttons, Slidern und Listen (mit Auto-Repeat nach 340 ms). Ein Druck auf **`A`** bestätigt den fokussierten Menüpunkt, während `R2` (oder `A` bei aktiver Mausbewegung) weiterhin direkt an der Mauszeiger-Position klickt.

### 3-Ebenen Radial-Menü auf dem rechten Joystick (Umschalten per R3)
* **Änderung:** Implementierung von [`RadialMenuOverlay.java`](src/main/java/com/steamdeck/mc/gui/RadialMenuOverlay.java), [`KeyboardHandlerAccessor.java`](src/main/java/com/steamdeck/mc/mixin/KeyboardHandlerAccessor.java) und Integration in [`SteamDeckInputManager.java`](src/main/java/com/steamdeck/mc/input/SteamDeckInputManager.java) sowie [`HudMixin.java`](src/main/java/com/steamdeck/mc/mixin/HudMixin.java).
  * **Begründung:** Da die Kamera im normalen Gameplay über das rechte Trackpad gesteuert wird, ist der rechte Joystick frei für ein schnelles, mehrschichtiges Auswahlrad.
  * **Funktionsweise:**
    * **Auslenken des rechten Sticks:** Öffnet das Radial-Menü mit Live-Hervorhebung und taktilem Haptik-Tick bei jedem Segmentwechsel. Beim Loslassen des Sticks wird die gewählte Funktion ausgeführt.
    * **R3 (Rechter Stick-Klick):** Schaltet nahtlos zwischen den **3 Radial-Menü-Ebenen** um (sowohl bei geöffnetem als auch bei geschlossenem Rad, inkl. kurzem HUD-Toast):
      1. **`[1/3] 🎒 Hotbar 1–9`:** 9 Segmente im Kreis mit echten 3D-Item-Icons, Stack-Größen, Haltbarkeitsbalken und verzögerungsfreier Live-Auswahl von Slot 1 bis 9.
      2. **`[2/3] 🖥️ F-Tasten (F1–F8)`:** `F1` (HUD ein/aus), `F2` (Screenshot), `F3` (Debug-HUD), `F3+2` (FPS/TPS-Graph), `F5` (Perspektive), `F8` (Kino-Kamera), `F3+F4` (Spielmodus-Umschalter), `F3+F6` (Debug-Menü).
      3. **`[3/3] 🔧 F3+ Utilities`:** `F3+G` (Chunk-Grenzen), `F3+B` (Hitboxen), `F3+H` (Erweiterte Item-Tooltips), `F3+A` (Chunks neu laden), `F3+N` (Spectator ↔ Kreativ), `F3+D` (Chat leeren), `F3+C` (Koordinaten/TP-Befehl kopieren), `F3+T` (Ressourcenpacks neu laden).

### Intelligenter Netzbetrieb (AC) vs. Akkubetrieb & Lüfter-Leisemodus (90/60 FPS Target)
* **Änderung:** Differenzierte Stromspar- und Thermalkontrolle in [`PowerManager.java`](src/main/java/com/steamdeck/mc/power/PowerManager.java) und [`DynamicChunkThreading.java`](src/main/java/com/steamdeck/mc/power/DynamicChunkThreading.java).
  * **Begründung:** Im Netzbetrieb (am Ladekabel) sollen Komfort-Einschränkungen wie starkes Dimmen oder frühes FPS-Drosseln zurückgefahren werden, während die Performance-Optimierungen aktiv bleiben müssen, um 90 FPS (OLED) bzw. 60 FPS (LCD) konstant zu halten und das Gebläse (Lüftergeräusch) minimal zu halten.
  * **Umsetzung:**
    * **Menü-FPS:** 60 FPS im Netzbetrieb (statt 30 FPS im Akkubetrieb).
    * **AFK-Drosselung im Netzbetrieb:** Startet erst nach 4 Minuten (statt 2 Min.), senkt sanfter um 5 %/Min. und drosselt maximal um 50 % auf mindestens 40 FPS (statt 15 FPS).
    * **OLED-Dimming im Netzbetrieb:** Startet erst nach 5 Minuten (statt 3 Min.) und dunkelt maximal um dezente 30 % (statt 65 %) ab.
    * **Chunk-Threading & Thermal-Cap für leises Gebläse:** `DynamicChunkThreading` bleibt auch im Netzbetrieb aktiv und nutzt dort kontrollierte **3 Worker-Threads** (Akkubetrieb: 2 Threads, AFK: 1 Thread) mit reduzierter Priorität, ergänzt durch den 90-Hz-Thermal-Cap. Das verhindert 100-%-CPU/GPU-Lastspitzen, hält das 90/60-FPS-Target stabil und sorgt für einen flüsterleisen Lüfter.

### Gyro-Fix (Kontextabhängig beim aktiven Bogen-/Fernrohr-Zielen)
* **Änderung:** Überarbeitung von [`GyroManager.java`](src/main/java/com/steamdeck/mc/input/gyro/GyroManager.java), [`DsuGyroProvider.java`](src/main/java/com/steamdeck/mc/input/gyro/DsuGyroProvider.java) und [`SDL2GyroProvider.java`](src/main/java/com/steamdeck/mc/input/gyro/SDL2GyroProvider.java).
  * **Begründung:** `DsuGyroProvider` meldete beim Start fälschlicherweise immer Verfügbarkeit, wodurch `SDL2GyroProvider` übersprungen wurde. Zudem wurde das Zusammenspiel mit Steam Input optimiert.
  * **Umsetzung:** Alle Gyro-Backends werden parallel abgefragt; zusätzlich schaltet der rechte Stick beim aktiven Spannen eines Bogens/Fernrohrs (`player.isUsingItem()`) automatisch vom Radial-Menü auf hochpräzises Zielen (2 % Deadzone) um.

### Direktionales Haptik-Feedback beim Hotbar-Wechsel (Trackpads)
* **Änderung:** Implementierung von [`HapticFeedbackManager.java`](src/main/java/com/steamdeck/mc/power/HapticFeedbackManager.java) mit direkter Ansteuerung der LRA-Aktuatoren über `SDLGamepad.SDL_RumbleGamepad` und `SDLJoystick.SDL_RumbleJoystick`.
  * **Begründung:** Der Spieler wünschte taktiles Feedback beim Durchschalten der Hotbar-Slots, wobei genau das Trackpad rüttelt, in dessen Richtung geschaltet wird.
  * **Umsetzung:**
    * Wechsel nach links (`HOTBAR_PREV`): Ein präziser 35ms-Impuls auf dem linken LRA-Aktuator des linken Trackpads (`low_frequency` Rumble).
    * Wechsel nach rechts (`HOTBAR_NEXT`): Ein präziser 35ms-Impuls auf dem rechten LRA-Aktuator des rechten Trackpads (`high_frequency` Rumble).
    * Natürliches Scroll-Verhalten: Ein kurzes Antippen schaltet exakt 1 Slot weiter. Bleibt die Taste gedrückt, wird nach einer kurzen natürlichen Pause (250 ms) kontinuierlich alle 90 ms im gewählten Takt weitergeschaltet.
    * Konfigurierbar im Mod-Menü (Tab „⚡ Akku & Eco“).

### SteamOS Dual-Cursor Trackpad-Tippen (OSK)
* **Änderung:** Vollwertige Dual-Maus-/Dual-Cursor-Steuerung für die On-Screen-Tastatur ([`VirtualKeyboard.java`](src/main/java/com/steamdeck/mc/gui/VirtualKeyboard.java)).
  * **Begründung:** Wie bei der nativen SteamOS-Bildschirmtastatur soll schnelles, flüssiges Tippen mit beiden Daumen möglich sein, ohne die Hände vom Deck zu nehmen oder das Display zu berühren.
  * **Umsetzung:**
    * Linker Stick / Linkes Trackpad steuert ein türkisfarbenes Fadenkreuz (`#00FFCC`) über die linke Tastaturhälfte.
    * Rechter Stick / Rechtes Trackpad steuert ein bernsteinfarbenes Fadenkreuz (`#FFB300`) über die rechte Tastaturhälfte.
    * Tasten unter den Fadenkreuzen werden visuell hervorgehoben.
    * **Auslöser:** Klick auf das linke Trackpad, LT oder LB tippt sofort die Taste unter dem linken Cursor (inkl. linkem Haptik-Klick). Klick auf das rechte Trackpad, RT oder RB tippt die Taste unter dem rechten Cursor (inkl. rechtem Haptik-Klick).

### Kapazitiver Stick-Touch (L-Stick Touch & R-Stick Touch)
* **Änderung:** Hinzufügen der Knöpfe `LEFT_STICK_TOUCH` und `RIGHT_STICK_TOUCH` zu [`DeckButton.java`](src/main/java/com/steamdeck/mc/input/DeckButton.java) und Integration in die Hardware-Bridge.
  * **Begründung:** Die Joysticks des Steam Decks besitzen kapazitive Berührungssensoren (Stick Touch). Diese können in Steam Input frei belegt werden (z. B. auf Tasten oder Gamepad-Misc-Buttons) und stehen nun in Minecraft als vollwertige Eingaben zur Verfügung (z. B. für Gyro-Aktivierung, Zoom, Sneak oder freie Tastaturbelegungen).
  * **Konfiguration:** Im Tab „⚙️ Bridge“ kann jeder Stick-Touch einer Steam-Taste zugewiesen oder per Klick direkt angelernt werden.

### UI-Layout-Optimierung & Sub-Tabs im Aktionsauswahl-Modal
* **Änderung:** Behebung der visuellen Überlappungen und unzugänglichen Optionen im Menü ([`SteamDeckConfigScreen.java`](src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java)):
  * **Dynamische Zeilenskalierung:** Zeilenhöhe und -abstände passen sich automatisch der Displayhöhe an, sodass alle 12 Buttons auf jeder Seite ohne Überlappung mit den Fußzeilen-Buttons dargestellt werden.
  * **Aktions-Modal mit 2 Sub-Tabs:** Aufteilung in `🎮 Spiel-Aktionen` (17 Einträge) und `🖱️ Maus & Spezial` (Mausklick links, rechts, mitte, Shift-Klick, Bildschirm schließen, Freie Taste). Dadurch sind Mausklicks und Spezialaktionen uneingeschränkt auswählbar.
  * **2-Spalten-Bridge-Dashboard:** L4/R4, L5/R5 und L-Touch/R-Touch sind übersichtlich links und rechts angeordnet und passen perfekt auf den 800p-Bildschirm.

### Dynamische AFK-Drosselung & OLED Screen-Dimming
* **Änderung:** Implementierung des intelligenten AFK-Stromsparmoduls in [`PowerManager.java`](src/main/java/com/steamdeck/mc/power/PowerManager.java), [`FramerateLimitTrackerMixin.java`](src/main/java/com/steamdeck/mc/mixin/FramerateLimitTrackerMixin.java) und [`HudMixin.java`](src/main/java/com/steamdeck/mc/mixin/HudMixin.java).
  * **Begründung:** Wenn der Spieler das Steam Deck beiseitelegt oder kurz AFK geht, soll der Akku geschont werden, ohne das Spiel pausieren zu müssen.
  * **Dynamischer Ablauf:** Nach 2 Minuten Inaktivität sinkt das FPS-Limit stufenlos und vollkommen ruckelfrei um 10 % pro Minute, bis maximal 80 % Drosselung erreicht sind (z. B. 90 → ~18 FPS).
  * **OLED Screen-Dimming:** Nach 3 Minuten Inaktivität dunkelt sich der Bildschirm sanft um bis zu 65 % ab (spart besonders beim OLED-Display massiv Watt ein) und zeigt einen dezenten Hinweis (`💤 AFK Stromsparmodus (-XX%) • Taste drücken`).
  * **Hintergrund-Idle:** [`DynamicChunkThreading.java`](src/main/java/com/steamdeck/mc/power/DynamicChunkThreading.java) drosselt Chunk-Arbeiter während AFK auf 1 Thread, damit CPU-Kerne in tiefere C-States schlafen gehen können.
  * **Partikel & Wetter:** Bleiben auf Wunsch des Spielers völlig unangetastet für ein ungestörtes visuelles Erlebnis.
  * **Sofortiges Erwachen:** Jegliche Eingabe (Stick, Button, Touch, Mauszeiger, Tastatur) stellt die volle Leistung (100 % FPS) und Helligkeit sofort in Bruchteilen einer Millisekunde wieder her.

### Volle Tastenunabhängigkeit, Native Zoom & Trackpad-Klicks
* **Änderung:** Vollständige Entkopplung und Einbindung aller 4 Rücktasten (**L4, R4, L5, R5**), beider **Trackpad-Klicks** (`LEFT_TRACKPAD`, `RIGHT_TRACKPAD`) sowie beider **Stick-Klicks** (`L3`, `R3`) in [`DeckButton.java`](src/main/java/com/steamdeck/mc/input/DeckButton.java) und [`SteamDeckConfigScreen.java`](src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java).
  * **Begründung:** Bisher waren L1/L4 und R1/R4 durch Doppelbelegung gekoppelt. Der Spieler benötigt 100 % freie Kontrolle über jeden einzelnen physischen Knopf des Decks.
* **Änderung:** Neue native 4x-Zoom-Aktion (`DeckAction.ZOOM`) über [`AbstractClientPlayerMixin.java`](src/main/java/com/steamdeck/mc/mixin/AbstractClientPlayerMixin.java).
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
* **Änderung:** Implementierung der ModMenu-Schnittstelle (`com.terraformersmc.modmenu.api.ModMenuApi`), eines Pause-Menü-Buttons (`[ 🎮 Controller ]`) und eines visuellen Konfigurationsbildschirms ([`SteamDeckConfigScreen`](src/main/java/com/steamdeck/mc/gui/SteamDeckConfigScreen.java)).
  * **Begründung:** Bisher mussten Belegungen und Einstellungen in `steamdeck_essentials.json` editiert werden. Der neue Bildschirm ermöglicht intuitive Tastenbelegung direkt am Handheld-Touchscreen oder mit Controller-Navigation.
* **Änderung:** Einführung von 3 intelligenten, kontextbasierten **Action-Sets** ([`ActionSet.java`](src/main/java/com/steamdeck/mc/input/ActionSet.java)):
  1. `IN_GAME` (Erkunden, Laufen, Bauen)
  2. `MENUS` (Inventar, Kisten, Amboss – mit Mausemulation über R2/A für Linksklick, L2/X für Rechtsklick und Y für Shift-Schnellverschieben)
  3. `AIMING` (Bogen, Armbrust, Dreizack, Fernrohr – mit automatischem Gyro-Zielen)
  * **Begründung:** In Menüs machen Bewegungs-Tasten keinen Sinn; dort werden Klicks und Schnellverschieben benötigt. Beim Spannen von Bogen/Fernrohr soll automatisch das Gyro-Zielen aktiviert werden, während es beim normalen Laufen ruht.

### OSK-Verfeinerung & Quick-Bar-Entfernung
* **Änderung:** Entfernung der Quick-Command-Leiste (`/home`, `/spawn`, `TAB`, `Enter`) über der Bildschirmtastatur ([`VirtualKeyboard.java`](src/main/java/com/steamdeck/mc/gui/VirtualKeyboard.java)).
  * **Begründung:** Die Leiste war unhandlich und verdeckte zu viel Sichtfeld auf dem 16:10-Bildschirm (1280×800).
* **Änderung:** Automatisches Aufklappen des OSK auf [`ChatScreen`](src/main/java/com/steamdeck/mc/SteamDeckEssentials.java) beschränkt; Menüs mit Suchleisten (wie das Kreativ-Inventar) öffnen stattdessen einen diskreten `KB`-Button in der Ecke.
  * **Begründung:** Im Kreativ-Inventar will man meistens Items auswählen und nicht sofort tippen; das automatische Aufklappen blockierte die Item-Gitter.

### Gamescope & Fullscreen-Fix
* **Änderung:** Drosselungslogik in [`PowerManager.java`](src/main/java/com/steamdeck/mc/power/PowerManager.java) entkoppelt von `isWindowActive()` / Fokusverlust und stattdessen an `isIconified()` gebunden.
  * **Begründung:** Im SteamOS Gaming Mode (Gamescope/Wayland) meldet SDL3 bei „Exclusive Fullscreen“ fälschlicherweise Fokusverlust. Minecrafts `FramerateLimitTracker` und die Mod hatten die Bildrate daraufhin fälschlicherweise auf 10 FPS gedrosselt. Durch Deaktivieren von Fullscreen im Spiel skaliert Gamescope das Bild nativ ohne FPS-Einbruch.

### SDL3-Subsystem-Initialisierung & Keybind-Klicks
* **Änderung:** Expliziter Aufruf von `SDLInit.SDL_InitSubSystem(SDL_INIT_GAMEPAD | SDL_INIT_JOYSTICK | SDL_INIT_SENSOR)` und `SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS`.
  * **Begründung:** Minecraft 26.3 initialisiert über LWJGL/SDL3 ausschließlich `SDL_INIT_VIDEO`. Ohne Initialisierung der Gamepad-Subsysteme meldete SDL3 `SDL_HasGamepad() == false` und ignorierte Controller-Eingaben komplett.
* **Änderung:** Erstellung des [`KeyMappingAccessor`](src/main/java/com/steamdeck/mc/mixin/KeyMappingAccessor.java) für atomare `clickCount++`-Registrierung.
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
