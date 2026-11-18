package com.arsop.mixin;

import com.arsop.effect.FallenSilence;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops outgoing sound packets to Fallen players unless they come from their caster. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void arsop$muteFallen(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game && FallenSilence.shouldDrop(game.player, packet)) {
            ci.cancel();
        }
    }
}
