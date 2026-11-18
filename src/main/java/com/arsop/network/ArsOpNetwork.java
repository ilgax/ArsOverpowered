package com.arsop.network;

import com.arsop.ArsOp;
import com.arsop.entity.NullCharge;
import com.arsop.possess.Possession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = ArsOp.MODID) // RegisterPayloadHandlersEvent is a mod-bus event; routed automatically
public final class ArsOpNetwork {
    private ArsOpNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(FallenCasterPayload.TYPE, FallenCasterPayload.STREAM_CODEC,
                (payload, context) -> FallenClientState.casterId = payload.casterId());
        registrar.playToServer(NullHoldPayload.TYPE, NullHoldPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> NullCharge.setHeld(context.player(), payload.held())));
        registrar.playToClient(CorruptionPayload.TYPE, CorruptionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.arsop.client.CorruptionHud.value = payload.value()));
        registrar.playToClient(PossessPayload.TYPE, PossessPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.arsop.client.PossessionClient.onPayload(payload.mobId())));
        registrar.playBidirectional(PossessMovePayload.TYPE, PossessMovePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        Possession.acceptMove(sp, payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch(), payload.onGround());
                    } else {
                        com.arsop.client.PossessionClient.onCorrection(payload);
                    }
                }));        registrar.playToServer(PossessActionPayload.TYPE, PossessActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    if (payload.action() == PossessActionPayload.ATTACK) Possession.attack(sp, payload.targetId());
                    else Possession.stop(sp);
                }));
    }
}
