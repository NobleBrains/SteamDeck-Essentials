# FINDINGS – Steam Input / SteamOS: Interoperabilitäts-Fakten für „SteamDeck Essentials“

Clean-Room-Dokumentation. Es wurde **kein Valve-Code übernommen**. Hier stehen nur Fakten (Konstanten,
Byte-Layouts, Formeln in eigener Notation) mit Fundstelle. Alle Binärdateien wurden nur gelesen
(objdump/readelf/Python-Parser), nichts aus der ISO oder aus Steam wurde ausgeführt.

**Sicherheit:** `hoch` = direkt im Disassembly/Klartext gesehen · `mittel` = gesehen, aber die Deutung
der Semantik ist abgeleitet · `niedrig` = Parser-Artefakt möglich oder nur indirekt belegt.
**Vermutung** = nicht belegte Hypothese, muss am Gerät geprüft werden.

Abkürzungen: `SC` = `~/.local/share/Steam/linux64/steamclient.so` (45 256 824 B, Steam-Snapshot ohne
Symbole; Adressen = VMA, `.rodata` VMA = Dateioffset, `.data.rel.ro` VMA − 0x2000 = Dateioffset).
`SDL3` = `ubuntu12_64/libSDL3.so.0` (3 184 456 B). `HS7` = Host `hid-steam.ko` (Kernel 7.3.4-ogc8.1),
`HS6` = ISO `hid-steam.ko.zst` (6.16.12-valve24.4).

---

## 1. Bestandsaufnahme

| Objekt | Befund | Fundstelle | Sicherheit |
|---|---|---|---|
| ISO | `steamdeck-oobe-repair-20260707.10-3.8.14.img.bz2`, `VERSION_ID=3.8.14`, `BUILD_ID=20260707.10`, Codename `holo`, Variante `steamdeck-oobe` | `extracted/rootfs/etc/os-release` | hoch |
| Steam-Client in der ISO | **nur Bootstrap-Archiv** `/usr/lib/steam/bootstraplinux_ubuntu12_32.tar.xz` (408 MB, 13 644 Einträge). Es enthält aber einen kompletten Client-Snapshot vom 2026-02-17 (`linux64/steamclient.so`, `ubuntu12_64/libSDL3.so.0`, `steamui/`, `controller_base/` inkl. `chord_triton.vdf`) | `extracted/bootstrap_filelist.txt` | hoch |
| Kernelmodul ISO | `hid-steam.ko.zst` (6.16.12): Aliase nur `28de:1205`, `1142`, `1102`, **kein Triton** | `modinfo` HS6 | hoch |
| Kernelmodul Host | HS7 `steam_controllers`: `1102`, `1142`, `1205` (Deck), `1302` (Triton USB), `1303` (Triton BLE, Bus 0x0005), `1304` (Puck), `1305`; Parameter `lizard_mode` | HS7 `.rodata+0xba0..0xc40` | hoch |
| udev | `70-steam-jupiter-input.rules`: `usb`, `hidraw*`, `uinput` → `TAG+="uaccess"` | rootfs `/usr/lib/udev/rules.d/` | hoch |
| Controller-Firmware | `jupiter-controller-update`: `D21_APP_REL_69C6B03E.bin`, `D20_APP_REL_69C6B03D.bin`, `RA_APP_REL_69C6B03C.bin` (Build 2026-03-27) | rootfs | hoch |
| controller_base | 80 `.vdf`-Vorlagen in `~/.local/share/Steam/controller_base/` | Klartext | hoch |
| inputplumber / gamescope | nicht controller-relevant ausgewertet, da Steam Input selbst im Client steckt | – | – |

---

## 2. VDF-Schema (controller_base + Enum-Tabellen in SC)

### 2.1 Struktur einer Vorlage

| Ebene | Schlüssel | Werte / Bedeutung | Sicherheit |
|---|---|---|---|
| Top-Level | `controller_type` | `controller_neptune`, `controller_triton`, `controller_legion_go_s`, `controller_ps4`, `controller_ps5`, `controller_xboxone`, `controller_switch_pro`, `controller_switch_joycon_left/right`, `controller_steamcontroller_gordon`, `controller_android`, `controller_apple`, `controller_mobile_touch` | hoch |
| Top-Level | `settings` | `left_trackpad_mode`, `right_trackpad_mode`, `action_set_trigger_cursor_show`, `action_set_trigger_cursor_hide` | hoch |
| `group` | `mode` | siehe 2.2 | hoch |
| `group.inputs.<input>.activators.<Activator>` | `bindings`, `settings` | siehe 2.4 | hoch |
| `preset.group_source_bindings` | Quelle → `active`/`inactive`/`modeshift` | Quellen: `switch`, `button_diamond`, `left_trigger`, `right_trigger`, `joystick`, `right_joystick`, `dpad`, `left_trackpad`, `right_trackpad`, `center_trackpad`, `gyro` | hoch |

Häufigkeit der Modi in den 80 VDFs: `trigger` 126, `dpad` 123, `joystick_move` 79, `absolute_mouse` 76,
`four_buttons` 65, `switches` 61, `joystick_mouse` 50, `flickstick` 40, `single_button` 28, `scrollwheel` 9,
`disabled` 8, `gyro_to_mouse` 7, `joystick_camera` 2, `mouse_joystick` 1.
Activators: `Full_Press` 1151 (+13 klein geschrieben), `Long_Press` 43, `Double_Press` 32, `Start_Press` 31,
`Soft_Press` 27, `release` 18. Der Parser akzeptiert also Groß- und Kleinschreibung.

### 2.2 `EControllerSourceMode` (SC `.data.rel.ro 0x2f2d5a0`, Sicherheit hoch)

`0 none · 1 dpad · 2 buttons · 3 four_buttons · 4 absolute_mouse · 5 relative_mouse · 6 joystick_move ·
7 joystick_mouse · 8 joystick_camera · 9 scrollwheel · 10 trigger · 11 touch_menu · 12 mouse_joystick ·
13 mouse_region · 14 radial_menu · 15 single_button · 16 switches · 17 flickstick · 18 2dscroll · 19 hotbar ·
20 disabled · 21 reference · 22 gyro_to_mouse · 23 gyro_to_joystick · 24 gyro_to_joystick_deflection ·
25 gyro_to_camera_angles · 26 joystick_to_camera_angles · 27 flickstick_to_camera_angles ·
28 touchpad_to_camera_angles · 29 camera_angles`

### 2.3 `EControllerActivationType` (SC `0x2f2d500`, hoch)

`0 none · 1 full_press · 2 soft_press · 3 start_press · 4 release · 5 long_press · 6 double_press · 7 analog · 8 chord`

Setting-Namen: Tabelle `EControllerSetting` (SC `0x2f2d780`, Einträge `{u64 id, char* name}`, IDs 0..164,
ID 71 fehlt). Valve-Tippfehler, die man **genau so** in VDFs schreiben muss: `hold_repeat_inverval` (26),
`doubetap_max_dist` (46), `doubetap_min_duration` (47), `doubetap_max_duration` (48), `soft_press_sytle` (112).

