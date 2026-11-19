package com.steamdeck.mc.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int getLeftPos();

    @Accessor("topPos")
    int getTopPos();

    @Accessor("hoveredSlot")
    Slot getHoveredSlot();

    @Accessor("hoveredSlot")
    void setHoveredSlot(Slot slot);

    @Invoker("getHoveredSlot")
    Slot invokeGetHoveredSlot(double mouseX, double mouseY);
}
