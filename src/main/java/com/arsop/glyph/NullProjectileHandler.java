package com.arsop.glyph;

import com.arsop.ArsOp;
import com.arsop.entity.NullEntity;
import com.arsop.glyph.effect.EffectNull;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.common.entity.EntityOrbitProjectile;
import com.hollingsworth.arsnouveau.common.entity.EntityProjectileSpell;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentSplit;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectOrbit;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectWall;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.arsop.entity.NullCharge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Swaps Ars spell projectiles for NULLs as they spawn:
 * <ul>
 *   <li>Projectile -> Null (players): a NULL charges above the head, growing while right click is held, and
 *       flies where they look on release. Other casters (turrets etc.) throw it at full size straight away.</li>
 *   <li>Orbit -> Null: 3 mini NULLs (+1 per Split) circle the caster.</li>
 *   <li>Projectile -> Wall -> Null: a 5 wide, 3 tall wall of 1x1 NULLs flies forward together.</li>
 * </ul>
 * Only when Null is the first effect (after Orbit/Wall).
 */
@EventBusSubscriber(modid = ArsOp.MODID)
public final class NullProjectileHandler {
    private NullProjectileHandler() {}

    public static final int WALL_WIDTH = 5;
    public static final int WALL_HEIGHT = 3;
    /** Center-to-center distance: 1x1 NULLs with a 1 block gap between them. */
    public static final double WALL_SPACING = 2.0;

    /** Mini NULLs per Orbit cast before Split. */
    public static final int ORBIT_BASE_COUNT = 3;
    /** Owner -> game time of the last Orbit -> Null cast; Ars spawns several orbiters per cast, we only want one ring. */
    private static final Map<UUID, Long> LAST_ORBIT_CAST = new HashMap<>();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof EntityProjectileSpell proj)) return;
        SpellResolver resolver = proj.resolver();
        if (resolver == null || resolver.spell == null) return;

        boolean orbit = proj instanceof EntityOrbitProjectile;
        List<AbstractSpellPart> parts = resolver.spell.unsafeList();
        int first = firstEffectIndex(parts, orbit);
        if (first < 0) return;
        boolean wall = !orbit && parts.get(first) instanceof EffectWall;
        int nullIndex = wall ? nextEffectIndex(parts, first) : first;
        if (nullIndex < 0 || parts.get(nullIndex) != EffectNull.INSTANCE) return;

        LivingEntity owner = proj.getOwner() instanceof LivingEntity l ? l
                : resolver.spellContext != null ? resolver.spellContext.getUnwrappedCaster() : null;
        if (owner == null) return;

        EffectNull.Params params = EffectNull.Params.of(parts, nullIndex);
        event.setCanceled(true);

        if (orbit) {
            long now = proj.level().getGameTime();
            Long last = LAST_ORBIT_CAST.put(owner.getUUID(), now);
            if (last != null && last == now) return; // rest of the same cast
            int count = ORBIT_BASE_COUNT + countSplit(parts, nullIndex);
            for (int i = 0; i < count; i++) {
                NullEntity.spawnOrbiting(proj.level(), owner, params, (float) (2 * Math.PI * i / count));
            }
            return;
        }

        Vec3 dir = proj.getDeltaMovement();
        if (dir.lengthSqr() < 1e-6) dir = owner.getLookAngle();
        dir = dir.normalize();

        if (wall) {
            spawnWall(proj, owner, params, dir);
            return;
        }

        if (owner instanceof ServerPlayer player) {
            NullCharge.begin(player, params);
            return;
        }
        // Start just ahead of the hand so it doesn't sit inside the caster's face.
        Vec3 start = proj.position().add(dir.scale(params.radius() + 0.5));
        NullEntity.spawn(proj.level(), owner, start, dir.scale(params.speed()), params);
    }

    /** 1x1 NULLs in a grid facing {@code dir}, all flying the same way. */
    private static void spawnWall(EntityProjectileSpell proj, LivingEntity owner, EffectNull.Params params, Vec3 dir) {
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) {
            // Straight up/down: use the caster's facing for the wall's width.
            float yaw = owner.getYRot() * ((float) Math.PI / 180f);
            right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        }
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();
        // Bottom row at the caster's eye level, rows stacking upward.
        Vec3 base = owner.getEyePosition().add(dir.scale(1.0));
        Vec3 velocity = dir.scale(params.speed());
        for (int w = 0; w < WALL_WIDTH; w++) {
            for (int h = 0; h < WALL_HEIGHT; h++) {
                double x = (w - (WALL_WIDTH - 1) / 2.0) * WALL_SPACING;
                double y = h * WALL_SPACING;
                Vec3 at = base.add(right.scale(x)).add(up.scale(y));
                NullEntity.spawn(proj.level(), owner, at, velocity, params, 0.5f, false);
            }
        }
        proj.level().playSound(null, base.x, base.y, base.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5f, 0.5f);
    }

    private static int nextEffectIndex(List<AbstractSpellPart> parts, int after) {
        for (int i = after + 1; i < parts.size(); i++) if (parts.get(i) instanceof AbstractEffect) return i;
        return -1;
    }

    private static int countSplit(List<AbstractSpellPart> parts, int nullIndex) {
        int n = 0;
        for (int i = 0; i < nullIndex; i++) if (parts.get(i) == AugmentSplit.INSTANCE) n++;
        return n;
    }

    /** Index of the first effect, skipping the Orbit glyph itself when {@code skipOrbit}. */
    private static int firstEffectIndex(List<AbstractSpellPart> parts, boolean skipOrbit) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i) instanceof AbstractEffect effect) {
                if (skipOrbit && effect instanceof EffectOrbit) continue;
                return i;
            }
        }
        return -1;
    }
}