Die Typen kommen aus RTTI `CModeSettingIbE` (bool), `IiE` (int32), `IyE` (uint64), `IfE` (float), `IsE` (string).
Speicherlayout int/float: `+0x08` Wert, `+0x0c` Default, `+0x10` Min, `+0x14` Max, `+0x18` isDefault.

### 2.4 Activators – Settings (Konstruktoren in SC)

| Activator (Ctor) | Setting (ID) | Typ | Default | Bereich | Sicherheit |
|---|---|---|---|---|---|
| alle | `hold_repeats` (25) | bool | 0 (Double_Press: **1**) | – | hoch |
| alle mit Repeat | `repeat_rate` (104) | int ms | 100 | 10..1000 | hoch |
| alle | `haptic_intensity` (3) | int | Full_Press/Chord **0**, alle anderen **2** | 0..3 | hoch |
| alle | `delay_start` (100) / `delay_end` (101) | int ms | 0 | 0..1000 (Double_Press `delay_end` 0..100) | hoch |
| alle | `cycle` (102), `toggle` (103), `interruptable` (111) | bool | 0 | – | hoch (Default-Byte: mittel) |
| Full/Soft/Chord | `edge_binding_invert` (20) | bool | 0 | – | hoch |
| Full_Press `0x11c9510` | `activate_stick_or_trackpad` (120), `activator_invert_input` (162) | int/bool | 0 | – | mittel |
| Soft_Press `0x11c9a2e` | `soft_press_sytle` (112) | int | 0 | 0..5 | hoch |
| Soft_Press | **`activation_threshold` (107)** | int | **4096** | 0..32255 | hoch |
| Long_Press `0x11ca8ac` | `long_press_time` (106) | int ms | **450** | 0..3000 | hoch |
| Long_Press | `activate_stick_or_trackpad` (120) | int | 0 | 0..3 | hoch |
| Double_Press `0x11cad90` | `double_tap_time` (105) | int ms | **190** | 80..1000 | hoch |
| Double_Press | `activate_stick_or_trackpad` (120) | int | 0 | 0..5 | hoch |
| Start_Press `0x11ca024`, Release `0x11ca464` | haptic/delay/interruptable/toggle/cycle | s. o. | | | hoch |
| Analog `0x11cb2a4` | `deadzone_inner_radius` (12) / `deadzone_outer_radius` (13) | int | 1000 / 32000 | 0..32767 | hoch |
| Analog | `activator_output_axis` (113) | int | 0 | 0..10 | hoch |
| Chord `0x11c8f41` | `chord_button` (114) | int | 0 | 0..3 | mittel |
| Chord | `gyro_ratchet_button_mask` (157) | u64 | 0 | – | hoch |
| Chord | `gyro_ratchet_button_requireany_or_all` (160) | int | 0 | 0..1 | hoch |

### 2.5 Modi – Settings (Auswahl mit Defaults; Ctor-Adressen in SC)

Hinweis: Bei float-Settings hat der Parser Min/Max nicht getrennt ausgelesen (er meldet Min = Max = Default).
Dort ist nur der Default belastbar.

**`absolute_mouse` (CAbsoluteMouseMode, Ctor `0x1243260`)**

| Setting (ID) | Typ | Default | Bereich | Sicherheit |
|---|---|---|---|---|
| `sensitivity` (30) | float | 100.0 | – | hoch |
| `gyro_natural_sensitivity` (140) | float | 100.0 | – | hoch |
| `trackball` (33), `doubletap_beep` (38), `invert_x` (41), `invert_y` (42) | bool | – | – | hoch |
| `haptic_intensity` (3) | int | 1 | 0..3 | hoch |
| `rotation_2` (45) | int ° | 0 | −180..180 | hoch |
| `rotation` (128) | int ° | 0 | −30..30 | hoch |
| `friction` (34) | int | 2 | 0..4 | hoch |
| `friction_vert_scale` (35), `sensitivity_vert_scale` (31) | int % | 100 | 0..200 | hoch |
| `acceleration` (36) | int | 0 | 0..3 | hoch |
| `mouse_smoothing` (43) | int | 20 | 0..40 | hoch |
| `mouse_move_threshold` (44) | int | 0 | 0..40 | hoch |
| `edge_spin_velocity` (40) / `edge_spin_radius` (39) | int | 0 / 25000 | 0..1000 / 0..32767 | hoch |
| `doubetap_max_dist` (46) | int | 3800 | 0..32767 | hoch |
| `doubetap_max_duration` (48) / `doubetap_min_duration` (47) | int ms | 325 / 5 | 20..500 / 0..100 | hoch |
| `gyro_button` (65) | int | 1 | 0..38 | hoch |
| `gyro_ratchet_button_mask` (157) | u64 | 0 | – | hoch |
| `gyro_button_haptic_effect` (158) / `gyro_on_off_haptic_intensity` (161) | int | 3 / 0 | 0..3 | hoch |
| `mouse_dampening_trigger` (78) | int | 0 | 0..6 | hoch |
| `mouse_trigger_clamp_amount` (79) | float | 90.0 | – | hoch |
| `gyro_axis` (64) / `gyro_button_invert` (69) | int | 0 / 1 | 0..2 | hoch |
| `mousejoystick_deadzone_x/_y` (50/51) | int | 10000 | 1..20000 | hoch |
| `mousejoystick_precision` (52) | int | 75 | gespeichert als Min 100 / Max 1 (umgekehrt) | hoch |
| `custom_curve_exponent` (10) | int | 200 | 100..300 | hoch |
| `gyro_sensitivity_scale` (81) | int | 300 | 1..400 | hoch |
| `deadzone_outer_radius` (13) | int | 2000 | 0..22000 | hoch |
| `gyro_yaw_scale` / `gyro_roll_scale` (125/126) | int | 10000 | −10000..10000 | hoch |

**`dpad` (CDPadMode, `0x124a98f`):** `requires_click` bool 0 · `layout` 1 (0..3) · `hold_repeat_inverval` 100 (10..500) ·
`deadzone` (string) · `edge_binding_radius` float 25000 · `haptic_intensity_override` 5 (0..5) ·
`analog_emulation_period` 30 (1..500) · `analog_emulation_duty_cycle_pct` 15 (1..99) · `overlap_region` 4000 (2000..16000) ·
`gyro_button` 0 · `gyro_neutral` 16384 (0..32767). — hoch

**`four_buttons` (`0x1251ee3`):** `button_size` 18000 · `button_dist` 20000 (1..32767) · `deadzone_inner/outer_radius` 1000 (50..10000) ·
`deadzone_shape` 1 (1..40) · `hold_repeat_inverval` 100 · `haptic_intensity_override` 5 · `output_trigger` 0 (0..2). — hoch

