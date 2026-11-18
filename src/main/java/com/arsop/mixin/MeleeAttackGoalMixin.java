package com.arsop.mixin;

import com.arsop.effect.FallenEffect;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fallen I melee mobs attack at {@link FallenEffect#CRUSHED_ATTACK_SPEED} of their normal rate. */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin {
    @Shadow @Final protected PathfinderMob mob;
    @Shadow private int ticksUntilNextAttack;

    @Inject(method = "resetAttackCooldown", at = @At("TAIL"))
    private void arsop$slowAttacks(CallbackInfo ci) {
        if (FallenEffect.isFallen(mob)) {
            ticksUntilNextAttack = (int) Math.ceil(ticksUntilNextAttack / FallenEffect.CRUSHED_ATTACK_SPEED);
        }
    }
}
