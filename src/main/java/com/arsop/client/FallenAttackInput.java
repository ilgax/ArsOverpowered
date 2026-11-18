package com.arsop.client;

import com.arsop.ArsOp;
import com.arsop.effect.FallenEffect;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * While pinned, attack and use clicks are cancelled before the client acts on them:
 * no local swing animation, and no packet is ever sent.
 */
@EventBusSubscriber(modid = ArsOp.MODID, value = Dist.CLIENT)
public final class FallenAttackInput {
    private FallenAttackInput() {}

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !FallenEffect.isPinned(player)) return;
        if (event.isAttack() || event.isUseItem()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }
}
