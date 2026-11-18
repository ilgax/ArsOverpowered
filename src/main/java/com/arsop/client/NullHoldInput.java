package com.arsop.client;

import com.arsop.ArsOp;
import com.arsop.network.NullHoldPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tells the server when the use key goes down or up, so a charging NULL knows when to grow and when to fly. */
@EventBusSubscriber(modid = ArsOp.MODID, value = Dist.CLIENT)
public final class NullHoldInput {
    private NullHoldInput() {}

    private static boolean lastHeld;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            lastHeld = false;
            return;
        }
        boolean held = mc.options.keyUse.isDown();
        if (held != lastHeld) {
            lastHeld = held;
            PacketDistributor.sendToServer(new NullHoldPayload(held));
        }
    }
}
