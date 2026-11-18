package com.arsop.network;

import com.arsop.ArsOp;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Both ways. Client to server: where the client moved the possessed mob this tick.
 * Server to client: a correction when the server rejected a move.
 */
public record PossessMovePayload(double x, double y, double z, float yaw, float pitch, boolean onGround) implements CustomPacketPayload {
    public static final Type<PossessMovePayload> TYPE = new Type<>(ArsOp.prefix("possess_move"));
    public static final StreamCodec<ByteBuf, PossessMovePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, PossessMovePayload::x,
            ByteBufCodecs.DOUBLE, PossessMovePayload::y,
            ByteBufCodecs.DOUBLE, PossessMovePayload::z,
            ByteBufCodecs.FLOAT, PossessMovePayload::yaw,
            ByteBufCodecs.FLOAT, PossessMovePayload::pitch,
            ByteBufCodecs.BOOL, PossessMovePayload::onGround,
            PossessMovePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}