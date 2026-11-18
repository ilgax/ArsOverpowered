package com.arsop.effect;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Decides which outgoing sound packets a Fallen player still receives: their own, and the caster's.
 * Positional sound packets carry no source entity, so "from the caster" means within
 * {@link #CASTER_RADIUS} blocks of the caster. Entity-attached sounds are matched by entity id.
 * With no known caster (e.g. applied via /effect), only the player's own sounds get through.
 */
public final class FallenSilence {
    private FallenSilence() {}

    public static final double CASTER_RADIUS = 2.5;
    /** The Fallen player's own sounds (footsteps, swings, eating, hurt) are kept. */
    public static final double OWN_RADIUS = 2.0;

    public static boolean shouldDrop(ServerPlayer player, Packet<?> packet) {
        boolean soundPacket = packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundSoundEntityPacket
                || packet instanceof ClientboundLevelEventPacket;
        if (!soundPacket || player == null || player.getServer() == null) return false;
        if (!player.getServer().isSameThread() || !FallenEffect.isFallen(player)) return false;

        Entity caster = FallenEffect.getCaster(player);
        if (packet instanceof ClientboundSoundEntityPacket p) {
            boolean ownOrCaster = p.getId() == player.getId() || (caster != null && p.getId() == caster.getId());
            return !ownOrCaster;
        }
        Vec3 pos = packet instanceof ClientboundSoundPacket p
                ? new Vec3(p.getX(), p.getY(), p.getZ())
                : Vec3.atCenterOf(((ClientboundLevelEventPacket) packet).getPos());
        return !isNear(player, pos, OWN_RADIUS) && !(caster != null && isNear(caster, pos, CASTER_RADIUS));
    }

    public static boolean isNear(Entity entity, Vec3 pos, double radius) {
        return entity.getBoundingBox().getCenter().distanceToSqr(pos) <= radius * radius;
    }
}
