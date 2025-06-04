package com.memmcol.hes.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/*
A custom decoder that extends ByteToMessageDecoder to extract full DLMS frames from incoming byte stream.
 */
public class DLMSFrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Ensure we have enough data for DLMS header
        if (in.readableBytes() < 8) return;

        // Mark current read position
        in.markReaderIndex();

        int length = in.getUnsignedShort(in.readerIndex() + 6); // wrapper header at pos 6

        if (in.readableBytes() < length + 8) {
            // Wait for more data
            in.resetReaderIndex();
            return;
        }

        // Extract the full frame
        byte[] frame = new byte[length + 8];
        in.readBytes(frame);

        // Pass it to next handler
        out.add(frame);
    }
}
