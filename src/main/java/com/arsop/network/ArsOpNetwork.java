package com.arsop.network;

import com.arsop.ArsOp;
import com.arsop.entity.NullCharge;
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
    }
}
