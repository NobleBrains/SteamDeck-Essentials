package com.steamdeck.mc.mixin;

import com.steamdeck.mc.power.DynamicChunkThreading;
import net.minecraft.TracingExecutor;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts the chunk builder background executor inside LevelRenderer to apply
 * battery-saving concurrency limits when running on Steam Deck battery power.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Redirect(
            method = "invalidateCompiledGeometry",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;backgroundExecutor()Lnet/minecraft/TracingExecutor;"
            )
    )
    private TracingExecutor redirectChunkBackgroundExecutor() {
        TracingExecutor defaultExecutor = Util.backgroundExecutor();
        return DynamicChunkThreading.wrapExecutor(defaultExecutor);
    }
}
