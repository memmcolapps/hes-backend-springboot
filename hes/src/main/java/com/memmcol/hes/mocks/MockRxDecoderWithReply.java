package com.memmcol.hes.mocks;

import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.service.MeterSession;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Arrays;

import static com.memmcol.hes.nettyUtils.RequestResponseService.logTx;

@Slf4j
public class MockRxDecoderWithReply {
    public static void main(String[] args) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException {
        RxDecoderWithReply();
//        mockAddSession("1234678963");
    }

    private static void RxDecoderWithReply() throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, SignatureException {
        String rxHex = "00 01 00 01 00 01 00 2B 61 29 A1 09 06 07 60 85 74 05 08 01 01 A2 03 02 01 00 A3 05 A1 03 02 01 00 BE 10 04 0E 08 00 06 5F 1F 04 00 60 1A 1D 00 C8 00 07";
        String rxHex2 = "00 01 00 01 00 01 00 C2 C4 02 C1 00 00 00 00 01 00 82 00 B6 01 25 02 02 09 0C 07 E9 07 19 05 11 37 20 FF FF C4 00 16 02 02 02 09 0C 07 E9 07 19 05 11 37 20 FF FF C4 00 16 01 02 02 09 0C 07 E9 07 19 05 11 37 23 FF FF C4 00 16 02 02 02 09 0C 07 E9 07 19 05 11 37 26 FF FF C4 00 16 58 02 02 09 0C 07 E9 07 19 05 11 37 26 FF FF C4 00 16 59 02 02 09 0C 07 E9 07 1B 07 07 29 11 FF FF C4 00 16 EC 02 02 09 0C 07 E9 07 1B 07 07 29 11 FF FF C4 00 16 ED 02 02 09 0C 07 E9 07 1B 07 07 29 11 FF FF C4 00 16 01 02 02 09 0C 07 E9 07 1B 07 08 02 0B FF FF C4 00 16 02 02 02 09 0C 07 E9 07 1B 07 08 02 0E FF FF C4 00 16 58";
        byte[] rx = hexToBytes(rxHex2);

        GXDLMSClient client = new GXDLMSClient(
                true, 1, 1,
                Authentication.LOW,
                "12345678",
                InterfaceType.WRAPPER);

        GXReplyData reply = new GXReplyData();
        client.getData(rx, reply, null);

        Object val = reply.getValue() != null ? reply.getValue() : reply.getData();
        if (val instanceof GXByteBuffer) {
            GXByteBuffer buf = (GXByteBuffer) val;
            System.out.println("Reply contains GXByteBuffer, size=" + buf.size());
        } else {
            System.out.println("Reply value = " + val);
        }
    }


    public static GXDLMSClient mockAddSession(String meterSerial) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException {
        String aare = "00 01 00 01 00 01 00 2B 61 29 A1 09 06 07 60 85 74 05 08 01 01 A2 03 02 01 00 A3 05 A1 03 02 01 00 BE 10 04 0E 08 00 06 5F 1F 04 00 60 1A 1D 00 C8 00 07";
        GXDLMSClient dlmsClient = new GXDLMSClient(
                true, 1, 1,
                Authentication.LOW,
                "12345678",
                InterfaceType.WRAPPER);

        try {
            String msg = String.format("Setting up DLMS Association for meter=%s", meterSerial);
            log.info(msg);
//            logTx(meterSerial, msg);
            byte[][] aarq = dlmsClient.aarqRequest();
            byte[] response = hexToBytes(aare);
            byte[] payload = Arrays.copyOfRange(response, 8, response.length);
            GXByteBuffer replyBuffer = new GXByteBuffer(payload);
            try {
                dlmsClient.parseAareResponse(replyBuffer);
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ AARE parse failed: {}", e.getMessage());
            }
            log.info("✅ DLMS Association established for {}", meterSerial);
        } catch (Exception e) {
            log.error("❌ DLMS Association failed for {}: {}", meterSerial, e.getMessage());
            throw e;
        }
        return dlmsClient;
    }

    private static byte[] hexToBytes(String s) {
        s = s.replaceAll("\\s", "");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
