package com.arsop.network;

import com.arsop.ArsOp;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: end the dream-walk, or make the possessed mob attack an entity. */
public record PossessActionPayload(int action, int targetId) implements CustomPacketPayload {
    public static final int STOP = 0;
    public static final int ATTACK = 1;

    public static final Type<PossessActionPayload> TYPE = new Type<>(ArsOp.prefix("possess_action"));
    public static final StreamCodec<ByteBuf, PossessActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PossessActionPayload::action,
            ByteBufCodecs.INT, PossessActionPayload::targetId,
            PossessActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}