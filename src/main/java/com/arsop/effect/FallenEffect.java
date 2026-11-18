package com.arsop.effect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

public class FallenEffect extends MobEffect {
    public static final int CRUSHED = 0;
    public static final int PINNED = 1;
    /** Persistent-data key holding the UUID of whoever applied Fallen with the spell. */
    public static final String CASTER_KEY = "ars_op_fallen_caster";
    /** Fallen I attack speed as a fraction of normal (players: attack-speed attribute; melee mobs: goal cooldown). */
    public static final double CRUSHED_ATTACK_SPEED = 0.42;
    public static final double CRUSHED_COOLDOWN_MULT = 1.0 / CRUSHED_ATTACK_SPEED; // ~2.38x

    public FallenEffect() {
        super(MobEffectCategory.HARMFUL, 0x000000);
    }

    public static boolean isFallen(LivingEntity entity) {
        return entity.hasEffect(ArsOpEffects.FALLEN);
    }

    public static boolean isPinned(LivingEntity entity) {
        MobEffectInstance inst = entity.getEffect(ArsOpEffects.FALLEN);
        return inst != null && inst.getAmplifier() >= PINNED;
    }

    /** The entity that cast Fall on this one, if known and loaded (server only). */
    public static @Nullable Entity getCaster(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        CompoundTag data = entity.getPersistentData();
        if (!data.hasUUID(CASTER_KEY)) return null;
        return level.getEntity(data.getUUID(CASTER_KEY));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /** Server-side upkeep; actual movement is clamped in FallenMovement. */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof Player p && p.isFallFlying()) p.stopFallFlying();
        if (amplifier >= PINNED) {
            if (entity.isPassenger()) entity.stopRiding();
            if (entity.isUsingItem()) entity.stopUsingItem(); // e.g. a drawn bow
            if (entity instanceof Creeper creeper) creeper.setSwellDir(-1); // AI is frozen; defuse
            if (entity instanceof Mob mob) mob.setAggressive(false);
            if (entity instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setJumping(false);
            }
            if (entity instanceof Player p && p.getAbilities().flying) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
        }
        return true;
    }
}
