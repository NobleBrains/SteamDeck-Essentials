package com.steamdeck.mc.input;

import com.steamdeck.mc.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Handles server- and anticheat-safe mapping of the analog left stick into vanilla
 * W, A, S, D KeyMapping presses.
 */
public class DiscreteStickMapper {
    private Direction8 currentDirection = Direction8.NONE;

    /**
     * Updates movement keys based on raw stick input.
     */
    public void updateStickInput(Minecraft client, float stickX, float stickY) {
        if (client == null || client.player == null) return;

        // If a GUI/screen is active, do not drive player movement
        if (client.gui != null && client.gui.screen() != null) {
            resetKeys(client.options);
            return;
        }

        ModConfig config = ModConfig.getInstance();
        if (!config.enable8WayStickMapping) return;

        Direction8 newDirection = Direction8.fromAnalog(stickX, stickY, config.stickDeadzone);

        if (newDirection != currentDirection) {
            currentDirection = newDirection;
            applyDirectionToKeys(client.options, newDirection);
        }
    }

    private void applyDirectionToKeys(Options options, Direction8 dir) {
        options.keyUp.setDown(dir.forward);
        options.keyDown.setDown(dir.backward);
        options.keyLeft.setDown(dir.left);
        options.keyRight.setDown(dir.right);
    }

    public void resetKeys(Options options) {
        if (currentDirection != Direction8.NONE) {
            currentDirection = Direction8.NONE;
            if (options != null) {
                options.keyUp.setDown(false);
                options.keyDown.setDown(false);
                options.keyLeft.setDown(false);
                options.keyRight.setDown(false);
            }
        }
    }
}
