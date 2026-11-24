# SteamDeck Essentials – Vision & Plug-and-Play-Roadmap

> **Leitziel:** Mit angeschlossenem Controller soll sich Minecraft so anfühlen, als wäre es von Grund auf als natives Controller-Spiel entwickelt worden – maximal anpassbar, aber ab Werk sofort spielbar. Sobald man mit Maus & Tastatur spielt, verhält sich die Mod komplett unsichtbar und stört an keiner Stelle.

---

## Grundprinzip: „Simple by default, deep when you open it“
1. **Plug & Play ab Werk:** Einstecken, losspielen – sinnvolle Defaults pro Controller-Typ (`Steam Deck`, `Steam Controller 2026`, `PS5 DualSense`, `Xbox`, `DualShock 4`, `Switch Pro`).
2. **Unsichtbar bei Maus & Tastatur:** Kein verändertes Chat-Layout, kein OSK, keine Controller-Legenden, keine abgefangenen Tasten, sobald Maus/Tastatur aktiv sind.
3. **Keine künstlichen Grenzen bei der Anpassung:** Jeder Button, jeder Stick, jedes Trackpad und jedes Radial-Menü soll frei kombinierbar sein – aber sauber gestaffelt, damit Einsteiger nie erschlagen werden.

---

## Phase 1 – Konfliktfreiheit & volle Mod-Hotkey-Kompatibilität
* [x] **Modifier & Tastenkombinationen für `CUSTOM_KEY`:** Unterstützung für `Strg / Shift / Alt` und 2-Tasten-Chords (z. B. `M + C` für Litematica / MaLiLib / MiniHUD / Tweakeroo).
* [x] **Mixins konfliktfrei absichern:**
  * `LevelRendererMixin`: `@Redirect` auf `require = 0` umgestellt (Verträglichkeit mit Sodium/VulkanMod/Renderer-Mods).
  * `ChatComponentMixin`, `CommandSuggestionsMixin` & `TooltipMixin`: defensiv mit `require = 0` (Verträglichkeit mit Chat Heads, Legendary Tooltips etc.).
  * `PauseScreenMixin`: `🎮 Controller`-Button kollisionsfrei positioniert (weicht automatisch nach unten aus, falls andere Mods die obere rechte Ecke nutzen).
* [x] **Erkennung anderer Controller-Mods:** Warnhinweis beim Start, wenn `controlify` oder `midnightcontrols` aktiv sind (verhindert unbemerkte doppelte Eingaben).

## Phase 2 – Plug & Play für alle Controller & Screens
* [x] **Weitere Controller nativ erkennen (`ControllerKind`):**
  * **PS4 DualShock 4** (1 Touchpad + Gyro + PS-Glyphen)
  * **Xbox One / Series** (Standard-Profil mit `R-Stick = Kamera`, Xbox-Glyphen)
  * **Nintendo Switch Pro** (Gyro + Standard-Profil mit `R-Stick = Kamera`)
* [x] **Konsequentes Auto-Umschalten Controller ↔ Maus/Tastatur:** Inventar-Legenden, Hinweise, OSK und Chat-Layout blenden sich sofort aus, sobald Maus/Tastatur benutzt werden (`isControllerRecentlyUsed()` überall einheitlich).
* [x] **Kein Aussperren (Not-Fallback):** Wenn im aktiven Profil kein `PAUSE` / `CLOSE_SCREEN` belegt ist, greifen `Start` / `B` automatisch als Fallback.
* [x] **Spezial-Screens ohne Maus-Gefummel:** Kreativ-Inventar (Tabs per `L1`/`R1` wechseln), sonstiges Listen-Scrollen per `L1`/`R1` in Menüs.
* [x] **Controller-Akku im Pausemenü auch im Desktop-Modus** (inkl. Controller-Name für Funk-Controller wie SC 2026 / DualSense / DS4).
* [x] **Verständliche Linux-/Flatpak-Hinweise:** Kurzer Hinweis im Konfigurator, falls `/dev/hidraw*` wegen fehlender Rechte/Sandbox nicht lesbar ist.

## Phase 3 – Maximale Anpassbarkeit (intuitiv gestaffelt)
* [x] **Frei konfigurierbare Radial-Menü-Ebene (`⭐ Eigene Shortcuts`):**
  * 4. Radial-Menü-Ebene mit 8 frei belegbaren Slots (`RADIAL_1..8`) für Spiel-Aktionen, Hotbar-Slots, Mod-`KeyMapping`s und `CUSTOM_KEY`s (inkl. Modifier/Chord) + direkter 8-Slot-Editor im Konfigurator unter *Rechter Stick → Radial-Shortcuts belegen …*.
* [x] **Erweiterte Trackpad-Modi:**
  * **DualSense / Zentral-Touchpad Zonen & Scrollrad:** Wählbar zwischen *Kamera*, *1 Taste*, *2 Zonen (Links = Trackpad L / Rechts = Trackpad R)*, *Scrollrad (Hotbar)* und *Aus*.
  * **Dual-Pad Trackpads:** *Taste*, *D-Pad zum Laufen*, *3×3-Raster (9 Felder)* mit Stick-Schutz.
* [ ] **Eingabe-Modifikatoren / Activators (Steam-Input-Prinzip, spätere Ausbaustufe):**
  * Pro Taste optional: *Kurz drücken*, *Gedrückt halten (Long Press)*, *Doppelklick (Double Tap)* und *Mode-Shift*.

## Phase 4 – Natives Konsolen-Spielgefühl (Event-basiertes Rumble & Haptik)
Alle Effekte einzeln togglebar + globale Stärke regelbar:
* [x] **Schaden & Fallschaden (`hapticOnDamage`):** kurzer, kräftiger Rumble-Stoß bei Treffern.
* [x] **Block abgebaut (`hapticOnBlockBreak`) & Kampf-Treffer (`hapticOnAttackHit`):** dezenter Bestätigungs-Impuls beim Abbauen und Nahkampf-Treffer.
* [x] **Bogen / Armbrust / Dreizack & Werkzeug-Haltbarkeit (< 10 %):** mehrstufiges Zug-Feedback & Warn-Impuls kurz vor dem Zerbrechen.

## Phase 5 – Release-Polish
* [ ] **Vollständige Lokalisierung (DE + EN):** Alle UI-Texte in `ControllerLayoutScreen`, `SteamDeckConfigScreen`, `ControllerToast` und `VirtualKeyboard` in `lang/de_de.json` und `lang/en_us.json` auslagern.
* [ ] **Smoke-Tests / CI:** Automatisierte Prüfung von Build, JSON-Dateien und Profil-Migration.
