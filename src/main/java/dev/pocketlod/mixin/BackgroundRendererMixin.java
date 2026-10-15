package dev.pocketlod.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pocketlod.PocketLodClient;
import net.minecraft.client.render.*;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Inject(method="applyFog",at=@At("TAIL"))
    private static void pocketlod$fog(Camera camera,BackgroundRenderer.FogType type,float distance,boolean thick,float delta,CallbackInfo ci) {
        var engine=PocketLodClient.engine;
        if (engine==null || !engine.renderer().active() || thick || type!=BackgroundRenderer.FogType.FOG_TERRAIN
                || camera.getSubmersionType()!=CameraSubmersionType.NONE) return;
        if (camera.getFocusedEntity() instanceof LivingEntity living && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS))) return;
        float fog=engine.renderer().fogDistance();
        RenderSystem.setShaderFogStart(fog*0.58f); RenderSystem.setShaderFogEnd(fog*1.02f);
    }
}