**`flickstick` (`0x124e345`):** `transition_time` 100 (0..250) · `sensitivity` float 1000 · `rotation` 0 (−90..90) · `mouse_smoothing` float 30 ·
`flickstick_command_binding_radius` 80 (20..100) · `flickstick_rotation_sensitivity` 6545 (1..32000) ·
`deadzone_inner_radius` 40 / `deadzone_outer_radius` 72 (0..80, in %) · `flickstick_snap_mode` 5 (0..5) ·
`flickstick_forward_deadzone_angle` 28 (0..720) · `flickstick_snap_tightness` 64 · `flickstick_sweep_tightness` 56 (0..80) ·
`flickstick_sweep_sensitivity` 8 (0..48) · `flickstick_depression_speed` float 20 · `output_axis` 1 · `haptic_intensity` 2 ·
`flickstick_haptic_bump_per_angle` 5 (0..360). — hoch

**`gyro_to_mouse` (CGyroTo2DMode `0x1259144`, CGyroToMouseMode `0x1261d50` erbt):** `sensitivity` float 100 ·
**`gyro_natural_sensitivity` float 250** · `gyro_vertical_horizontal_ratio` 400 (0..800) · `gyro_to_2d_conversion_style` 0 (0..7) ·
`gyro_mouse_sample_angle_offset` 0 (−180..180) · `gyro_speed_deadzone` 36 (0..100) · `gyro_precision_speed` 75 (0..1500) ·
`friction` 100 (0..720) · `friction_vert_scale` 200 (0..720) · `acceleration` 0 (0..3) · `gyro_button` 1 · `gyro_button_invert` 1 ·
`gyro_use_per_controller_preferences` 2 (0..2) · `gyro_deflection_to_joystick_max` 0 (0..3) · `custom_curve_exponent` 200. — hoch

**`joystick_move` (`0x1273f67`):** `curve_exponent` 0 (0..5) · `custom_curve_exponent` float 200 · `acceleration_axis_style` 1 ·
`edge_binding_radius` float 25000 · `anti_deadzone` 0 · `anti_deadzone_buffer` 0 (0..32767) · `virtual_cap_size` 7500 (1000..20000) ·
`virtual_stick_throw` 11000 (4000..28000) · `deadzone_inner_radius` 0 (0..32000) · `deadzone_shape` 1 (0..2) · `deadzone_enable_type` 1 (0..2) ·
`sensitivity` float 100 · `scale` 80 (1..100) · `position_x/y` 50 · `pressurejoystick_min_pressure` 300 / `_max_pressure` 5000 (0..32767). — hoch;
einzelne Einträge (`gyro_button` = 16384, `output_axis` 2 mit Min 2/Max 1, `mouse_trigger_clamp_amount` 4000) sind **niedrig** (Parser-Versatz wahrscheinlich).

**`joystick_camera` (`0x126cd97`):** `curve_exponent` 0 · `swipe_duration` 1 (0..2) · `sensitivity_vert_scale` 100 (25..175) ·
`anti_deadzone` **5200** · `sensitivity` float 100. — hoch

**`joystick_to_camera_angles` (`0x127a363`):** `curve_exponent` 3 · `deadzone_outer_radius` 28000 · `sensitivity` float 270. — hoch (hintere Einträge niedrig)

**`touch_menu` (`0x129b7bd`):** `touch_menu_button_count` 0 (0..16) · `touch_menu_opacity` 90 (40..100) · `touch_menu_position_y` 80 ·
`touch_menu_scale` 100 (50..150) · `touchmenu_inner_deadzone` 25 · `touchmenu_outer_deadzone` 0 (0..100) · `activation_button` 13 (0..16) ·
`touchmenu_button_fire_type` 0 (0..3) · `sensitivity` float 180 · `sensitivity_vert_scale` 56. — hoch

**`trigger` (`0x12a8d43`):** `hold_repeat_inverval` 100 · `output_trigger` 0 (0..2) · `deadzone_outer_radius` 32000 · `deadzone_inner_radius` 1000 ·
`edge_binding_radius` 10000 (0..32767) · `haptic_intensity` 2 · `adaptive_threshold` 1 (0..5) · `curve_exponent` 0 (0..5) ·
`custom_curve_exponent` 1000 (gespeichert als Min 4000 / Max 25) · `test_param_0..5` 55/55/60/60/60/60. — hoch

**Weitere:** `hotbar` (`0x1267abb`) wie touch_menu; ein gemeinsamer Impl-Block `0x122bf87` mit `scroll_angle` 90 (1..180),
`scroll_type` 0 (0..2), `scroll_friction` 2 (0..2), `scroll_wrap`/`scroll_invert` bool (vermutlich scrollwheel/2dscroll, **mittel**).
`switches` (`0x128ed90`), `single_button` (`0x128a0e0`), `radial_menu` (`0x1280300`), `relative_mouse` (`0x1285b00`),
`scrollwheel` (`0x1286470`) nutzen andere Initialisierer (`0x122b6f0`, `0x122fc60`). Ihre Defaults wurden **nicht** extrahiert.
`CControllerModeBase` (`0x12364a0`) registriert die IDs 0x46 (70) und 0x47 (72) als bool (`0x1236614`, `0x1236651`).

---

## 3. Bildschirmtastatur (steamui, Klartext-JS/CSS)

| Aspekt | Fakt | Fundstelle | Sicherheit |
|---|---|---|---|
| Pad → Tastaturhälfte | Jedes Pad steuert einen Zeiger-Container mit **55 % Breite**. Links `left:0` (0–55 %), rechts `right:0` (45–100 %), also **10 % Überlappung in der Mitte** | `virtualkeyboard.css` `.TouchpadPointerContainer {width:55%}` | hoch |
| Zeiger | 30×30 px, Opazität 0.5; gedrückt `scale(0.8)` | css `.TouchpadPointer`, `.PressedDown` | hoch |
| Abbildung | normiertes Pad `x,y ∈ [−1,1]`, `s = inputScale × TrackPadTypingInputScale`; `px = left + width·0.5·(1 + clamp(x·s,−1,1))`, `py = top + height·0.5·(1 − clamp(y·s,−1,1))` (Y invertiert) | `virtualkeyboard.js` Komponente `@63960`, Zeiger `@63782` | hoch |
| Skalierung | Default `TrackPadTypingInputScale = 1`, UI-Slider 1..2, Schritt 0.25 | `sp.js` | hoch |
| Glättung | keine Filterung, nur Drosselung über `requestAnimationFrame` | js `@63960` | hoch |
| Abheben | 100 ms ohne Update → Zeiger aus (`setTimeout(…,100)`) | js `@63960` | hoch |
| Hysterese | Die aktuelle Taste bleibt aktiv, solange der Punkt in **ihrem BoundingClientRect** liegt. Erst beim Verlassen wird neu getestet (`elementsFromPoint`) | `OnTrackpadAnalogInternal @51735` | hoch |
| Haptik beim Tastenwechsel | `PlayHaptic(pad, …, Tick, Intensität 1, 0 dB)` | ebd. | hoch |
| Klick | `LPAD_CLICK`/`RPAD_CLICK` tippt die Taste. Bei `TrackpadTypingTriggerAsClick = true` (Default) klicken auch LT/RT, wenn das Pad aktiv ist | `HandleTrackpadClick @30726` | hoch |
| Tippen bei Drücken oder Loslassen | normale Tasten **beim Drücken** (`KeyDown` → `TypeKey`). Nur Tasten mit Zusatzzeichen/Emoji-Varianten starten einen 450-ms-Long-Press-Timer und tippen beim Loslassen | `KeyDown @28000`, `KeyUp @28891` | hoch |
| Tasten-Fallbacks | LT = Shift, RT = Enter, X/B = Backspace (450 ms Verzögerung, 200 ms Repeat), Y = Leertaste, LB/RB = IME/Emoji, Start = Fenster verschieben | `HandleTrackpadClick` | hoch |
| Haptik-Presets | 0: aus · 1: Tick, Intensität 2, −2 dB · **2 (Default): Click, Intensität 4, −5 dB** · 3: Click, Intensität 3, −3 dB | `sp.js k_Presets @11348855` | hoch |

