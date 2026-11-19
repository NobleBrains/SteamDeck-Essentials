package com.steamdeck.mc.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.steamdeck.mc.config.ModConfig;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Built-in controller layouts.
 *
 * <h2>"SteamDeck Minecraft Controls" (Steam Workshop 3624927898, controller_triton)</h2>
 * 1:1 translation of the Steam Input layout {@code steamdeck minecraft controls_0.vdf}.
 * Translation rules:
 * <ul>
 *     <li>{@code key_press} of a Minecraft default key becomes the matching action
 *     (W/A/S/D/arrows = movement, SPACE = jump, LEFT_SHIFT = sneak, LEFT_CONTROL = sprint, E = inventory,
 *     ESCAPE = pause/close). Any other key (RETURN, KEYPAD_1/2, F2, F3) stays a literal key.</li>
 *     <li>{@code xinput_button} was passed through to this mod under Steam, so those buttons keep the
 *     mod's own default action.</li>
 *     <li>{@code controller_action} (Steam overlay: SHOW_KEYBOARD, game clip, SCREENSHOT) has no
 *     Steam-free equivalent; SCREENSHOT becomes Minecraft's F2 screenshot, the clip is dropped.</li>
 *     <li>Steam had one action set; the mod's IN_GAME and AIMING sets get the same bindings, MENUS gets
 *     the same keys where they make sense in screens.</li>
 * </ul>
 */
public final class ControllerPresets {
    public static final String WORKSHOP_MINECRAFT = "SteamDeck Minecraft Controls";

    private ControllerPresets() {}

