package com.arsop.network;

import com.arsop.ArsOp;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: dream-walk into this mob's view, or -1 to return to your own body. */
public record PossessPayload(int mobId) implements CustomPacketPayload {
    public static final Type<PossessPayload> TYPE = new Type<>(ArsOp.prefix("possess"));
    public static final StreamCodec<ByteBuf, PossessPayload> STREAM_CODEC =
            ByteBufCodecs.INT.map(PossessPayload::new, PossessPayload::mobId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}