---

## 4. Formeln & Schwellen (Binäranalyse SC)

| Konstante | Wert | Bedeutung | Fundstelle | Sicherheit |
|---|---|---|---|---|
| Soft-Press-Schwelle | 4096 von 32768 = **0.125** | `activation_threshold`-Default | Ctor `0x11c9a2e` | hoch (Wert) / mittel (Skala gilt für Pad-Druck wie für Trigger) |
| Long Press | 450 ms | `long_press_time` | `0x11ca8ac` | hoch |
| Double Tap | 190 ms | `double_tap_time` | `0x11cad90` | hoch |
| Stick-Deadzone Default | siehe 4.1 | `GetDefaultJoystickDeadzone` | `0x1911aa0` | hoch |
| Gyro-Speed-Deadzone / Präzision | siehe 4.2 | | `0x1911b50` | hoch |
| Triton-Mausfaktor | ×**1.053** bei Controllertyp 10 | AbsMouse-Sensitivität | `0x123f8f0` | mittel |

### 4.1 Default-Stick-Deadzone (Rückgabe 0..32767)

Argument `edi` = `EControllerType` (Tabelle SC `0x2ff79c0`: 4/5 = `controller_neptune`, 10 = `controller_triton`,
30 generic, 31 xbox360, 32 xboxone, 33 ps3, 34 ps4, 38 switch_pro, 45 ps5, 46 xboxelite, 48 ps5_edge,
49 hori_steam, 102 legion_go_s). Sprungtabelle `0xb6bc04`:

| Typ | Wert | Anteil |
|---|---|---|
| ≤ 9 (Deck = 4/5), 11–29, 35–44, 47, 49, > 50 außer 102 | **8192** (`0x2000`) | 25 % |
| 10 (Triton), 34 (PS4) | **4096** (`0x1000`) | 12.5 % |
| 30–33, 45, 46, 48 | 10000 (`0x2710`) | 30.5 % |
| 50 | 4600 (`0x11f8`) | 14 % |
| 102 (Legion Go S) | 2048 (`0x800`) | 6.25 % |
| Sonderpfad PID `0x33dd` (`esi`) | Unterart 1–3 oder 6 → 0; 0x1b → 2048 | – |

(Korrektur zu früheren Notizen: 34 ist `controller_ps4`, nicht Triton.)

### 4.2 Gyro-Defaults (°/s): Speed-Deadzone / Präzisionsgeschwindigkeit (`0x1911b50..0x1911bd4`)

| Typ | Deadzone | Präzision |
|---|---|---|
| Default (inkl. Deck) | 11.0 | 100.0 |
| 10 Triton | **0.75** | **6.0** |
| 34 PS4 | 11.5 | 105.0 |
| Bitmaske `0x813c000000000` (Typen 38–41, 44, 51: Switch-Familie u. a.) | 5.0 | 9.1 |
| Bitmaske `0x1200000000000` (45 PS5, 48 PS5 Edge) | 0.5 | 12.0 |

### 4.3 Antwortkurven

- `ResponseCurve::Evaluate(x)` (`0x12aa5d0`): `t = clamp((x−x0)/(x1−x0), 0, 1)`, Ergebnis `y0 + t^exp·(y1−y0)`.
  Bei `x1 == x0` ist `t` eine Stufe. Die vorzeichenbehaftete Variante (`0x12ab180`) rechnet `sign(x)·Evaluate(|x|)`. — hoch
- `joystick_move` `curve_exponent` 0..4 nutzt Tabellenwerte bei `0xd07870` (−1.5, 1.0, 0.75, 0.5), 5 = custom.
  Custom: `e = custom_curve_exponent/100`; `e ≤ 2.01 → e³`, sonst `−1 − (4−e)^7` (`0x1272440..0x12726df`). — mittel
  (die Zuordnung Index → Tabellenwert ist abgeleitet)

### 4.4 Trackpad-Maus (`absolute_mouse`, `0x123e200..0x1244756`) – mittel

- Sensitivität: Skalierung über `32767 / (Basis × Faktor)`, Triton mit Zusatzfaktor 1.053 (`0x123f8a0..0x123f9a7`).
- Glättung: Divisor 200.0 (`0x123e23a`), also `mouse_smoothing` 20 → 0.1. **Vermutung:** gleitender Mittelwert mit Gewicht `smoothing/200`.
- Trackball-Abklingen pro Tick: `v *= (fp/9000)^(1 + clamp(friction/3,0,1)·0.125)`, Speed-Clamp 9000 (`0x123e510..0x123e5d3`, `0x1244510..0x1244555`).
  Was `fp` genau ist, ist nicht sicher geklärt (**niedrig**).
- Beschleunigung: `e = accel/100`; `e > 2.01 → −1 − (4−e)^7` (`0x1244698..0x1244727`). Die Konstanten 7.0, 3.0 und −1.0 liegen bei `0xd09150`, `0xd091a0`, `0xd08ae8`.
- Rand-/Double-Tap-Erkennung: Skalarprodukt mit (−0.745705, −0.666276) > 31000 und `hypot > 35000` (`0x123f68c..0x123f745`).
- Haptik-Tick bei Mausbewegung: Akkumulator > Schwelle → Haptik-Befehl `Tick`, sonst Puls `int(intensity/3·300)` µs (`0x123e740`).

### 4.5 Gyro (`CGyroTo2DMode`)

- `gyro_natural_sensitivity` (`0x1254140`): `r = val/100 · 0.5625`; `r > 1 → w = min(1, (1/r − 1)/(−0.111111)), t = 0.5·(w+1)`; sonst `t = 0.5·max(0,r)`.
  Mit `t` wird zwischen zwei internen Werten interpoliert. — mittel
