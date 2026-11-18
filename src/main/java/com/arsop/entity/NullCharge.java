package com.arsop.entity;

import com.arsop.ArsOp;
import com.arsop.glyph.effect.EffectNull;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side bookkeeping for hold-to-grow NULLs.
 * <ul>
 *   <li>A Projectile -> Null cast first waits {@link #HOLD_THRESHOLD} ticks.</li>
 *   <li>Released before that (a click): an uncharged 1x1 NULL is thrown straight from the hand.</li>
 *   <li>Still held: a NULL starts charging above the head (see {@link NullEntity#spawnCharging}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = ArsOp.MODID)
public final class NullCharge {
    private NullCharge() {}

    /** Ticks right click must stay down after casting for it to count as holding (0.3 s). */
    public static final int HOLD_THRESHOLD = 6;

    private static final Map<UUID, Boolean> HELD = new ConcurrentHashMap<>();
    private static final Map<UUID, NullEntity> CHARGING = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private static final class Pending {
        final EffectNull.Params params;
        int ticks;

        Pending(EffectNull.Params params) {
            this.params = params;
        }
    }

    public static void setHeld(Player player, boolean held) {
        HELD.put(player.getUUID(), held);
    }

    public static boolean isHeld(UUID owner) {
        return HELD.getOrDefault(owner, false);
    }

    /** Called on a Projectile -> Null cast by a player. */
    public static void begin(ServerPlayer player, EffectNull.Params params) {
        if (isBusy(player.getUUID())) return;
        PENDING.put(player.getUUID(), new Pending(params));
    }

    public static void start(UUID owner, NullEntity n) {
        CHARGING.put(owner, n);
    }

    public static void stop(UUID owner, NullEntity n) {
        CHARGING.remove(owner, n);
    }

    /** True while this caster has a NULL waiting to be decided or growing above their head. */
    public static boolean isBusy(UUID owner) {
        return PENDING.containsKey(owner) || isCharging(owner);
    }

    public static boolean isCharging(UUID owner) {
        NullEntity n = CHARGING.get(owner);
        if (n == null) return false;
        if (n.isRemoved() || !n.isCharging()) {
            CHARGING.remove(owner, n);
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            Pending p = e.getValue();
            p.ticks++;
            if (!isHeld(e.getKey())) {
                it.remove();
                throwFromHand(player, p.params);
            } else if (p.ticks >= HOLD_THRESHOLD) {
                it.remove();
                NullEntity.spawnCharging(player.level(), player, p.params);
            }
        }
    }

    /** A click: no charging, a 1x1 NULL leaves the hand right away. */
    private static void throwFromHand(ServerPlayer player, EffectNull.Params params) {
        EffectNull.Params small = params.withAoe(0);
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(dir.scale(small.radius() + 0.5));
        NullEntity.spawn(player.level(), player, start, dir.scale(small.speed()), small);
    }
}
