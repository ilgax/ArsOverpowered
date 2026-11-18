package com.arsop.client;

import com.arsop.ArsOp;
import com.arsop.network.PossessActionPayload;
import com.arsop.network.PossessMovePayload;
import com.arsop.possess.PossessionClientState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of dream-walking: camera in the mob, own movement turned into mob input, clicks into mob attacks. */
@EventBusSubscriber(modid = ArsOp.MODID, value = Dist.CLIENT)
public final class PossessionClient {
    private PossessionClient() {}

    private static float forward, strafe;
    private static boolean jump;

    public static void onPayload(int id) {
        forward = 0;
        strafe = 0;
        jump = false;
        PossessionClientState.mobId = id;
        Minecraft mc = Minecraft.getInstance();
        if (id < 0) restoreCamera(mc);
        else applyCamera(mc);
    }

    public static boolean isActive() {
        return PossessionClientState.mobId >= 0;
    }

    private static boolean active() {
        return PossessionClientState.mobId >= 0;
    }

    private static void restoreCamera(Minecraft mc) {
        if (mc.player != null && mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
    }

    private static void applyCamera(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        Entity e = mc.level.getEntity(PossessionClientState.mobId);
        if (e != null && mc.getCameraEntity() != e) mc.setCameraEntity(e);
    }

    /** End locally at once (no waiting for the server) and tell the server. */
    private static void requestStop(Minecraft mc) {
        PossessionClientState.mobId = -1;
        restoreCamera(mc);
        PacketDistributor.sendToServer(new PossessActionPayload(PossessActionPayload.STOP, -1));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            PossessionClientState.mobId = -1;
            return;
        }
        applyCamera(mc);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        Input in = event.getInput();
        if (in.shiftKeyDown) {
            requestStop(mc);
        } else {
            forward = in.forwardImpulse;
            strafe = in.leftImpulse;
            jump = in.jumping;
        }
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** Before the possessed mob ticks on this client: feed it your look and keys, like a player. */
    @SubscribeEvent
    public static void onMobTickPre(EntityTickEvent.Pre event) {
        if (!active() || !(event.getEntity() instanceof Mob mob) || !mob.level().isClientSide
                || mob.getId() != PossessionClientState.mobId) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float yaw = mc.player.getYRot();
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setXRot(mc.player.getXRot());

        AttributeInstance attr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        mob.setSpeed((float) (attr != null ? attr.getValue() : 0.25));
        mob.xxa = strafe;
        mob.zza = forward;
        mob.setJumping(jump);
    }

    /** After it ticks: tell the server where it went. */
    @SubscribeEvent
    public static void onMobTickPost(EntityTickEvent.Post event) {
        if (!active() || !(event.getEntity() instanceof LivingEntity mob) || !mob.level().isClientSide
                || mob.getId() != PossessionClientState.mobId) return;
        PacketDistributor.sendToServer(new PossessMovePayload(mob.getX(), mob.getY(), mob.getZ(),
                mob.getYRot(), mob.getXRot(), mob.onGround()));
    }

    /** The server rejected a move: put the mob back where it says. */
    public static void onCorrection(PossessMovePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.level == null) return;
        Entity e = mc.level.getEntity(PossessionClientState.mobId);
        if (e == null) return;
        e.setPos(p.x(), p.y(), p.z());
        e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCameraEntity() == null || mc.getCameraEntity().getId() != PossessionClientState.mobId) return;
        float pt = (float) event.getPartialTick();
        event.setYaw(mc.player.getViewYRot(pt));
        event.setPitch(mc.player.getViewXRot(pt));
    }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        event.setSwingHand(false);
        event.setCanceled(true);
        if (event.isAttack()) {
            int target = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
            PacketDistributor.sendToServer(new PossessActionPayload(PossessActionPayload.ATTACK, target));
        } else if (event.isUseItem()) {
            requestStop(mc);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (active()) event.setCanceled(true);
    }
}