- Update (`0x12569f0`): Wert `[+0x5f0]` / 32767, auf 1.0 begrenzt, × π/2 (`0xd08f60` = 1.5707964). Konstante `0xd09110` = 0.000958767 ≈ 1/1043. — hoch (Konstanten), mittel (Semantik)
- Haptik der Gyro-„Tightening“-Rasterung (`0x1255fb0..0x1256123`): bei Wert ≤ 0.01 passiert nichts. Befehl **2 (Click) bei > 0.66**,
  **1 (Tick) bei > 0.33**, sonst 0. Gain-Byte = `int8((3 − intensity)·5·v)`, Amplitude = `int(intensity·v·100)`. Aufruf über vtable +0x170 = Haptik-Befehl `0x82`.
  Gilt für Typ 10 (Triton) und für Typen 100+{0,1,2,20,30} (Maske `0x40100007`). Seiten 3/4 werden übersprungen. — hoch

---

## 5. Button-Bitmasken

### 5.1 Steam-interne 64-Bit-Maske (`sp.js k_eGamepadButtonBitMask`) – hoch

| Bit | Taste | Bit | Taste | Bit | Taste |
|---|---|---|---|---|---|
| 0 | RT voll | 17 | LPad Klick | 32–39 | Macro0..7 |
| 1 | LT voll | 18 | RPad Klick | 41 | GripL oben (L5) |
| 2 | RB | 19 | **LPad Touch** | 42 | GripR oben (R5) |
| 3 | LB | 20 | **RPad Touch** | 44 | CapSense LAux |
| 4 | Y | 22 | L3 | 45 | CapSense RAux |
| 5 | B | 24 | RT soft | 46 | CapSense LStick |
| 6 | X | 25 | LT soft | 47 | CapSense RStick |
| 7 | A | 26 | R3 | 48/49 | Macro 1/2 Finger |
| 8–11 | DPad ↑ → ← ↓ | 27 | CPad Touch | 50 | QAM (…) |
| 12 | Back/View | 28 | CPad Klick | | |
| 13 | Steam/Guide | 29 | Ancillary1 | | |
| 14 | Start/Menu | 30/31 | Achse LStick/RStick | | |
| 15 | GripL unten (L4) | | | | |
| 16 | GripR unten (R4) | | | | |

`gyro_ratchet_button_mask 0x180000` = Bits 19|20 = **LPad- oder RPad-Berührung** (hoch).

### 5.2 Triton-Rohbits `buf[2..5]` (u32 LE) → 64-Bit-Maske (SC LUT `0xb1cea0`, HS7 `.rodata+0xd20`) – hoch

| Bit | Taste | Bit | Taste | Bit | Taste | Bit | Taste |
|---|---|---|---|---|---|---|---|
| 0 | A | 8 | R4 (GripR unten) | 16 | Steam | 24 | CapSense LStick* |
| 1 | B | 9 | RB | 17 | L5 (GripL oben) | 25 | LPad Touch |
| 2 | X | 10 | DPad ↓ | 18 | L4 (GripL unten) | 26 | LPad Klick |
| 3 | Y | 11 | DPad → | 19 | LB | 27 | LT voll |
| 4 | QAM (…) | 12 | DPad ← | 20 | CapSense RStick* | 28 | CapSense RAux* |
| 5 | R3 | 13 | DPad ↑ | 21 | RPad Touch | 29 | CapSense LAux* |
| 6 | Start/Menu | 14 | View/Back | 22 | RPad Klick | 30–31 | – |
| 7 | R5 (GripR oben) | 15 | L3 | 23 | RT voll | | |

`*` = wertet HS7 nicht aus.

### 5.3 Steam Deck Report `0x09` (64 B, `buf[0]=0x01, buf[2]=0x09`) – hoch (HS7 `.rodata+0xe80`, SDL3 `0x1a3bf0..0x1a4200`)

| Byte | Bits |
|---|---|
| 8 | b0 RT · b1 LT · b2 RB · b3 LB · b4 Y · b5 B · b6 X · b7 A |
| 9 | b0..3 DPad ↑→←↓ · b4 View · b5 Steam · b6 Menu · b7 L4 |
| 10 | b0 R4 · b1 LPad Klick · b2 RPad Klick · b3 LPad Touch · b4 RPad Touch · b6 L3 |
| 11 | b2 R3 |
| 13 | b1 L5 · b2 R5 |
| 14 | b2 QAM |
| 16..23 | s16 LPad X, Y, RPad X, Y |
| 24..29 / 30..35 | s16 Accel X, −Z, Y (±2 g) / Gyro X, −Z, Y (±2000 °/s) |
| 44..47 | u16 LT, RT |
| 48..55 | s16 LX, LY, RX, RY (Y wird in SDL3/Linux negiert) |
| 56..59 | u16 **LPad-Druck, RPad-Druck** (SDL3: ÷ 32768) |

---

## 6. Triton-HID-Protokoll

### 6.1 Input-Reports

| ID | Länge | Inhalt | Fundstelle | Sicherheit |
|---|---|---|---|---|
| `0x42` / `0x45` | 54 / 46 B | `[2..5]` Buttons u32 · `[6..9]` LT/RT u16 · `[10..17]` LX,LY,RX,RY s16 · `[18..23]` LPad X,Y s16 + **Druck u16** · `[24..29]` RPad X,Y + **Druck u16** · `[30..33]` IMU-Zeitstempel u32 µs · `[34..45]` Accel X,−Z,Y + Gyro X,−Z,Y s16 | SDL3 `0x1a5440..`, HS7 (Pads `0x12`/`0x18`) | hoch |
| `0x47` (BLE) | – | wie oben, aber `[18..19]` frei, Pads `[20..31]`, Zeitstempel `[32..33]` u16 × 32 µs | SDL3 | hoch |
| `0x43` | ≥ 15 B | `[1]` Ladezustand (SDL3: 1 → Akku, 2 → lädt, 3 → unbekannt, 4 → voll), `[2]` Prozent 0..100, weitere Spannungs-/Stromfelder | SDL3 `.rodata 0x91110`, HS7 `0x2aea..` | hoch / Felder ab `[3]` mittel |
| `0x46` / `0x79` | 2 B | `[1]`: 1 = getrennt, 2 = verbunden (Puck/BLE) | SC `0x11a1e80`, HS7, SDL3 | hoch |
| `0x7B` | – | QoS-/Statusreport | SC `0x11a32d0` | niedrig |

### 6.2 Output-Reports (CSteamControllerTritonPacketAbstraction, vtable `0x2f36628`)

