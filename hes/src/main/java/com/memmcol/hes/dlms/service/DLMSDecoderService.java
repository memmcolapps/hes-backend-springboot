package com.memmcol.hes.dlms.service;


import com.memmcol.hes.config.DlmsClientConfig;
import com.memmcol.hes.util.DLMSFrameParser;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.memmcol.hes.util.HexUtils.bytesToHex;


public class DLMSDecoderService {

    private static final Logger logger = LoggerFactory.getLogger(DLMSDecoderService.class);
    private final GXDLMSClient client;

    public DLMSDecoderService() {
        this.client = DlmsClientConfig.configureClient();
    }

    // Decode received frame
    public void decode(byte[] frame, ChannelHandlerContext ctx) {
        try {
            if (!isValidFrame(frame)) {
                logger.warn("Invalid frame: too short or null");
                return;
            }

            byte command = frame[7];
//            logger.debug("Command byte: 0x{}", String.format("%02X", command));

            if (isLoginFrame(command)) {
                handleLoginFrame(frame, ctx);
            } else if (isHeartbeatFrame(command)) {
                // handle heartbeat frame here
            } else if (command == (byte) 0x60) { // Association request
                handleAssociationRequest(frame, ctx);
            } else {
                handleGeneralDLMSFrame(frame);
            }
        } catch (Exception e) {
            logger.error("Error decoding frame: {}", e.getMessage(), e);
        }
    }

    private boolean isValidFrame(byte[] frame) {
        return frame != null && frame.length >= 8;
    }

    private boolean isLoginFrame(byte command) {
        // 0x13X represents login frame (based on frame[7] = 013X)
        return (command & 0xF0) == 0x10 && (command & 0x0F) == 0x03;
    }

    private boolean isHeartbeatFrame(byte command) {
        // 0x12X represents heartbeat frame (based on frame[7] = 012X)
        return (command & 0xF0) == 0x10 && (command & 0x0F) == 0x02;
    }

    public boolean isLoadProfileResponse(byte[] frame) {
        byte[] loadProfileObis = new byte[]{0x01, 0x00, 0x01, 0x08, 0x00, (byte) 0xFF};

        if (frame == null || frame.length < loadProfileObis.length) {
            return false;
        }

        // Search for OBIS code within the frame
        for (int i = 0; i <= frame.length - loadProfileObis.length; i++) {
            boolean match = true;
            for (int j = 0; j < loadProfileObis.length; j++) {
                if (frame[i + j] != loadProfileObis[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }

        return false;
    }



    private void handleLoginFrame(byte[] frame, ChannelHandlerContext ctx) {
        boolean validateCRC = false;
        DLMSFrameParser.LoginFrameData data = DLMSFrameParser.parseLoginFrame(frame, validateCRC);
        logger.info("LOGIN - METER NO: {}", data.getMeterNumber());

        // ✅ Send AARE response for successful login
        byte[] response = buildAAREFrame(data.getMeterNumber());

        logger.info("➡️ AARE Response Frame (HEX): {}", bytesToHex(response));

        ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
        logger.info("Sent AARE response to meter: {}", data.getMeterNumber());
    }

    private void handleAssociationRequest(byte[] frame, ChannelHandlerContext ctx) {
        GXByteBuffer buffer = new GXByteBuffer(frame);
        GXReplyData replyData = new GXReplyData();

        try {
            if (client.getData(buffer, replyData)) {
                logger.info("Association request processed successfully.");
                byte[] aareFrame = buildAAREFrame("");
                ctx.writeAndFlush(Unpooled.wrappedBuffer(aareFrame));
                logger.info("Sent AARE (Association Response) Frame.");
            } else {
                logger.warn("Invalid Association Request. Sending rejection.");
                byte[] rejectFrame = buildAAREFrame("");
                ctx.writeAndFlush(Unpooled.wrappedBuffer(rejectFrame));
            }
        } catch (Exception e) {
            logger.error("Error handling association request: ", e);
        }
    }

    private void handleGeneralDLMSFrame(byte[] frame) throws Exception {
        GXReplyData replyData = new GXReplyData();

        if (client.getData(new GXByteBuffer(frame), replyData)) {
            logger.info("Decoded DLMS Frame Data: {}", replyData.getValue());
        } else {
            logger.warn("Invalid DLMS frame or incomplete data");
        }
    }

    public void handleLoadProfileResponse(byte[] frame) {
        try {
            // Example: Extract actual data after OBIS code (adjust based on your meter's format)
            int offset = 6; // OBIS code length
            byte[] data = Arrays.copyOfRange(frame, offset, frame.length);

            logger.info("Handling Load Profile Response - Raw Data: {}", bytesToHex(data));

            // ✅ Example of parsing data (adjust to match your actual meter's structure)
            int value = (data[0] & 0xFF) << 8 | (data[1] & 0xFF);
            logger.info("Parsed Load Profile Value: {}", value);

            // TODO: Save to DB or further process based on your business logic
//            saveLoadProfileData(value);

        } catch (Exception e) {
            logger.error("Failed to handle load profile response", e);
        }
    }

//    private void saveLoadProfileData(int value) {
//        // Example: Save to database or processing pipeline
//        LoadProfile loadProfile = new LoadProfile();
//        loadProfile.setValue(value);
//        loadProfile.setTimestamp(LocalDateTime.now());
//
//        loadProfileRepository.save(loadProfile);
//        log.info("Load Profile data saved: {}", value);
//    }


    private byte[] buildAAREFrameV1(boolean isAccepted) {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.put((byte) 0x61); // AARE Tag
        buffer.put((byte) 0x16); // Length of frame

        buffer.put((byte) 0xA1); // Result field
        buffer.put((byte) 0x03);

        buffer.put((byte) (isAccepted ? 0x00 : 0x01)); // 0x00 = Accepted, 0x01 = Rejected

        // Responding AP Title
        buffer.put((byte) 0xA2);
        buffer.put((byte) 0x06);
        buffer.put(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06});

        buffer.put((byte) 0xA3);
        buffer.put((byte) 0x02);
        buffer.put((byte) 0x00);

        byte[] frame = Arrays.copyOf(buffer.array(), buffer.position());
        short crc = calculateCRC(frame, frame.length);
        buffer.putShort(crc);

        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private byte[] buildAAREFrame(String meterNumber) {
        byte[] meterBytes = meterNumber.getBytes(StandardCharsets.US_ASCII);
        int meterLength = meterBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(32);

        // Session and control identifiers
        buffer.put(new byte[]{0x00, 0x01, 0x00, 0x01, 0x00, 0x01});
        buffer.put((byte) 0x00); // Protocol control/reserved
        buffer.put((byte) 0x13); // Command type (login-related)

        // Response code and status
        buffer.put((byte) 0xAA); // Response code for AARE
        buffer.put((byte) 0x03); // Status/result code

        // Dynamic length of the meter number
        buffer.put((byte) meterLength);

        // Meter number (ASCII encoded)
        buffer.put(meterBytes);

        buffer.put((byte) 0x00); // Reserved byte

        // Compute CRC over current content
        byte[] frameWithoutCRC = Arrays.copyOf(buffer.array(), buffer.position());
        short crc = calculateCRC(frameWithoutCRC, frameWithoutCRC.length);

        buffer.putShort(crc); // Append CRC

        return Arrays.copyOf(buffer.array(), buffer.position());
    }


    private short calculateCRC(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc = crc >> 1;
                }
            }
        }
        return (short) crc;
    }
}