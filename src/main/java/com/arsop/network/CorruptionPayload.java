package com.arsop.network;

import com.arsop.ArsOp;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the player's current corruption (0-100), for the HUD bar. */
public record CorruptionPayload(int value) implements CustomPacketPayload {
    public static final Type<CorruptionPayload> TYPE = new Type<>(ArsOp.prefix("corruption"));
    public static final StreamCodec<ByteBuf, CorruptionPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(CorruptionPayload::new, CorruptionPayload::value);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}