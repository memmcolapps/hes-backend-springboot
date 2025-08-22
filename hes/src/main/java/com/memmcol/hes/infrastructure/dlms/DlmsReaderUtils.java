package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.service.AssociationLostException;
import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.service.GuruxObjectFactory;
import com.memmcol.hes.service.MeterConnections;
import com.memmcol.hes.service.MeterSession;
import gurux.dlms.*;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import gurux.dlms.objects.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class DlmsReaderUtils {

    private final SessionManager sessionManager;
    private final MeterLockPort meterLockPort;

    public DlmsReaderUtils(SessionManager sessionManager, MeterLockPort meterLockPort) {
        this.sessionManager = sessionManager;
        this.meterLockPort = meterLockPort;
    }

    /**
     * Reads a DLMS data block, including segmented/multi-frame responses.
     *
     * @param client       DLMS client for protocol interaction
     * @param serial       Meter serial number (used to route channel/command)
     * @param firstRequest Initial byte request (e.g., from `client.read`)
     * @return GXReplyData containing the full response
     */
    public GXReplyData readDataBlock(GXDLMSClient client, String serial, byte[] firstRequest) throws Exception {
        GXReplyData reply = new GXReplyData();

        // Send initial request
//        byte[] response = RequestResponseService.sendCommandWithRetry(serial, firstRequest);
        byte[] response = RequestResponseService.sendOnceListen(serial, firstRequest, 4000, 16000, 100);
        if (sessionManager.isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException("Association lost with " + serial);
        }
        client.getData(response, reply, null);

        // Handle multi-block responses
        // Loop if there is more data
        while (reply.isMoreData()) {
            byte[] nextRequest;

            if (reply.isStreaming()) {
                log.debug("Streaming block: no receiverReady needed.");
                nextRequest = null; // Streaming continues automatically // Streaming doesn't need new request
            } else {
                log.debug("Sending receiverReady...");
//                nextRequest = client.receiverReady(reply.getMoreData());
                nextRequest = client.receiverReady(reply); // ✅ Correct
            }
            if (nextRequest == null) break; // Safety

//            response = RequestResponseService.sendCommandWithRetry(serial, nextRequest);
            response = RequestResponseService.sendOnceListen(serial, nextRequest, 4000, 16000, 100);

            if (sessionManager.isAssociationLost(response)) {
                sessionManager.removeSession(serial);
                throw new AssociationLostException("Association lost in block read");
            }

            client.getData(response, reply, null);
        }

        return reply;
    }

    public void readScalerUnit(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] scalerUnitRequest = client.read(obj, index);
        byte[] response = RequestResponseService.sendOnceListen(serial, scalerUnitRequest[0], 4000, 16000, 100);
        if (sessionManager.isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        client.updateValue(obj, index, reply.getValue());
    }

    public Object readAttribute(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] request = client.read(obj, index);
//        byte[] response = RequestResponseService.sendOnceListen(serial, request[0], 4000, 16000, 100);

        byte[] response = RequestResponseService.sendReceiveWithContext(serial, request[0], 20000);

        if (sessionManager.isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        return client.updateValue(obj, index, reply.getValue());
    }

    public Object readObisObject(String meterSerial, String obisCode, int classId, int attributeIndex) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            try {
                //Step 1: Establish DLMS Association
                GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
                if (client == null) {
                    log.warn("No active session for {}.", meterSerial);
                    throw new Exception("No active session for " + meterSerial + ".");
                }
                //Step 2: Create DLMS objects and read objects
                GXDLMSObject object = GuruxObjectFactory.create(classId, obisCode);
                return readAttribute(client, meterSerial, object, attributeIndex);
            } catch (AssociationLostException lostException) {
                //Step3: Catch Association Lost Exception and retry
                log.warn("Association lost for {}.", meterSerial);
                sessionManager.removeSession(meterSerial);
                return readObisObject(meterSerial, obisCode, classId, attributeIndex);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                return e.getMessage();
            }
        });
    }

    public void sendDisconnectRequest(String meterSerial, GXDLMSClient dlmsClient) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException, InterruptedException {
        byte[] disconnect = dlmsClient.disconnectRequest();
        log.debug("Disconnect Request: {}", GXCommon.toHex(disconnect));
        byte[] response = RequestResponseService.sendReceiveWithContext(meterSerial, disconnect, 20000);
        log.info("Meter disconnect response: {}", GXCommon.toHex(response));
    }

    public LocalDateTime parseTimestamp(Object raw) {
        switch (raw) {
            case null -> {
                return null;
            }

            case GXDateTime gxdt -> {
                Date date = gxdt.getValue(); // returns java.util.Date
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }

            case LocalDateTime dt -> {
                return dt;
            }
            case String str -> {
                try {
                    // Acceptable format in your JSON: 2024-08-21 10:30:00
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    return LocalDateTime.parse(str, formatter);
                } catch (Exception e) {
                    log.error("❌ Failed to parse timestamp: {}", str, e);
                }
            }
            default -> {
            }
        }

        return null;
    }

    public LocalDateTime extractFirstRowTimestampDirect(GXDLMSProfileGeneric profile) {
        Object rawBuf = profile.getBuffer();
        if (rawBuf == null) {
            log.debug("extractFirstRowTimestampDirect: buffer null.");
            return null;
        }

        Object firstRow = null;
        if (rawBuf instanceof List<?> list) {
            if (list.isEmpty()) return null;
            firstRow = list.get(0);

        } else if (rawBuf instanceof GXArray gxArr) {
            if (gxArr.size() == 0) return null;
            firstRow = gxArr.get(0);

        } else if (rawBuf.getClass().isArray()) {
            Object[] arr = (Object[]) rawBuf;
            if (arr.length == 0) return null;
            firstRow = arr[0];

        } else if (rawBuf instanceof GXByteBuffer gxBuf) {
            GXDataInfo info = new GXDataInfo();
            Object decoded = GXCommon.getData(null, gxBuf, info);
            return extractFirstRowTimestampFromDecoded(decoded);
        } else {
            log.debug("extractFirstRowTimestampDirect: unexpected buffer type {}", rawBuf.getClass());
            return null;
        }

        return extractFirstRowTimestampFromDecoded(firstRow);
    }

    @SuppressWarnings("unchecked")
    private LocalDateTime extractFirstRowTimestampFromDecoded(Object rowObj) {
        Object firstCol;

        if (rowObj instanceof GXStructure gs) {
            if (gs.size() == 0) return null;
            firstCol = gs.get(0);

        } else if (rowObj instanceof List<?> l) {
            if (l.isEmpty()) return null;
            firstCol = l.get(0);

        } else if (rowObj != null && rowObj.getClass().isArray()) {
            Object[] arr = (Object[]) rowObj;
            if (arr.length == 0) return null;
            firstCol = arr[0];

        } else {
            // Already a single value? Then assume timestamp column directly
            firstCol = rowObj;
        }

        return parseTimestamp(firstCol);
    }
}
