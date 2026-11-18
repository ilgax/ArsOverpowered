package com.arsop.network;

import com.arsop.ArsOp;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: whether the use key (right click) is held, for charging NULLs. */
public record NullHoldPayload(boolean held) implements CustomPacketPayload {
    public static final Type<NullHoldPayload> TYPE = new Type<>(ArsOp.prefix("null_hold"));
    public static final StreamCodec<ByteBuf, NullHoldPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, NullHoldPayload::held, NullHoldPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
