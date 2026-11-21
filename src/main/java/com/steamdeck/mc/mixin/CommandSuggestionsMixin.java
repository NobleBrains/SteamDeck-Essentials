package com.steamdeck.mc.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Positions command auto-completion suggestions below the relocated top chat input bar.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    @Final
    private EditBox input;

    @ModifyConstant(
            method = "showSuggestions",
            constant = @Constant(intValue = 72)
    )
    private int modifySuggestionsTopY(int original) {
        if (!com.steamdeck.mc.gui.ChatLayout.isTop()) return original;
        return this.input != null ? this.input.getY() + this.input.getHeight() + (this.input.isBordered() ? 2 : 1) : original;
    }

    @ModifyConstant(
            method = "extractUsage",
            constant = @Constant(intValue = 72)
    )
    private int modifyUsageTopY(int original) {
        if (!com.steamdeck.mc.gui.ChatLayout.isTop()) return original;
        return this.input != null ? this.input.getY() + this.input.getHeight() + (this.input.isBordered() ? 2 : 1) : original;
    }
}
