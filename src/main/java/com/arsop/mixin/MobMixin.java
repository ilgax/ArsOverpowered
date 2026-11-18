package com.arsop.mixin;

import com.arsop.effect.FallenEffect;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A pinned (Fallen II) mob's AI does not run at all: no goals, no brain, no targeting,
 * so it never starts an attack and never sends a swing/attack animation packet.
 * Single-target Fallen I mobs keep their AI (they can still fight, just weaker).
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void arsop$freezeAi(CallbackInfo ci) {
        if (FallenEffect.isPinned((Mob) (Object) this)) ci.cancel();
    }
}
