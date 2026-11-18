package com.arsop.effect;

import com.arsop.ArsOp;
import com.arsop.glyph.effect.EffectFall;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Enforces Fallen by clamping how far an entity actually moved each tick, on the side that
 * owns that entity's movement: the server for mobs, the client for the local player.
 * This ignores where the motion came from (walking, Speed, knockback, elytra, pearls...).
 * Pinned players are additionally anchored server-side and snapped back if they get away.
 */
@EventBusSubscriber(modid = ArsOp.MODID)
public final class FallenMovement {
    private FallenMovement() {}

    private static final Map<Player, Vec3> ANCHORS = Collections.synchronizedMap(new WeakHashMap<>());


    @SubscribeEvent
    public static void onTickPre(EntityTickEvent.Pre event) {
        // Pinned players lie flat on the ground (both sides, so client and server agree on the hitbox).
        if (!(event.getEntity() instanceof Player player)) return;
        // Stateless: whenever we're not pinned, release our forced crawl pose.
        if (FallenEffect.isPinned(player)) {
            player.setForcedPose(Pose.SWIMMING);
        } else if (player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
    }

    @SubscribeEvent
    public static void onTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        boolean client = entity.level().isClientSide;
        if (!client && !FallenEffect.isFallen(entity) && entity.getPersistentData().contains(FallenEffect.CASTER_KEY)) {
            entity.getPersistentData().remove(FallenEffect.CASTER_KEY);
        }

        if (!client && entity instanceof ServerPlayer sp) {
            anchorPinnedPlayer(sp);
            return; // clients own player movement; the local client clamps below
        }
        if (!FallenEffect.isFallen(entity)) return;
        boolean ownsMovement = client ? (entity instanceof Player p && p.isLocalPlayer()) : !(entity instanceof Player);
        if (ownsMovement) clamp(entity, FallenEffect.isPinned(entity));
    }

    private static void clamp(Entity e, boolean pinned) {
        double maxStep = pinned ? 0.0 : crawlSpeed();
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        double len = Math.sqrt(dx * dx + dz * dz);
        double scale = len > maxStep ? (len > 1.0E-7 ? maxStep / len : 0.0) : 1.0;
        double ny = pinned ? Math.min(e.getY(), e.yo) : e.getY(); // pinned: can only go down
        if (scale < 1.0 || ny != e.getY()) {
            e.setPos(e.xo + dx * scale, ny, e.zo + dz * scale);
        }
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * scale, pinned ? Math.min(v.y, 0) : v.y, v.z * scale);
    }

    private static void anchorPinnedPlayer(ServerPlayer sp) {
        if (!FallenEffect.isPinned(sp)) {
            ANCHORS.remove(sp);
            return;
        }
        Vec3 anchor = ANCHORS.computeIfAbsent(sp, p -> p.position());
        double ddx = sp.getX() - anchor.x, ddz = sp.getZ() - anchor.z;
        if (ddx * ddx + ddz * ddz > 0.25) {
            sp.connection.teleport(anchor.x, Math.min(sp.getY(), anchor.y), anchor.z, sp.getYRot(), sp.getXRot());
        }
    }

    private static double crawlSpeed() {
        var v = EffectFall.INSTANCE.CRAWL_SPEED;
        return v == null ? 0.03 : v.get();
    }
}
