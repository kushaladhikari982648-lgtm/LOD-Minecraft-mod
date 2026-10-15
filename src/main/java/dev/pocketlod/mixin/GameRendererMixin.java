package dev.pocketlod.mixin;

import dev.pocketlod.PocketLodClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method="getFarPlaneDistance",at=@At("RETURN"),cancellable=true)
    private void pocketlod$farPlane(CallbackInfoReturnable<Float> cir) {
        var engine=PocketLodClient.engine;
        if (engine!=null && engine.config().enabled && !engine.shaderPackActive())
            cir.setReturnValue(Math.max(cir.getReturnValue(),engine.config().lodRadiusChunks*32f+128f));
    }
}
