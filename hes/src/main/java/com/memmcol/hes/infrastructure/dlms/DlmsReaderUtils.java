package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.service.AssociationLostException;
import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.service.DlmsUtils;
import com.memmcol.hes.service.GuruxObjectFactory;
import gurux.dlms.*;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import gurux.dlms.objects.*;
import lombok.RequiredArgsConstructor;
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
import java.util.*;

import static com.memmcol.hes.nettyUtils.RequestResponseService.logTx;

//import static com.memmcol.hes.nettyUtils.RequestResponseService.logTx;


@Service
@Slf4j
@RequiredArgsConstructor
public class DlmsReaderUtils {

    private final SessionManager sessionManager;
    private final MeterLockPort meterLockPort;
    private final DlmsPartialDecoder partialDecoder;
    private final DlmsTimestampDecoder timestampDecoder;
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
        byte[] response = RequestResponseService.sendReceiveWithContext(serial, firstRequest, 20000);
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
            response = RequestResponseService.sendReceiveWithContext(serial, nextRequest, 20000);

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
        byte[] response = RequestResponseService.sendReceiveWithContext(serial, scalerUnitRequest[0], 20000);
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


    public List<ProfileRowGeneric> readRange(String model, String meterSerial, String profileObis,
                                        ProfileMetadataResult metadataResult,
                                        LocalDateTime from, LocalDateTime to, boolean mdMeter) throws Exception {
        GXDLMSClient client = null;
        long t0 = System.currentTimeMillis();
        // Clear any stale partial buffer before a fresh read
        partialDecoder.clear(meterSerial, profileObis);
        try {
            client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("DLMS client not available for " + meterSerial);
            }
            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(profileObis);
            GXDateTime gxFrom = new GXDateTime(Date.from(from.atZone(ZoneId.systemDefault()).toInstant()));
            GXDateTime gxTo = new GXDateTime(Date.from(to.atZone(ZoneId.systemDefault()).toInstant()));

            String msg = String.format(
                    "Building DLMS range request model=%s meter=%s obis=%s from=%s to=%s",
                    model, meterSerial, profileObis, from, to);
            log.info(msg);
            logTx(meterSerial, msg);

            // 🔧 Load metadata and setup DLMS profile object
            // Ensure capture objects are populated (attr 3)
            //get profile columns objects and scaler
            List<ModelProfileMetadata> metadataList = metadataResult.getMetadataList();
            DlmsUtils.populateCaptureObjects(profile, metadataList);

            byte[][] reqFrames = client.readRowsByRange(profile, gxFrom, gxTo);

            if (reqFrames == null || reqFrames.length == 0) {
                log.warn("readRowsByRange produced no frames meter={} obis={}", meterSerial, profileObis);
                return List.of();
            }

            GXReplyData reply = new GXReplyData();

            // --- Send first frame
            byte[] firstFrame = reqFrames[0];
            byte[] resp1 = RequestResponseService.sendReceiveWithContext(meterSerial, firstFrame, 20000);
            if (sessionManager.isAssociationLost(resp1)) {
                log.warn("Association lost for {} on first frame.", meterSerial);
                sessionManager.removeSession(meterSerial);
                log.info("Creating new Association and Reading all over!");
                readRange(model, meterSerial, profileObis, metadataResult, from, to, mdMeter);
//                throw new AssociationLostException("Association lost on first frame");
            }
            client.getData(resp1, reply, null);

            // After each successful data chunk, accumulate partial rows for fallback
            updateProfileBufferOnBlock(client, profile, profileObis, meterSerial, reply);

            // --- Multi-block loop
            while (reply.isMoreData()) {
                byte[] nextReq = client.receiverReady(reply);
                if (nextReq == null) break;
                byte[] resp = RequestResponseService.sendReceiveWithContext(meterSerial, nextReq, 20000);
                client.getData(resp, reply, null);
                updateProfileBufferOnBlock(client, profile, profileObis, meterSerial, reply);
            }

            // SUCCESS PATH: we now have the buffer inside profile (attr 2)
            List<ProfileRowGeneric> rows = decodeProfileBuffer(profile, meterSerial, profileObis, metadataList);
            // Clear partial buffer because full read succeeded
            partialDecoder.clear(meterSerial, profileObis);

            long ms = System.currentTimeMillis() - t0;
            log.info("Range read complete meter={} obis={} rows={} elapsedMs={}", meterSerial, profileObis, rows.size(), ms);
            return rows;

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new Exception(e.getMessage());
        }
    }

    private void updateProfileBufferOnBlock(GXDLMSClient client, GXDLMSProfileGeneric profile, String profileObis, String meterSerial, GXReplyData reply) {
        Object val = reply.getValue() != null ? reply.getValue() : reply.getData();

        try {
            // Only update if val is not a raw GXByteBuffer (which is invalid for profile buffer)
            if (!(val instanceof GXByteBuffer)) {
                client.updateValue(profile, 2, val);

                List<List<Object>> buf = partialDecoder.normalizeProfileBuffer(profile.getBuffer());
                if (buf != null && !buf.isEmpty()) {
                    // Accumulate after each new chunk
                    partialDecoder.accumulate(meterSerial, profileObis, buf);
                    log.debug("Accumulated {} Object[] rows so far.", buf.size());
                }
            }

            if (val instanceof GXByteBuffer buf) {    // If raw buffer, decode once.
                GXDataInfo info = new GXDataInfo();
                buf.position(0);
                val = GXCommon.getData(null, buf, info);
                if (val instanceof List<?> rows) {
                    List<List<Object>> buf2 = partialDecoder.normalizeProfileBuffer(val);
                    if (buf2 != null && !buf2.isEmpty()) {
                        // Accumulate after each new chunk
                        partialDecoder.accumulate(meterSerial, profileObis, buf2);
                        log.debug("Accumulated {} GXByteBuffer rows so far.", buf.size());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Silent buffer update issue (not fatal) meter={} obis={} msg={}", meterSerial, profileObis, e.getMessage());
        }
    }

    private List<ProfileRowGeneric> decodeProfileBuffer(GXDLMSProfileGeneric profile, String meterSerial, String profileObis, List<ModelProfileMetadata> metadataList) {
        List<List<Object>> raw = partialDecoder.normalizeProfileBuffer(profile.getBuffer());
        if (raw == null || raw.isEmpty()) return List.of();
        return mapRawLists(raw, metadataList, meterSerial, profileObis);
    }

    List<ProfileRowGeneric> mapRawLists(
            List<List<Object>> raw,
            List<ModelProfileMetadata> metadataList,  //Column lists
            String meterSerial,
            String profileObis,
            boolean forDebug
    ) {
        List<ProfileRowGeneric> out = new ArrayList<>();
        int rowIndex = 0;
        String obisCode = "";

        /*TODO:
        *  1. Remove when debug confirmed
        *  2.
        * */
        log.debug("All lists size: {}", raw.size());

        for (List<Object> row : raw) {
            rowIndex++;
            if (row == null || row.isEmpty()) continue;


            // 🔎 Log the entire raw row
            log.debug("Inner list size: {}", row.size());
            log.debug("RAW ROW #{} → {}", rowIndex, row);
            // 🔎 Log each element with its corresponding OBIS + attribute index
            for (int i = 0; i < row.size() && i < metadataList.size(); i++) {
                ModelProfileMetadata meta = metadataList.get(i);
                Object value = row.get(i);

                String key = meta.getCaptureObis() + "-" + meta.getAttributeIndex();

                if (value instanceof byte[] bytes) {
                    log.debug("   {} : HEX={}", key, GXCommon.toHex(bytes));
                } else {
                    log.debug("   {} : {}", key, value);
                }
            }



            Map<String, Object> values = new LinkedHashMap<>();

            //Set timestamp
            obisCode = metadataList.get(0).getCaptureObis();

            Object tsRaw = row.get(0);
            LocalDateTime ts = timestampDecoder.toLocalDateTime(tsRaw);
            values.put(obisCode, ts);
            if (ts == null) {
                log.debug("Skipping row {} (no timestamp) meter={} obis={}", rowIndex, meterSerial, profileObis);
                continue;
            }

            //Set other columns
            for (int i = 1; i < row.size() && i < metadataList.size(); i++) {
                ModelProfileMetadata meta = metadataList.get(i);
                String key = meta.getCaptureObis() + "-" + meta.getAttributeIndex();
                Object value = row.get(i);
                values.put(key, value);
            }
            ProfileRowGeneric rowGeneric = new ProfileRowGeneric(ts.atZone(ZoneId.systemDefault()).toInstant(), values);
            out.add(rowGeneric);
        }
        return out;
    }

    private List<ProfileRowGeneric> mapRawLists(
            List<List<Object>> raw,
            List<ModelProfileMetadata> metadataList,  //Column lists
            String meterSerial,
            String profileObis
    ) {
        List<ProfileRowGeneric> out = new ArrayList<>();
        int rowIndex = 0;
        String obisCode = "";

        for (List<Object> row : raw) {
            rowIndex++;
            if (row == null || row.isEmpty()) continue;

            Map<String, Object> values = new LinkedHashMap<>();

            //Set timestamp
            obisCode = metadataList.get(0).getCaptureObis();

            Object tsRaw = row.get(0);
            LocalDateTime ts = timestampDecoder.toLocalDateTime(tsRaw);
            values.put(obisCode, ts);
            if (ts == null) {
                log.debug("Skipping row {} (no timestamp) meter={} obis={}", rowIndex, meterSerial, profileObis);
                continue;
            }

            //Set other columns
            for (int i = 1; i < row.size() && i < metadataList.size(); i++) {
//                obisCode = metadataList.get(i).getCaptureObis();
//                Object value = row.get(i);
//                values.put(obisCode, value);
//                obisCode = metadataList.get(i).getCaptureObis() + "-" + metadataList.get(i).getAttributeIndex();
                ModelProfileMetadata meta = metadataList.get(i);
                String key = meta.getCaptureObis() + "-" + meta.getAttributeIndex();
                Object value = row.get(i);
                values.put(key, value);


            }
            ProfileRowGeneric rowGeneric = new ProfileRowGeneric(ts.atZone(ZoneId.systemDefault()).toInstant(), values);
            out.add(rowGeneric);
        }
        return out;
    }

    public List<ProfileRowGeneric> recoverPartial(String meterSerial, String profileObis, String model, ProfileMetadataResult metadataResult) throws ProfileReadException {
        try {
            List<List<Object>> raw = partialDecoder.drainPartial(meterSerial, profileObis);
            if (raw.isEmpty()) {
                log.info("No partial rows to recover meter={} obis={}", meterSerial, profileObis);
                return List.of();
            }
            List<ModelProfileMetadata> metadataList = metadataResult.getMetadataList();
            List<ProfileRowGeneric> rows = mapRawLists(raw, metadataList, meterSerial, profileObis, true);
            log.info("Recovered {} partial rows meter={} obis={}", rows.size(), meterSerial, profileObis);
            return rows;
        } catch (Exception e) {
            throw new ProfileReadException("Partial recovery failed: " + e.getMessage(), e);
        }
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
