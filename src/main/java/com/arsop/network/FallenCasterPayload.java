package com.arsop.network;

import com.arsop.ArsOp;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the entity id of whoever cast Fall on this player (-1 = unknown). */
public record FallenCasterPayload(int casterId) implements CustomPacketPayload {
    public static final Type<FallenCasterPayload> TYPE = new Type<>(ArsOp.prefix("fallen_caster"));
    public static final StreamCodec<ByteBuf, FallenCasterPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, FallenCasterPayload::casterId, FallenCasterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
