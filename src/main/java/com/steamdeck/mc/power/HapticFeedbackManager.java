package com.steamdeck.mc.power;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.SteamDeckInputManager;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLJoystick;

/**
 * Handles precision haptic rumble on the Steam Deck's dual trackpad actuators.
 * Routes left motor to the Left Trackpad actuator and right motor to the Right Trackpad actuator.
 */
public class HapticFeedbackManager {
    private static final HapticFeedbackManager INSTANCE = new HapticFeedbackManager();

    public static HapticFeedbackManager getInstance() {
        return INSTANCE;
    }

    private HapticFeedbackManager() {}

    /**
     * Triggers a crisp directional haptic pulse on the trackpad corresponding to the scroll direction.
     * @param direction -1 for Left Trackpad, +1 for Right Trackpad
     */
    public void triggerHotbarHaptic(int direction) {
        if (!ModConfig.getInstance().enableHotbarHaptics) return;
        triggerHaptic(direction < 0, direction > 0, (short) 38000, 35);
    }

    /**
     * Triggers haptic feedback with side selection, intensity and duration.
     */
    public void triggerHaptic(boolean leftSide, boolean rightSide, short intensity, int durationMs) {
        SteamDeckInputManager input = SteamDeckInputManager.getInstance();
        long gamepad = input.getActiveGamepadHandle();
        long joystick = input.getActiveJoystickHandle();

        short left = leftSide ? intensity : 0;
        short right = rightSide ? intensity : 0;

        try {
            if (gamepad != 0) {
                SDLGamepad.SDL_RumbleGamepad(gamepad, left, right, durationMs);
            } else if (joystick != 0) {
                SDLJoystick.SDL_RumbleJoystick(joystick, left, right, durationMs);
            }
        } catch (Throwable t) {
            SteamDeckEssentials.LOGGER.debug("Haptic rumble dispatch error", t);
        }
    }
}
