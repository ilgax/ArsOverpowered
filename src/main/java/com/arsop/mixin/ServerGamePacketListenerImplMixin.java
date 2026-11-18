package com.arsop.mixin;

import com.arsop.effect.FallenEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops a pinned (Fallen II) player's swing / attack / use / block-break packets on arrival,
 * so the server never acts on them and never broadcasts the swing animation to anyone.
 *
 * Packet handlers first run on the netty thread and re-schedule themselves onto the server
 * thread; we only decide on the server thread so the effect check is not racy.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    private boolean arsop$blocked() {
        return player != null && player.getServer() != null
                && player.getServer().isSameThread()
                && FallenEffect.isPinned(player);
    }

    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
    private void arsop$swing(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (arsop$blocked()) ci.cancel();
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void arsop$interact(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (arsop$blocked()) ci.cancel();
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void arsop$useItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (arsop$blocked()) ci.cancel();
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void arsop$useItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (arsop$blocked()) ci.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void arsop$playerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        // Only block hitting blocks; dropping items / swapping hands etc. still go through.
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && arsop$blocked()) {
            ci.cancel();
        }
    }
}