    public static void applyWorkshopMinecraft(ModConfig c) {
        Map<DeckButton, ActionBinding> game = new EnumMap<>(DeckButton.class);

        // group 0 "button_diamond" (four_buttons)
        game.put(DeckButton.A, key(InputConstants.KEY_RETURN, "Enter"));                       // key_press RETURN
        game.put(DeckButton.B, key(InputConstants.KEY_NUMPAD1, "Numpad 1"));                   // key_press KEYPAD_1 (+ Steam keyboard)
        game.put(DeckButton.X, key(InputConstants.KEY_NUMPAD2, "Numpad 2")
                .withRepeat(200, 50).withHaptic(true));                                       // KEYPAD_2, hold_repeats 200/50, haptic 1
        game.put(DeckButton.Y, key(InputConstants.KEY_F3, "F3"));                              // key_press F3

        // group 7 "switch"
        game.put(DeckButton.SELECT, act(DeckAction.INVENTORY));                                 // button_escape (View) = E
        game.put(DeckButton.START, act(DeckAction.PAUSE));                                      // button_menu = ESCAPE
        game.put(DeckButton.LB, act(DeckAction.ZOOM));                                          // xinput shoulder_left -> mod default
        game.put(DeckButton.RB, act(DeckAction.PICK_BLOCK));                                    // xinput shoulder_right -> mod default
        game.put(DeckButton.L5, act(DeckAction.SNEAK));                                         // button_back_left = LEFT_SHIFT (hold)
        game.put(DeckButton.R5, act(DeckAction.NONE));                                          // button_back_right = ts_none
        game.put(DeckButton.L4, act(DeckAction.MOUSE_SCROLL_UP).withRepeat(100, 150).withHaptic(true));   // mouse_wheel SCROLL_UP
        game.put(DeckButton.R4, act(DeckAction.MOUSE_SCROLL_DOWN).withRepeat(100, 150).withHaptic(true)); // mouse_wheel SCROLL_DOWN

        // groups 4/5 triggers
        game.put(DeckButton.LT, act(DeckAction.USE));                                           // mouse_button RIGHT
        game.put(DeckButton.RT, act(DeckAction.ATTACK));                                        // mouse_button LEFT

        // group 23 left joystick (dpad mode): N = Ctrl + Up, click = LEFT_SHIFT toggle
        game.put(DeckButton.L3, act(DeckAction.SNEAK_TOGGLE));
        // group 29 right joystick (joystick_mouse), click = mouse MIDDLE
        game.put(DeckButton.R3, act(DeckAction.MOUSE_MIDDLE_CLICK));

        // group 1 left trackpad = click D-Pad (W/A/S/D, N = Ctrl + W) -> leftTrackpadMode DPAD_MOVE
        game.put(DeckButton.LEFT_TRACKPAD, act(DeckAction.NONE));
        // group 21 right trackpad (absolute_mouse), Soft_Press = SPACE
        game.put(DeckButton.RIGHT_TRACKPAD, act(DeckAction.JUMP));

        // group 9 dpad: N = game clip (n/a), S = Steam screenshot -> F2, E/W = xinput -> mod defaults
        game.put(DeckButton.DPAD_UP, act(DeckAction.NONE).withHaptic(true));
        game.put(DeckButton.DPAD_DOWN, key(InputConstants.KEY_F2, "Screenshot"));
        game.put(DeckButton.DPAD_LEFT, act(DeckAction.TOGGLE_PERSPECTIVE).withHaptic(true));
        game.put(DeckButton.DPAD_RIGHT, act(DeckAction.OPEN_CHAT).withHaptic(true));

        game.put(DeckButton.LEFT_STICK_TOUCH, act(DeckAction.NONE));
        game.put(DeckButton.RIGHT_STICK_TOUCH, act(DeckAction.NONE));

        // Menus: same physical keys, mapped to what they do inside screens
        Map<DeckButton, ActionBinding> menus = new EnumMap<>(game);
        menus.put(DeckButton.LT, act(DeckAction.MOUSE_RIGHT_CLICK));                            // mouse RIGHT
        menus.put(DeckButton.RT, act(DeckAction.MOUSE_LEFT_CLICK));                             // mouse LEFT
        menus.put(DeckButton.SELECT, key(InputConstants.KEY_E, "E"));                           // E closes the inventory
        menus.put(DeckButton.START, act(DeckAction.CLOSE_SCREEN));                              // ESCAPE
        menus.put(DeckButton.LB, act(DeckAction.HOTBAR_PREV));                                  // xinput -> mod MENUS default
        menus.put(DeckButton.RB, act(DeckAction.HOTBAR_NEXT));
        menus.put(DeckButton.L5, act(DeckAction.SNEAK));                                        // held Shift = shift-click
        menus.put(DeckButton.L3, act(DeckAction.NONE));
        menus.put(DeckButton.DPAD_LEFT, act(DeckAction.NONE));                                  // xinput -> mod menu navigation
        menus.put(DeckButton.DPAD_RIGHT, act(DeckAction.NONE));
        menus.put(DeckButton.DPAD_UP, act(DeckAction.NONE));

        for (ActionSet set : ActionSet.values()) {
            Map<DeckButton, ActionBinding> src = set == ActionSet.MENUS ? menus : game;
            Map<String, String> serialized = new HashMap<>();
            for (DeckButton b : DeckButton.values()) {
                serialized.put(b.name(), src.getOrDefault(b, act(DeckAction.NONE)).serialize());
            }
            c.actionSets.put(set.name(), serialized);
        }

        // Sticks, pads & gyro
        c.enableRightStickRadialMenu = false;     // right joystick = joystick_mouse (camera)
        c.enable8WayStickMapping = true;          // left joystick in D-Pad mode
        c.sprintOnForward = true;                 // north = LEFT_CONTROL + W / UP
        c.leftTrackpadMode = "DPAD_MOVE";         // group 1: D-Pad (requires click)
        c.leftTrackpadClickMode = "HARDWARE";
        c.rightTrackpadClickMode = "PRESSURE";    // group 21: Soft_Press
        c.rightTrackpadClickPressure = 0.30f;
        c.nativeTrackpadCamera = true;            // absolute_mouse
        c.trackpadSensitivity = 1.2f;             // sensitivity 120
        c.enableGyroAiming = true;                // group 33 gyro_to_mouse
        c.gyroActivation = "TRACKPAD_TOUCH";      // ratchet mask 0x180000 = left/right pad finger down, "any"
        c.gyroSensitivityX = 2.0f;                // gyro_natural_sensitivity 200
        c.gyroSensitivityY = 2.0f;
        c.save();
    }

    private static ActionBinding key(int code, String name) {
        return new ActionBinding(code, name);
    }

    private static ActionBinding act(DeckAction action) {
        return new ActionBinding(action);
    }
}
