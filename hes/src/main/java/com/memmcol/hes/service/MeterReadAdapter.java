package com.memmcol.hes.service;

import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.model.ProfileMetadataDTO;
import com.memmcol.hes.model.ProfileRowDTO;
import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.repository.ModelProfileMetadataRepository;
import com.memmcol.hes.trackByTimestamp.MeterProfileStateService;
import com.memmcol.hes.trackByTimestamp.ProfileTimestampCacheService;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.GXStructure;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.math.BigDecimal;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeterReadAdapter {

    private final SessionManager sessionManager;
//    private final @Lazy ProfileMetadataService profileMetadataService;
    private final ProfileTimestampCacheService cacheService;
    private final MeterProfileStateService stateService;
    private final DlmsUtils dlmsUtils;
    private final ModelProfileMetadataRepository repo;
    private final ProfileMetadataService profileMetadataService;
    private final RequestResponseService requestResponseService;

    /**
     * Reads a DLMS data block, including segmented/multi-frame responses.
     *
     * @param client       DLMS client for protocol interaction
     * @param serial       MetersEntity serial number (used to route channel/command)
     * @param firstRequest Initial byte request (e.g., from `client.read`)
     * @return GXReplyData containing the full response
     */
    public GXReplyData readDataBlock(GXDLMSClient client, String serial, byte[] firstRequest) throws Exception {
        GXReplyData reply = new GXReplyData();

        // Send initial request
        byte[] response = requestResponseService.sendReceiveWithContext(serial, firstRequest, 20000);
        if (isAssociationLost(response)) {
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

            response = requestResponseService.sendReceiveWithContext(serial, nextRequest, 20000);

            if (isAssociationLost(response)) {
                sessionManager.removeSession(serial);
                throw new AssociationLostException("Association lost in block read");
            }

            client.getData(response, reply, null);
        }

        return reply;
    }

    public List<ProfileRowDTO> readDataBlockWithPartialSupport(
            GXDLMSClient client,
            String serial,
            byte[] initialRequest,
            List<ProfileMetadataDTO.ColumnDTO> columns,
            int entryStart
    ) throws Exception {

        List<ProfileRowDTO> result = new ArrayList<>();
        ProfileRowParserRaw rowParserRaw = new ProfileRowParserRaw();
        int entryId = entryStart;
        int blockCounter = 0;

        // ---- First block ----
        byte[] resp = requestResponseService.sendReceiveWithContext(serial, initialRequest, 20000);
        if (isAssociationLost(resp)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException("Association lost on initial request.");
        }

        GXReplyData reply = new GXReplyData();
        client.getData(resp, reply, null);

        Object value = reply.getValue() != null ? reply.getValue() : reply.getData();
        List<ProfileRowDTO> parsed = rowParserRaw.parseBlock(columns, value, entryId);
        result.addAll(parsed);
        entryId += parsed.size();
        log.info("Block#{} rows={} total={}", blockCounter, parsed.size(), result.size());

        // ---- Next blocks ----
        while (reply.isMoreData()) {
            byte[] nextReq = client.receiverReady(reply);
            if (nextReq == null) break;

            byte[] blockResp = requestResponseService.sendReceiveWithContext(serial, nextReq, 20000);
            if (isAssociationLost(blockResp)) {
                sessionManager.removeSession(serial);
                throw new AssociationLostException("Association lost mid-transfer.");
            }

            reply = new GXReplyData(); // fresh
            client.getData(blockResp, reply, null);

            List<Object> decodedObjs = rowParserRaw.decodeAllObjectsFromReply(reply);

            //debug only
            for (int k = 0; k < decodedObjs.size(); k++) {
                Object o = decodedObjs.get(k);
                if (o instanceof GXStructure gs) {
                    log.debug("Block{} obj{} = GXStructure(size={})", blockCounter, k, gs.size());
                } else if (o instanceof byte[] b) {
                    log.debug("Block{} obj{} = byte[] len={} hex={}", blockCounter, k, b.length, GXCommon.toHex(b));
                } else {
                    log.debug("Block{} obj{} = {} -> {}", blockCounter, k, o.getClass().getSimpleName(), o);
                }
            }

            List<ProfileRowDTO> more = rowParserRaw.parseDecodedObjects(columns, decodedObjs, entryId);
            result.addAll(more);
            entryId += more.size();

            log.info("Block#{} rows={} total={}", ++blockCounter, more.size(), result.size());
        }

        // Optional dedup by timestamp
        result = dedupByTimestamp(result);
        return result;
    }

    //    Dedup by Timestamp (Optional but strongly recommended)
    private List<ProfileRowDTO> dedupByTimestamp(List<ProfileRowDTO> rows) {
        Map<LocalDateTime, ProfileRowDTO> map = new LinkedHashMap<>();
        for (ProfileRowDTO r : rows) {
            LocalDateTime ts = r.getProfileTimestamp();
            if (ts == null) {
                // keep "no timestamp" rows uniquely using synthetic key
                ts = LocalDateTime.MIN.plusNanos(map.size());
            }
            map.put(ts, r); // last one wins
        }
        return new ArrayList<>(map.values());
    }


    public boolean isAssociationLost(byte[] response) {
        // Match DLMS "Association Lost" signature
        if (response == null || response.length < 3) return false;
        // Check specific sequence or code e.g., ends with D8 01 01
        int len = response.length;
        return response[len - 3] == (byte) 0xD8
                && response[len - 2] == 0x01
                && response[len - 1] == 0x01;
    }

    public Object readAttribute(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] request = client.read(obj, index);
        byte[] response = requestResponseService.sendReceiveWithContext(serial, request[0], 20000);
        if (isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        return client.updateValue(obj, index, reply.getValue());
    }

    private Object readAttributeWithBlock(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] request = client.read(obj, index);
        GXReplyData reply = readDataBlock(client, serial, request[0]);
        return client.updateValue(obj, index, reply.getValue());
    }


    private double readScaler(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] scalerUnitRequest = client.read(obj, index);
        byte[] response = requestResponseService.sendReceiveWithContext(serial, scalerUnitRequest[0], 20000);
        if (isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        Object updateValue = client.updateValue(obj, index, reply.getValue());

        try {
            if (obj instanceof GXDLMSRegister reg) {
                double scaler = reg.getScaler();
                return BigDecimal.valueOf(Math.pow(10, scaler)).doubleValue();
            } else if (obj instanceof GXDLMSDemandRegister reg) {
                double scaler = reg.getScaler();
                return BigDecimal.valueOf(Math.pow(10, scaler)).doubleValue();
            } else {
                log.warn("⚠️ Object is not Register or Demand Register: {}", obj.getLogicalName());
            }
        } catch (Exception e) {
            log.error("❌ Error reading scaler from object {}: {}", obj.getLogicalName(), e.getMessage());
        }

        return BigDecimal.ONE.doubleValue();
    }


    public void readScalerUnit(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] scalerUnitRequest = client.read(obj, index);
        byte[] response = requestResponseService.sendReceiveWithContext(serial, scalerUnitRequest[0], 20000);
        if (isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        client.updateValue(obj, index, reply.getValue());
    }

    public LocalDateTime bootstrapFromMeter(String serial, String profileObis, String model) throws Exception {
        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) throw new IllegalStateException("DLMS session missing for " + serial);

        // Build PG object
        GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
        profile.setLogicalName(profileObis);

        // Ensure capture objects are populated (attr 3)
        List<ModelProfileMetadata> metadataList =
                profileMetadataService.getOrLoadMetadata(model, profileObis, serial);
        DlmsUtils.populateCaptureObjects(profile, metadataList);

        // ---- READ FIRST ROW (entry=1, count=1) ----
        byte[][] req = client.readRowsByEntry(profile, 1, 1);
        GXReplyData reply = readDataBlock(client, serial, req[0]);

        // Update attribute 2 (buffer) in the profile object
        client.updateValue(profile, 2, reply.getValue());

        // Extract from profile.getBuffer()
        LocalDateTime ts = dlmsUtils.extractFirstTimestampFromProfileBuffer(profile);
        if (ts != null) {
            cacheService.put(serial, profileObis, ts);
            stateService.upsertLastTimestamp(serial, profileObis, ts);
            log.info("Bootstrapped start timestamp for {} ({}) = {}", serial, profileObis, ts);
        } else {
            log.warn("Failed to parse first timestamp for {} ({})", serial, profileObis);
        }
        return ts;
    }

    public byte[][] buildReadRequest(GXDLMSClient client, GXDLMSProfileGeneric profile, int start, int count) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException {
        return client.readRowsByEntry(profile, start, count);
    }

    // Other reusable DLMS operations...
}
