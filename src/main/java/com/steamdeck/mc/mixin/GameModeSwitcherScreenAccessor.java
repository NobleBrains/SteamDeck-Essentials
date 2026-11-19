package com.steamdeck.mc.mixin;

import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameModeSwitcherScreen.class)
public interface GameModeSwitcherScreenAccessor {
    @Invoker("switchToHoveredGameMode")
    void invokeSwitchToHoveredGameMode();
}
