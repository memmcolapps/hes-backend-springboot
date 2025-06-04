package com.memmcol.hes.netty;

import io.netty.buffer.ByteBuf;

/*
a safe and reusable utility method to convert a Netty ByteBuf to a byte[] only
when needed — without affecting the buffer’s reader state.
 */
public class NettyUtils {
    public static byte[] toByteArray(ByteBuf buf) {
        if (buf == null || !buf.isReadable()) {
            return new byte[0];
        }
        byte[] bytes = new byte[buf.readableBytes()];
        int readerIndex = buf.readerIndex(); // Save reader position
        buf.getBytes(readerIndex, bytes);    // Copy without moving reader
        return bytes;
    }
}