| ID | Länge | Byte-Layout | Fundstelle | Sicherheit |
|---|---|---|---|---|
| `0x80` Rumble | 10 | `[0]=0x80,[1]=?,[2..3]=u16,[4..5]=L u16,[6]=?,[7..8]=R u16,[9]=?`; SDL3 sendet `80 00 00 00 Llo Lhi 00 Rlo Rhi 00` (alle 40 ms erneuert) | SC `0x11a2340`, SDL3 `0x1a5e67` | hoch |
| `0x81` Haptik-Puls | 8 | `[0]=0x81,[1]=Seite,[2..3],[4..5],[6..7]` je u16 (Vermutung: an/aus/Wiederholung wie beim alten Steam Controller) | SC `0x11a2b20` | hoch (Layout) |
| `0x82` Haptik-Befehl | 4 | `[0]=0x82,[1]=Seite,[2]=Befehl,[3]=Gain (int8)`. Gain = `dB − 12`, bei Intensität 1..3 zusätzlich `+ 3·Intensität`. Nicht gesendet, wenn Flag `[+0x500]` gesetzt | SC `0x11a3b90` (vtable-Slot `0x2f36798` = +0x170) | hoch |
| `0x83` | 10 | `[0]=0x83,[1]=Seite,[2]=Typ,[3..4]=u16,[5..6]=u16,[7..9]=0` | SC `0x11a3c10` | hoch (Layout), Semantik unbekannt |
| `0x85` | 4 | `[0]=0x85,[1],[2],[3]` | SC `0x11a3b50` | mittel |
| `0x05` LED | 21 | `[0]=0x05,[1],[2],[3]…` | SC `0x11a7750` | mittel |
| `0x0D` (LED/Status, „`0x2f`“) | 69 (0x45) | `[0]=0x0d,…` | SC `0x11a3c90`, `0x11a6d95` | mittel |
| `0x0B` | 21 | `[0]=0x0b,[1]=Wert` | SC `0x11a61d5` | niedrig |
| `0x01` | 21 | `[0]=0x01,…` | SC `0x11a6927` | niedrig |
| `0x03` IMU-Modus | 17 (0x11) | `[0]=0x03,[1]=0x30,[2..3]=u16`. Wird gesendet, wenn im Settings-Satz die ID 0x57 einen neuen Wert hat | SC `0x11a5d01` | hoch |

Haptik-Befehle (`0x82 [2]`): belegt sind **1 = Tick, 2 = Click** (steamui `EHapticType`, Gyro-Code). 0 wird vom Gyro-Code ebenfalls übergeben
(Wert ≤ 0.33). Weitere IDs (≥ 3) kommen in den untersuchten Aufrufern **nicht** vor. Seiten: 0 = links, 1 = rechts, 2 = **Vermutung** beide.

### 6.3 Feature-Reports

| ID | Layout | Bedeutung | Fundstelle | Sicherheit |
|---|---|---|---|---|
| `0x87` Set Settings | `[0x87, 3·n, (id u8, wert u16 LE) × n]`, n ≤ 5 pro Paket. IDs > 0x55 werden übersprungen. Retry-Schleife mit Timeout (`imul 0xc350`) | Settings schreiben | SC `0x11a6280..0x11a6660` | hoch |
| `0x87` Sonderfälle | ID **0x2e**: Wert wird ersetzt durch `Firmware-Build ≤ 0x5e2f3da8 ? 1 : 0`. ID **0x57** = `0xaa00` setzt das IMU-Flag; ID **0x30** ≠ 0 setzt dasselbe Flag (Deck-Pfad) | | SC `0x11a6800`, `0x11a5cb9`, `0x11a5d8d` | hoch |
| `0x87` aus der Mapping-Logik | ID `0x34 + Seite` mit Wert **10 (0x000A)**; außerdem `0x34` und `0x35` zusammen mit **0xFFFF**. IDs 0x38..0x3B werden ebenfalls erzeugt | SC `0x12b6348..0x12b6441`, `0x12b4f93..0x12b51c0` | hoch (Werte) |
| `0x9F` Ausschalten | `[0x9f, 0x04, 'o','f','f','!']` | | SC `0x11a5db0` | hoch |
| `0x8F` | 10 B | unbekannt | SC `0x11a6040` | niedrig |
| `0xEA` | `[0xea, 0x0d, …, [4]=cl, [5]=r8b]` (15 B) | unbekannt | SC `0x11a5f40` | mittel |
| `0xEB` | `[0xeb, 0x09, …]` (11 B) | unbekannt | SC `0x11a6aff` | mittel |
| `0xC5` | `[0xc5, 0x03, a, b, c]` (5 B), ratenbegrenzt (≥ 0x20 Zeiteinheiten Abstand) | unbekannt | SC `0x11a6c28` | mittel |
| `0xF2` | Set `[id16, sub=0/1/2]`, dann Get mit Länge 0x2b / 0x24 / 0x0b, Antwort beginnt mit `0xf2` | Funk-/Debug-Info (Puck) | SC `0x11a7850..0x11a7ac7` | hoch (Ablauf) |
| `0xAE` / `0xF0` | Puck-Seriennummer / Pairing | | SC `0x1794bbe`, `0x1794f2e` | mittel |
| `0x81` / `0x85` / `0x8E` | Mappings löschen / Default-Mappings / Default-Settings laden | Lizard-Mode | HS7 `steam_set_lizard_mode 0x1280` | hoch |

**Lizard-Mode in HS7 (`0x1280..0x1387`):**
- *Aus* (Parameter 0 oder Flag `[+0x171]==1`): sende `0x81`. Bei Deck/Triton (Quirk-Bits `0x6`) folgt `0x87` mit `{0x09=0, 0x47=0}`, beim alten SC `{0x07=7, 0x08=7}`.
- *An*: sende `0x85`, dann `0x8E`.

Setting-ID-Namen (**Vermutung**, laut öffentlichem `hid-steam.c`-Quelltext, im Binary nicht als Strings vorhanden):
0x07/0x08 = Trackpad-Modus links/rechts (7 = keiner), 0x09 = „USB debug mode“/Lizard-Steuerung, 0x30 = IMU-Modus,
0x34/0x35 = **Trackpad-Klickdruck links/rechts**, 0x47 = Watchdog. Dass Steam auf 0x34+Seite genau einen u16 pro Pad schreibt,
passt zu einer Firmware-Klickschwelle pro Pad.

---

## 7. Abgleich Open Source – Widersprüche

| Thema | SC | SDL3 | HS7/HS6 | Bewertung |
|---|---|---|---|---|
| Triton-Unterstützung | voll | voll (`0x42/0x45/0x47`) | HS7 ja, **HS6 (SteamOS 3.8.14) nein** | Auf SteamOS 3.8.14 bindet der Kernel Triton nicht. hidraw ist dann frei |
| BLE-Report `0x47` | – | geparst | **nicht** in `steam_raw_event` (HS7) | Widerspruch: unter BLE liefert HS7 keine Events |
| Report-ID bei `0x87` | sendet `[0x87,…]`, Länge n+2 | sendet `[0x01, 0x87, …]` mit 64 B | – | Widerspruch im Framing (SDL schreibt die Report-ID voran). Muss am Gerät geprüft werden |
| Pad-Druck Triton | genutzt | genutzt | HS7 liest nur X/Y und überspringt die Druckbytes | Druck nur über hidraw/SDL |
| Bits 20/24/28/29 (CapSense Sticks/Aux) | gemappt | – | ignoriert | Kernel liefert keine Stick-Berührung |
| Linux-Namen | – | – | `BTN_NORTH`=X, `BTN_WEST`=Y | Namen sind gegenüber Xbox vertauscht, die Bits sind trotzdem korrekt |
| Lizard aus (Deck/Triton) | eigene Settings | `0x87` mit ID 0x09 = 0 (`.rodata 0x910f0..`) | `{0x09=0, 0x47=0}` | konsistent |

