package com.arsop.possess;

import com.arsop.ArsOp;
import com.arsop.corruption.Corruption;
import com.arsop.network.PossessMovePayload;
import com.arsop.network.PossessPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dream-walk: the player's view moves into a mob and drives it. The player's body stays frozen where it stood and
 * snaps back the moment it is hurt. Ends on sneak, use, timeout, distance, or when the mob dies.
 */
@EventBusSubscriber(modid = ArsOp.MODID)
public final class Possession {
    private Possession() {}

    public static final int START_CORRUPTION = 5;
    /** One more corruption point every this many ticks while dream-walking. */
    public static final int CORRUPTION_TICKS = 20;
    public static final double MAX_DISTANCE = 48;
    public static final int ATTACK_COOLDOWN = 12;
    public static final double ATTACK_REACH = 3.0;

    private static final String TAG = "ars_op_possessed";
    private static final String TAG_WAS_NO_AI = "ars_op_possessed_was_noai";

    private static final class State {
        final ServerLevel level;
        final int mobId;
        final Vec3 anchor;
        final int maxTicks;
        final boolean corrupt;
        int ticks;
        int attackCooldown;
        float yaw, pitch;

        State(ServerLevel level, int mobId, Vec3 anchor, int maxTicks, boolean corrupt, float yaw, float pitch) {
            this.level = level;
            this.corrupt = corrupt;
            this.maxTicks = maxTicks;
            this.mobId = mobId;
            this.anchor = anchor;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static boolean isPossessing(net.minecraft.world.entity.player.Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** Possessing through a normal Codex: corruption does not decay meanwhile. */
    public static boolean isCorrupting(net.minecraft.world.entity.player.Player player) {
        State s = ACTIVE.get(player.getUUID());
        return s != null && s.corrupt;
    }

    private static boolean isPossessed(Mob mob) {
        for (State s : ACTIVE.values()) if (s.level == mob.level() && s.mobId == mob.getId()) return true;
        return false;
    }

    public static void start(ServerPlayer player, Mob mob, int durationTicks, boolean corrupt) {
        if (isPossessing(player)) stop(player);
        if (mob.getType().is(Tags.EntityTypes.BOSSES)) {
            player.displayClientMessage(Component.translatable("ars_op.possess.too_strong").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        if (isPossessed(mob)) return;

        // No-AI mobs don't move at all, so keep AI on and switch its goals off instead.
        mob.getPersistentData().putBoolean(TAG, true);
        setGoalsEnabled(mob, false);
        mob.setTarget(null);
        mob.getNavigation().stop();

        ACTIVE.put(player.getUUID(), new State(player.serverLevel(), mob.getId(), player.position(), durationTicks, corrupt, player.getYRot(), player.getXRot()));
        PacketDistributor.sendToPlayer(player, new PossessPayload(mob.getId()));
        if (corrupt) Corruption.add(player, START_CORRUPTION);
        mob.level().playSound(null, mob, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
        player.displayClientMessage(Component.translatable("ars_op.possess.start", mob.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    public static void stop(ServerPlayer player) {
        State state = ACTIVE.remove(player.getUUID());
        if (state == null) return;
        if (state.level.getEntity(state.mobId) instanceof Mob mob) release(mob);
        PacketDistributor.sendToPlayer(player, new PossessPayload(-1));
        player.displayClientMessage(Component.translatable("ars_op.possess.end").withStyle(ChatFormatting.GRAY), true);
    }

    private static void release(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.getBoolean(TAG)) return;
        data.remove(TAG);
        data.remove(TAG_WAS_NO_AI); // left over from older builds
        setGoalsEnabled(mob, true);
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
    }

    private static void setGoalsEnabled(Mob mob, boolean enabled) {
        for (Goal.Flag flag : Goal.Flag.values()) {
            if (enabled) {
                mob.goalSelector.enableControlFlag(flag);
                mob.targetSelector.enableControlFlag(flag);
            } else {
                mob.goalSelector.disableControlFlag(flag);
                mob.targetSelector.disableControlFlag(flag);
            }
        }
    }

    /** Max horizontal / vertical distance the client may move the mob in one tick before the server pulls it back. */
    public static final double MAX_STEP_XZ = 1.2;
    public static final double MAX_STEP_Y = 4.0;

    /** The client simulates the possessed mob; take its position if it is plausible, otherwise correct the client. */
    public static void acceptMove(ServerPlayer player, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        State s = ACTIVE.get(player.getUUID());
        if (s == null || !(s.level.getEntity(s.mobId) instanceof Mob mob) || !mob.isAlive()) return;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        double dx = x - mob.getX(), dy = y - mob.getY(), dz = z - mob.getZ();
        boolean tooFar = dx * dx + dz * dz > MAX_STEP_XZ * MAX_STEP_XZ || Math.abs(dy) > MAX_STEP_Y;
        boolean blocked = !tooFar && !s.level.noCollision(mob, mob.getBoundingBox().move(dx, dy, dz).deflate(0.0625));
        if (tooFar || blocked) {
            PacketDistributor.sendToPlayer(player, new PossessMovePayload(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot(), mob.onGround()));
            return;
        }
        s.yaw = Mth.wrapDegrees(yaw);
        s.pitch = Mth.clamp(pitch, -90f, 90f);
        mob.absMoveTo(x, y, z, s.yaw, s.pitch);
        mob.setYHeadRot(s.yaw);
        mob.yBodyRot = s.yaw;
        mob.setOnGround(onGround);
        if (onGround) mob.resetFallDistance();
        mob.setDeltaMovement(Vec3.ZERO);
    }
    public static void attack(ServerPlayer player, int targetId) {
        State s = ACTIVE.get(player.getUUID());
        if (s == null || s.attackCooldown > 0) return;
        if (!(s.level.getEntity(s.mobId) instanceof Mob mob)) return;
        s.attackCooldown = ATTACK_COOLDOWN;
        mob.swing(InteractionHand.MAIN_HAND);
        if (mob instanceof Creeper creeper) {
            creeper.ignite();
            return;
        }
        Entity target = s.level.getEntity(targetId);
        if (target == null || target == mob || target == player || !target.isAlive()) return;
        double reach = ATTACK_REACH + mob.getBbWidth() / 2 + target.getBbWidth() / 2;
        if (mob.distanceToSqr(target) > reach * reach) return;
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.doHurtTarget(target);
        } else {
            target.hurt(mob.damageSources().mobAttack(mob), 2f);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            State s = ACTIVE.get(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                ACTIVE.remove(id);
                if (s.level.getEntity(s.mobId) instanceof Mob mob) release(mob);
                continue;
            }
            if (!(s.level.getEntity(s.mobId) instanceof Mob mob) || !mob.isAlive() || !player.isAlive()
                    || player.level() != s.level || mob.distanceToSqr(s.anchor) > MAX_DISTANCE * MAX_DISTANCE
                    || ++s.ticks >= s.maxTicks) {
                stop(player);
                continue;
            }
            if (s.corrupt && s.ticks % CORRUPTION_TICKS == 0) Corruption.add(player, 1);
            if (s.attackCooldown > 0) s.attackCooldown--;

            // Keep the body where it stood.
            if (player.position().distanceToSqr(s.anchor) > 0.04) {
                player.connection.teleport(s.anchor.x, s.anchor.y, s.anchor.z, player.getYRot(), player.getXRot());
            }
            drive(mob, s);
        }
    }

    /** The client moves the mob; the server only keeps its own AI and physics out of the way. */
    private static void drive(Mob mob, State s) {
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.setYRot(s.yaw);
        mob.setYHeadRot(s.yaw);
        mob.yBodyRot = s.yaw;
    }
    /** The body snaps back as soon as it is hurt. */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPossessing(player)) stop(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player != null) stop(player);
        }
        ACTIVE.clear();
    }

    /** A mob saved mid-possession (crash) gets its AI back on load. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity() instanceof Mob mob && mob.getPersistentData().getBoolean(TAG)) {
            release(mob);
        }
    }
}