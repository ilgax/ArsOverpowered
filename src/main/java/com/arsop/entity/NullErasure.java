package com.arsop.entity;

import com.arsop.ArsOp;
import com.arsop.mixin.LivingEntityAccessor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Discarding NULLs erase what they touch at the very start of the level tick, before any entity
 * (or the armor/items it carries) ticks. Players are killed without going through hurt(), and a
 * cancelled death event is overruled, so damage-immunity armor and totem-like effects can't stop it.
 */
@EventBusSubscriber(modid = ArsOp.MODID)
public final class NullErasure {
    /** The player a NULL is killing right now; its death event may not be cancelled. */
    @Nullable private static Entity killing;

    private NullErasure() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelTickStart(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        List<? extends NullEntity> nulls = server.getEntities(EntityTypeTestHolder.NULLS, n -> n.isAlive() && n.discardsNow());
        for (NullEntity n : nulls) n.eraseTouching(server);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDeath(LivingDeathEvent event) {
        if (killing != null && event.getEntity() == killing) event.setCanceled(false);
    }

    static void erase(ServerLevel server, Entity e, Entity cause, @Nullable Entity owner) {
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator() || p.isDeadOrDying()) return;
            DamageSource src = new DamageSource(server.damageSources().genericKill().typeHolder(), cause, owner);
            p.getEntityData().set(LivingEntityAccessor.arsop$healthId(), 0f);
            killing = p;
            try {
                p.die(src);
            } finally {
                killing = null;
            }
            return;
        }
        server.sendParticles(ParticleTypes.SQUID_INK, e.getX(), e.getY(0.5), e.getZ(), 12,
                e.getBbWidth() / 2, e.getBbHeight() / 2, e.getBbWidth() / 2, 0.02);
        e.discard();
        // Some entities override remove() to refuse; setRemoved is final and does the real unloading.
        if (!e.isRemoved()) e.setRemoved(Entity.RemovalReason.DISCARDED);
    }

    private static final class EntityTypeTestHolder {
        static final net.minecraft.world.level.entity.EntityTypeTest<Entity, NullEntity> NULLS =
                net.minecraft.world.level.entity.EntityTypeTest.forClass(NullEntity.class);
    }
}
