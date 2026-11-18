package com.arsop.client;

import com.arsop.ArsOp;
import com.arsop.effect.FallenEffect;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/** Intercepts the local player's movement input while Fallen. */
@EventBusSubscriber(modid = ArsOp.MODID, value = Dist.CLIENT)
public final class FallenInput {
    private FallenInput() {}

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!FallenEffect.isFallen(event.getEntity())) return;
        Input in = event.getInput();
        in.jumping = false;
        event.getEntity().setSprinting(false);
        if (FallenEffect.isPinned(event.getEntity())) {
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.shiftKeyDown = false;
        } else {
            in.forwardImpulse *= 0.2f;
            in.leftImpulse *= 0.2f;
        }
    }
}