---

## 8. Direkt umsetzbar in der Mod

1. **Soft-Press-Schwelle:** `pressure ≥ 0.125` (= 4096/32768) auf der SDL-Skala `SDL_GetGamepadTouchpadFinger … pressure 0..1`.
   Empfehlung: Hysterese zum Lösen, z. B. 0.10. Das ist eine eigene Wahl, keine Valve-Konstante.
2. **Long Press 450 ms, Double Tap 190 ms, Repeat 100 ms** (Bereich 10..1000).
3. **Stick-Deadzone Defaults:** Deck 8192/32767 (25 %), Triton 4096 (12.5 %).
4. **Gyro:** Deck Deadzone 11 °/s, Präzision 100 °/s. Triton 0.75 °/s und 6 °/s.
   Defaults für `gyro_to_mouse`: `gyro_natural_sensitivity` 250, `gyro_speed_deadzone` 36, `gyro_precision_speed` 75.
5. **Trackpad-Maus:** Defaults `sensitivity` 100, `friction` 2, `mouse_smoothing` 20 (Divisor 200), `acceleration` 0,
   `haptic_intensity` 1, Triton-Faktor ×1.053. Trackball-Abklingen nach Formel 4.4 (Exponent 1 + 0.125·clamp(friction/3)).
6. **Bildschirmtastatur:** 55-%-Hälften mit 10 % Überlappung, lineare Abbildung mit Y-Invertierung, Rechteck-Hysterese,
   Tick (Intensität 1, 0 dB) beim Tastenwechsel, Tippen beim Drücken, 100 ms Abheben-Timeout, LT/RT als Klick.
7. **Bitmasken:** Tabellen 5.2 (Triton u32) und 5.3 (Deck). Zum Konvertieren in die Steam-64-Bit-Maske Tabelle 5.1 verwenden.
   `0x180000` = „ein Pad berührt“.
8. **Haptik Triton (hidraw, Output):** `82 <Seite 0|1> <1=Tick|2=Click> <gain int8>`. Gain-Formel wie Steam: `dB − 12 (+3·Intensität)`.
   Presets aus steamui: Tick −2 dB, Click −5 dB / −3 dB. Rumble: `80 00 00 00 Llo Lhi 00 Rlo Rhi 00`, alle ≤ 40 ms wiederholen.
9. **Lizard-Mode aus (Triton/Deck):** Feature `81`, danach `87 06 09 00 00 47 00 00`. Zurück: `85`, `8E`.
   Framing (mit/ohne führendes `0x01`) am Gerät testen.
10. **Ausschalten:** Feature `9F 04 6F 66 66 21`.
11. **Akku:** Report `0x43`: Byte 1 Status, Byte 2 Prozent.
12. **Puck:** Reports `0x46`/`0x79` mit Byte 1 = 1 (weg) / 2 (da) für Hotplug.

---

## 9. Offene Fragen (nur am Gerät klärbar)

1. Hat `0x87` ID 0x34/0x35 wirklich die Bedeutung „Klickschwelle links/rechts“? Welche Einheit hat der Wert 10, und heißt 0xFFFF „Firmware-Default“? (**Vermutung**)
2. Welche Report-ID-Rahmung akzeptiert Triton über hidraw (`87 …` vs. `01 87 …`; USB vs. Puck vs. BLE)?
3. Haptik-Befehle `0x82` mit `[2]` = 0 oder ≥ 3: gibt es weitere Effekte? Bewirkt Seite 2 „beide“?
4. Semantik von `0x81`-Feldern (Puls an/aus/Anzahl?) und `0x83`/`0x85`/`0xEA`/`0xEB`/`0xC5`/`0x8F`.
5. Druck-Skala der Triton-Pads (u16; Rohwert bei leichtem Auflegen / bei mechanischem Klick), damit die 0.125-Schwelle passt.
6. Ob Triton im Lizard-Mode selbst Trackpad-Haptik erzeugt und ob `0x87` 0x09=0 die Firmware-Klicks deaktiviert.
7. Exakte Bedeutung von `fp` in der Trackball-Formel sowie die Glättungsform (EMA vs. Ringpuffer; Ctor legt einen Puffer mit `0x14`=20 Einträgen an, `0x12433d8`).
8. Defaults der nicht extrahierten Modi (`switches`, `single_button`, `radial_menu`, `relative_mouse`, `scrollwheel`).
9. BLE (`28de:1303`): Liefert HS7 ohne `0x47`-Support überhaupt Events? Ist hidraw dann exklusiv nutzbar?

---

## 10. Ergänzungen (Messungen am Gerät, SteamlessController, SDL, Mod-Entwicklung)

Quellen: `SLC` = SteamlessController (MIT, `src/steam/SteamController.h/.cpp`, `src/app/TrackpadConfig.h`),
`DESC` = HID-Report-Deskriptor des Testgeräts (`/sys/class/hidraw/hidraw2/device/report_descriptor`,
Steam Controller 2026 über Bluetooth, `28de:1303`, Host-Kernel 7.3.4), `MOD` = Beobachtung in SteamDeck Essentials
(SDL 3.4.14 aus LWJGL 3.4.3, Minecraft 26.3).

### 10.1 Pad-Druck: Skala und Messwerte (beantwortet offene Frage 5 weitgehend)

| Zustand | Rohwert u16 | ÷ 32768 | Quelle | Sicherheit |
|---|---|---|---|---|
| Finger liegt auf | – | **ca. 1 %** (SDL-Wert) | MOD, am Gerät abgelesen | hoch |
| Daumen ruht / gleitet | 259–381 | 0,8–1,2 % | SLC `kPadPressArea`-Kommentar | hoch |
| Druck, den die Firmware **nicht** als Klick meldet | 2840–3921 | 8,7–12,0 % | SLC | hoch |
| Druck, den die Firmware als Klick meldet | 4026–4427 (Loslassen bei ca. 2200) | 12,3–13,5 % | SLC | hoch |
| gehaltener Druck | 4671–14784 | 14–45 % | SLC | hoch |

- SDL normiert den Triton-Pad-Druck wie beim Deck mit **÷ 32768**: Der abgelesene Wert von ca. 1 % passt zu den Rohwerten von SLC. — hoch
- Die Steam-Soft-Press-Schwelle 4096 (12,5 %, Abschnitt 4) liegt damit fast genau am Klickpunkt der Firmware. — mittel (Deutung)
- **Das Klick-Bit der Firmware ist unzuverlässig:** Bleibt der Daumen auf dem Pad, meldet sie nur etwa 9 von 20 Drücken als Klick. SLC erkennt Drücke deshalb über den Druck (Schwelle 1800 ≈ 5,5 %, 2 Frames Entprellung, ohne zweite Löse-Schwelle). — hoch (SLC-Messung)

### 10.2 HID-Report-Deskriptor (Bluetooth, `28de:1303`) – hoch

Angegeben sind die Datenbytes **ohne** Report-ID:

| Richtung | Report-IDs (Länge) |
|---|---|
| Input | `0x40` (5) · `0x41` (8) · `0x42` (53) · `0x43` (14) · `0x44` (5) · `0x45` (45) · `0x79` (1) · `0x7B` (12) |
| Output | `0x80` (9) · `0x81` (7) · `0x82` (3) · `0x83` (9) · `0x84` (8) · `0x85` (3) · `0x86` (3) · `0x87` (63) · `0x88` (63) · `0x89` (63) |
| Feature | nur `0x01` (63) und `0x02` (63) |

Folgen:
- **Widerspruch zu 6.1:** Über Bluetooth deklariert dieser Host/Controller **`0x42`/`0x45`, aber kein `0x47`**. — hoch
- **Framing (offene Frage 2):** Feature-Reports gibt es nur als `0x01`/`0x02`. Steuerbefehle laufen also als
  Feature-Report `[0x01, cmd, len, payload…]`. Das passt zu SDL3 (`01 87 …`) und zu SLC (`FEATURE_REPORT_CMD = 0x01`, Fallback `0x02`).
  `0x87`, `0x88` und `0x89` gibt es zusätzlich als **Output**-Reports mit 63 Byte. Das ist vermutlich der Weg, den
  steamclient.so mit `[0x87, …]` nimmt. — hoch (Deskriptor) / mittel (Zuordnung)
- In 6.2 fehlen noch: Output `0x84` (8), `0x86` (3), `0x88` (63), `0x89` (63); Input `0x40` (5), `0x41` (8). Laut SLC ist `0x44` ein Status-Report
  mit 5 Byte (Akku/Verbindung), `0x79` hat 1 Byte (TBD), `0x7B` 12 Byte (TBD). — mittel

### 10.3 Am Gerät bestätigt

| Fakt | Quelle | Sicherheit |
|---|---|---|
| `82 01 02 00` (Klick auf dem rechten Pad, Gain 0) direkt auf `/dev/hidraw2` per Bluetooth, **ohne** vorangestelltes `0x01`: angenommen, im Pad gespürt | MOD (v1.1.3) | hoch |
| Haptik-Befehl `0x82`: Seite 0 = links, 1 = rechts, **2 = beide**, **3 = keine** (beantwortet offene Frage 3 teilweise). Befehl 1 = Tick, 2 = Click | SLC (gemessen, Kommentar „measured“) | hoch |
| Eine neue Wellenform auf demselben Pad innerhalb von ca. 50 ms wird von der Firmware verworfen | SLC `kTrackpadHapticMinGap` | mittel |
| SDL-Rumble (`SDL_RumbleGamepad`) steuert nur die Motoren in den **Griffen**, nicht die Pad-Aktuatoren | MOD | hoch |
| SDL3 stellt Triton nur bei `SDL_HINT_JOYSTICK_HIDAPI_STEAM=1` als Gamepad bereit. Diagnose in 3.4.14: `type=1 touchpads=2 gyro=true accel=true rumble=true`, Gyro-Rate 248 Hz | MOD (Log) | hoch |
| SDL-Gamepad-Belegung Triton (libsdl-org/SDL #15601): `misc1`=QAM, `paddle1..4`=L4/R4/L5/R5, `touchpad`=Klick linkes Pad (b17), `misc2`=Klick rechtes Pad (b16), `misc3`/`misc4`=Touch linker/rechter Stick (b19/b18), `misc5`/`misc6`=Grip rechts/links (b21/b20). Steam Deck (#15612): gleich, nur ohne misc5/6 | SDL-PRs, String in libSDL3 von LWJGL 3.4.3 | hoch |
| Touchpad-Index in SDL: 0 = links, 1 = rechts; Koordinaten 0..1 mit y = 0 oben | MOD (Kamera/Zeiger funktionieren so) | hoch |
| Prism-Flatpak hat `devices=all`, `/dev/hidraw*` des Controllers trägt die ACL `user:<user>:rw` (udev `uaccess`) | MOD | hoch |

### 10.4 Widersprüche, am Gerät zu klären

| Thema | FINDINGS | SLC | Bewertung |
|---|---|---|---|
| Rücktasten-Bits (5.2) | Bit 7 = R5, 8 = R4, 17 = L5, 18 = L4 | Bit 7 = **R4**, 8 = **R5**, 17 = **L4**, 18 = **L5** | Namenskonvention (oben/unten) oder echter Widerspruch. Am Gerät mit Rohreport prüfen |
| Bits 28/29 | CapSense RAux/LAux | Grip-Sensor rechts/links | konsistent (Aux = Grip) |
| Setting 0x07/0x08 (6.3) | „Trackpad-Modus links/rechts“ (**Vermutung**) | `0x07 = RIGHT_TRACKPAD_MODE`, `0x08 = LEFT_TRACKPAD_MODE` | SLC-Zuordnung bevorzugen |
| IMU-Modus | ID 0x30 | `SETTING_IMU_MODE 0x30`, Wert `0x0018` = Accel + Gyro roh; ohne IMU ist der Report < 46 B | konsistent, Wert ergänzt |
| `gyro_natural_sensitivity` | Default 100 (`absolute_mouse`) bzw. 250 (`gyro_to_mouse`) | – | Ein Layoutwert 200 heißt also nicht „2-fach“. Umrechnung über Formel 4.5 nötig |

### 10.5 SLC-Befehlsbytes (Feature-Report `0x01`, `[0x01, cmd, len, …]`) – hoch (SLC)

`0x80` Set Digital Mappings · `0x81` Clear Digital Mappings (Lizard aus) · `0x82` Get Digital Mappings ·
`0x85` Set Default Mappings (Lizard an) · `0x87` Set Settings · `0x89` Get Settings · `0x8E` Load Default Settings ·
`0x9F` Turn Off (`"off!"`). SLC wiederholt „Lizard aus“ alle 800 ms, weil die Firmware sonst nach einer Pause ohne
Host-Feature-Reports zurückfällt.

### 10.6 Trackpad-Filter anderer Projekte (zum Vergleich mit 4.4)

| Projekt | Verfahren | Parameter |
|---|---|---|
| Steamify 1.3.0 (Fabric, MC 26.3, GPL) | Einschwingzeit + gestufte Glättung + Tightening auf der Finger-Geschwindigkeit (Pad-Breiten/s), EMA zeitbasiert | Settle 40 ms · tight 0,05 · lo 0,15 · hi 0,6 · τ 50 ms |
| mc-deck-native-controls (Forge) | dieselbe gestufte Glättung mit Ringpuffer (32 Einträge) pro Achse, danach Tightening | Schwellen aus `Settings` |
| SLC | keine Filterung, nur Übertrag von Sub-Pixel-Resten | – |
| Steam (4.4) | `mouse_smoothing` 20 / Divisor 200, Ctor legt Puffer mit 20 Einträgen an | passt zu einem Ringpuffer-Mittel wie bei mc-deck (**Vermutung**) |
