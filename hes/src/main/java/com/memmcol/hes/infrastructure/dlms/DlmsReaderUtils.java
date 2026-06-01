package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.model.*;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.exception.AssociationLostException;
import com.memmcol.hes.service.DlmsUtils;
import com.memmcol.hes.service.GuruxObjectFactory;
import com.memmcol.hes.trackByTimestamp.MeterProfileStateRepository;
import gurux.dlms.*;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.Unit;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import gurux.dlms.objects.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.memmcol.hes.nettyUtils.RequestResponseService.logTx;

@Service
@Slf4j
@RequiredArgsConstructor
public class DlmsReaderUtils {

    private final SessionManagerMultiVendor sessionManager;
    private final MeterLockPort meterLockPort;
    private final DlmsPartialDecoder partialDecoder;
    private final DlmsTimestampDecoder timestampDecoder;
    private final TxRxService txRxService;
    private final MeterProfileStateRepository meterProfileStateRepository;
    private final MeterRepository meterRepository;
    private final DlmsResponseDecoder responseDecoder;

    private record DlmsRangeWindow(LocalDateTime from, LocalDateTime to) {}

    /**
     * Resolves the DLMS read window. When {@code requestedFrom} is supplied by the ingestion loop,
     * it takes precedence over {@code meter_profile_state} so in-memory cursor catch-up is not reset
     * to an older persisted timestamp on every range read.
     */
    private DlmsRangeWindow resolveDlmsRangeWindow(
            String meterSerial,
            String profileObis,
            LocalDateTime requestedFrom,
            LocalDateTime requestedTo
    ) {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime fromTs = requestedFrom;
        if (fromTs == null) {
            fromTs = meterProfileStateRepository
                    .findByMeterSerialAndProfileObis(meterSerial, profileObis)
                    .map(s -> s.getLastTimestamp())
                    .filter(Objects::nonNull)
                    .orElse(null);
        }

        if (fromTs == null) {
            fromTs = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                    .map(MeterDTO::getCreatedAt)
                    .filter(Objects::nonNull)
                    .orElse(now.minusDays(1));
        }

        LocalDateTime toTs = requestedTo != null ? requestedTo : fromTs.plusDays(1);
        if (toTs.isAfter(now)) {
            toTs = now;
        }
        if (!toTs.isAfter(fromTs)) {
            toTs = fromTs.plusHours(1);
            if (toTs.isAfter(now)) {
                toTs = now;
            }
        }

        log.info("DLMS range window meter={} obis={} from={} to={} (requestedFrom={} requestedTo={})",
                meterSerial, profileObis, fromTs, toTs, requestedFrom, requestedTo);
        return new DlmsRangeWindow(fromTs, toTs);
    }

    public GXReplyData readDataBlock(GXDLMSClient client, String serial, byte[] firstRequest) throws Exception {
        GXReplyData reply = new GXReplyData();

        byte[] response = txRxService.sendReceiveWithContext(serial, firstRequest, 20000);
        if (sessionManager.isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException("Association lost with " + serial);
        }
        client.getData(response, reply, null);

        while (reply.isMoreData()) {
            byte[] nextRequest;

            if (reply.isStreaming()) {
                log.debug("Streaming block: no receiverReady needed.");
                nextRequest = null;
            } else {
                log.debug("Sending receiverReady...");
                nextRequest = client.receiverReady(reply);
            }
            if (nextRequest == null) break;

            response = txRxService.sendReceiveWithContext(serial, nextRequest, 20000);

            if (sessionManager.isAssociationLost(response)) {
                sessionManager.removeSession(serial);
                throw new AssociationLostException("Association lost in block read");
            }

            client.getData(response, reply, null);
        }
        DlmsErrorUtils.checkError(reply, serial, "OBIS Read!");
        return reply;
    }

    public void readScalerUnit(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] scalerUnitRequest = client.read(obj, index);
        byte[] response = txRxService.sendReceiveWithContext(serial, scalerUnitRequest[0], 20000);
        if (sessionManager.isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        DlmsErrorUtils.checkError(reply, serial, "OBIS Read!");
        client.updateValue(obj, index, reply.getValue());
    }

    public DlmsResponse writeAttribute(GXDLMSClient client,
                                       String serial,
                                       GXDLMSObject obj,
                                       int index,
                                       Object value) throws Exception {

        try {
            if (obj instanceof GXDLMSClock clock && value instanceof GXDateTime dt) {
                clock.setTime(dt);
            } else {
                client.updateValue(obj, index, value);
            }

            byte[][] requests = client.write(obj, index);

            if (requests == null || requests.length == 0) {
                throw new IllegalStateException("DLMS write generated no frames");
            }

            GXReplyData reply = new GXReplyData();

            long start = System.currentTimeMillis();
            for (int i = 0; i < requests.length; i++) {
                byte[] requestFrame = requests[i];

                log.debug("📤 DLMS TX [{} / {}] → meter={}, bytes={}",
                        i + 1, requests.length, serial, toHex(requestFrame));

                byte[] response = txRxService.sendReceiveWithContext(serial, requestFrame, 20000);
                if (sessionManager.isAssociationLost(response)) {
                    sessionManager.removeSession(serial);
                    throw new AssociationLostException("Association lost during write → meter=" + serial);
                }

                processReply(client, serial, response, reply);
            }

            if (!reply.isComplete()) {
                throw new IllegalStateException("Incomplete DLMS reply → meter=" + serial);
            }

            long duration = System.currentTimeMillis() - start;
            log.info("✅ DLMS write completed → meter={}, duration={}ms", serial, duration);

            DlmsResponseStatus status = responseDecoder.decodeStatus(reply);
            return DlmsResponse.builder()
                    .status(status)
                    .message(reply.getErrorMessage())
                    .meterSerial(serial)
                    .build();
        } catch (java.net.SocketTimeoutException | java.util.concurrent.TimeoutException te) {
            return DlmsResponse.builder()
                    .status(DlmsResponseStatus.TIMEOUT)
                    .message("Connection timeout: " + te.getMessage())
                    .meterSerial(serial)
                    .build();
        } catch (Exception e) {
            return DlmsResponse.builder()
                    .status(DlmsResponseStatus.COMMUNICATION_ERROR)
                    .message("Communication error: " + e.getMessage())
                    .meterSerial(serial)
                    .build();
        }
    }

    public DlmsResponse writeAttribute(GXDLMSClient client,
                                       String serial,
                                       String logicalName,
                                       int classId,
                                       int index,
                                       Object value,
                                       DataType dataType) throws Exception {

        try {
            byte[][] request = client.write(logicalName, value, dataType, gurux.dlms.enums.ObjectType.forValue(classId), index);

            return getDlmsResponse(client, serial, request);
        } catch (java.net.SocketTimeoutException | java.util.concurrent.TimeoutException te) {
            return DlmsResponse.builder()
                    .status(DlmsResponseStatus.TIMEOUT)
                    .message("Connection timeout: " + te.getMessage())
                    .meterSerial(serial)
                    .build();
        } catch (Exception e) {
            return DlmsResponse.builder()
                    .status(DlmsResponseStatus.COMMUNICATION_ERROR)
                    .message("Communication error: " + e.getMessage())
                    .meterSerial(serial)
                    .build();
        }
    }

    private DlmsResponse getDlmsResponse(GXDLMSClient client, String serial, byte[][] request) throws Exception {
        byte[] response = txRxService.sendReceiveWithContext(serial, request[0], 20000);

        if (sessionManager.isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }

        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);

        DlmsResponseStatus status = responseDecoder.decodeStatus(reply);
        return DlmsResponse.builder()
                .status(status)
                .message(reply.getErrorMessage())
                .meterSerial(serial)
                .build();
    }

    public DlmsResponse executeMethod( GXDLMSClient client, String serial, byte[][] requests) throws Exception {

        try {
            GXReplyData reply = new GXReplyData();
            long start = System.currentTimeMillis();

            for (int i = 0; i < requests.length; i++) {
                byte[] requestFrame = requests[i];

                log.debug( "📤 DLMS METHOD TX [{} / {}] → meter={}, bytes={}", i + 1,requests.length,serial,toHex(requestFrame));

                byte[] response = txRxService.sendReceiveWithContext(serial,requestFrame,20000);

                if (sessionManager.isAssociationLost(response)) {
                    sessionManager.removeSession(serial);
                    throw new AssociationLostException("Association lost during method execution → meter=" + serial);
                }

                processReply(client, serial, response, reply);
            }

            if (!reply.isComplete()) {
                throw new IllegalStateException("Incomplete DLMS method reply → meter=" + serial);
            }

            long duration = System.currentTimeMillis() - start;

            log.info("✅ DLMS method completed → meter={}, duration={}ms",serial,duration);

            DlmsResponseStatus status = responseDecoder.decodeStatus(reply);

            return DlmsResponse.builder()
                    .status(status)
                    .message(reply.getErrorMessage())
                    .meterSerial(serial)
                    .rawResponse(GXCommon.toHex(reply.getData().getData()))
                    .build();

        } catch (java.net.SocketTimeoutException |
                 java.util.concurrent.TimeoutException te) {

            return DlmsResponse.builder()
                    .status(DlmsResponseStatus.TIMEOUT)
                    .message("Connection timeout: " + te.getMessage())
                    .meterSerial(serial)
                    .build();

        } catch (Exception e) {

            return DlmsResponse.builder()
                    .status(DlmsResponseStatus.COMMUNICATION_ERROR)
                    .message("Communication error: " + e.getMessage())
                    .meterSerial(serial)
                    .build();
        }
    }

    public TokenWriteResult parseTokenResponse(byte[] rawBytes) {

        TokenWriteResult result = new TokenWriteResult();

        result.setRawHex(GXCommon.toHex(rawBytes));

        try {

            if (rawBytes == null || rawBytes.length == 0) {
                result.setTokenStatus(TokenStatus.UNKNOWN);
                result.setErrorDetail("Empty meter response");
                return result;
            }

            for (int i = 0; i < rawBytes.length; i++) {

                int tag = rawBytes[i] & 0xFF;

                if (tag == 0x11 && (i + 1) < rawBytes.length) {
                    int tokenCode = rawBytes[i + 1] & 0xFF;
                    if (tokenCode >= 0 && tokenCode <= 20) {
                        TokenStatus status = TokenStatus.fromCode(tokenCode);
                        result.setTokenStatus(status);
                        result.setTokenResultCode(tokenCode);
                        log.info("Parsed token result → code={}, status={}",tokenCode,status);
                    }
                }
                else if (tag == 0x05 && (i + 4) < rawBytes.length) {
                    long rawCredit =
                            ((rawBytes[i + 4] & 0xFFL) << 24) |
                                    ((rawBytes[i + 3] & 0xFFL) << 16) |
                                    ((rawBytes[i + 2] & 0xFFL) << 8)  |
                                    (rawBytes[i + 1] & 0xFFL);

                    BigDecimal credit =
                            BigDecimal.valueOf(rawCredit)
                                    .divide(BigDecimal.valueOf(100));

                    result.setMeterCredit(credit);
                    log.info("Parsed meter credit → {}", credit);
                }
                else if (tag == 0x09 && (i + 1) < rawBytes.length) {
                    int length = rawBytes[i + 1] & 0xFF;
                    if ((i + 2 + length) <= rawBytes.length) {
                        byte[] tokenBytes =
                                Arrays.copyOfRange(
                                        rawBytes,
                                        i + 2,
                                        i + 2 + length
                                );

                        String logoutToken =
                                GXCommon.toHex(tokenBytes)
                                        .replace(" ", "");

                        result.setLogoutToken(logoutToken);
                        log.info("Parsed logout token → {}", logoutToken);
                    }
                }
            }

            result.setSuccess(result.getTokenStatus() == TokenStatus.SUCCESS);

            if (result.getTokenStatus() == null) {
                result.setTokenStatus(TokenStatus.UNKNOWN);
                result.setTokenResultCode(-1);
                result.setErrorDetail("Unable to determine token result");
            }

            return result;

        } catch (Exception e) {
            log.error("Token response parsing failed", e);
            result.setTokenStatus(TokenStatus.UNKNOWN);
            result.setTokenResultCode(-1);
            result.setErrorDetail(e.getMessage());
            return result;
        }
    }

    public Object readAttribute(GXDLMSClient client, String serial, GXDLMSObject obj, int index) throws Exception {
        byte[][] request = client.read(obj, index);
        byte[] response = txRxService.sendReceiveWithContext(serial, request[0], 20000);

        if (sessionManager.isAssociationLost(response)) {
            sessionManager.removeSession(serial);
            throw new AssociationLostException();
        }
        GXReplyData reply = new GXReplyData();
        client.getData(response, reply, null);
        DlmsErrorUtils.checkError(reply, serial, "OBIS Read!");
        return client.updateValue(obj, index, reply.getValue());
    }

    public Object checkAssociationStatus(String meterSerial) throws Exception {
        Object response = "Associated";
        GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
        GXDLMSAssociationLogicalName association = new GXDLMSAssociationLogicalName();
        association.setLogicalName("0.0.40.0.0.255");
        try {
            response = readAttribute(client, meterSerial, association, 8);
        } catch (AssociationLostException lostException) {
            sessionManager.removeSession(meterSerial);
            sessionManager.getOrCreateClient(meterSerial);
        }
        return response;
    }

    public Object readClock(String meterSerial) throws Exception {
        Object response = LocalDateTime.now();
        GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
        GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");
        try {
            response = readAttribute(client, meterSerial, clock, 2);
        } catch (AssociationLostException lostException) {
            sessionManager.removeSession(meterSerial);
            sessionManager.getOrCreateClient(meterSerial);
        }
        return response;
    }

    public Object readObisObject(String meterSerial, String obisCode, int classId, int attributeIndex) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            try {
                GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
                if (client == null) {
                    log.warn("No active session for {}.", meterSerial);
                    throw new Exception("No active session for " + meterSerial + ".");
                }
                GXDLMSObject object = GuruxObjectFactory.create(classId, obisCode);
                return readAttribute(client, meterSerial, object, attributeIndex);
            } catch (AssociationLostException lostException) {
                log.warn("Association lost for {}.", meterSerial);
                sessionManager.removeSession(meterSerial);
                return readObisObject(meterSerial, obisCode, classId, attributeIndex);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                return e.getMessage();
            }
        });
    }

    /**
     * Authoritative readRange method.
     */
    public List<ProfileRowGeneric> readRange(String model, String meterSerial, String profileObis,
                                             ProfileMetadataResult metadataResult,
                                             LocalDateTime from, LocalDateTime to, boolean mdMeter) throws Exception {

        if (profileObis == null || profileObis.isBlank()) {
            throw new IllegalArgumentException("profileObis must not be null/blank");
        }
        if (metadataResult == null || metadataResult.getMetadataList() == null || metadataResult.getMetadataList().isEmpty()) {
            throw new IllegalArgumentException("Capture objects not read.");
        }

        long t0 = System.currentTimeMillis();
        partialDecoder.clear(meterSerial, profileObis);

        try {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("DLMS client context could not be created for " + meterSerial);
            }

            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(profileObis);

            DlmsRangeWindow resolved = resolveDlmsRangeWindow(meterSerial, profileObis, from, to);
            LocalDateTime actualFrom = resolved.from();
            LocalDateTime actualTo = resolved.to();

            if (actualTo.isBefore(actualFrom)) {
                actualTo = actualFrom.plusHours(24);
            }

            GXDateTime gxFrom = new GXDateTime(java.util.Date.from(actualFrom.atZone(ZoneId.systemDefault()).toInstant()));
            GXDateTime gxTo = new GXDateTime(java.util.Date.from(actualTo.atZone(ZoneId.systemDefault()).toInstant()));

            log.info("Building DLMS range request model={} meter={} obis={} from={} to={}",
                    model, meterSerial, profileObis, actualFrom, actualTo);

            List<ModelProfileMetadata> metadataList = metadataResult.getMetadataList();
            DlmsUtils.populateCaptureObjects(profile, metadataList);

            byte[][] reqFrames = client.readRowsByRange(profile, gxFrom, gxTo);
            if (reqFrames == null || reqFrames.length == 0) {
                log.warn("readRowsByRange produced no frames meter={} obis={}", meterSerial, profileObis);
                return List.of();
            }

            GXReplyData reply = new GXReplyData();

            // --- First frame
            byte[] resp = txRxService.sendReceiveWithContext(meterSerial, reqFrames[0], 20000);
            if (sessionManager.isAssociationLost(resp)) {
                log.warn("Association lost for {} on first frame. Re-establishing session...", meterSerial);
                sessionManager.removeSession(meterSerial);
                throw new AssociationLostException("Association lost on first frame");
            }
            client.getData(resp, reply, null);
            updateProfileBufferOnBlock(client, profile, profileObis, meterSerial, reply);

            // --- Multi-block loop
            while (reply.isMoreData()) {
                byte[] nextReq = client.receiverReady(reply);
                if (nextReq == null) break;

                resp = txRxService.sendReceiveWithContext(meterSerial, nextReq, 20000);
                if (sessionManager.isAssociationLost(resp)) {
                    sessionManager.removeSession(meterSerial);
                    throw new AssociationLostException("Association lost during block read");
                }
                client.getData(resp, reply, null);
                updateProfileBufferOnBlock(client, profile, profileObis, meterSerial, reply);
            }

            List<ProfileRowGeneric> rows = decodeProfileBuffer(profile, meterSerial, profileObis, metadataList);
            partialDecoder.clear(meterSerial, profileObis);

            long ms = System.currentTimeMillis() - t0;
            log.info("Range read complete meter={} obis={} rows={} elapsedMs={}", meterSerial, profileObis, rows.size(), ms);
            return rows;

        } catch (Exception e) {
            log.error("Range read failed meter={} obis={} cause={}", meterSerial, profileObis, e.getMessage());
            // Rethrow so service layer can catch and trigger recovery
            throw e;
        }
    }

    private void updateProfileBufferOnBlock(GXDLMSClient client, GXDLMSProfileGeneric profile, String profileObis, String meterSerial, GXReplyData reply) throws Exception {
        Object val = reply.getValue();
        if (val == null) {
            val = reply.getData();
        }

        if (val instanceof GXByteBuffer buf) {
            GXDataInfo info = new GXDataInfo();
            buf.position(0);
            val = GXCommon.getData(null, buf, info);
        }

        if (val != null) {
            client.updateValue(profile, 2, val);
            List<List<Object>> rawBuffer = partialDecoder.normalizeProfileBuffer(profile.getBuffer());
            if (rawBuffer != null && !rawBuffer.isEmpty()) {
                partialDecoder.accumulate(meterSerial, profileObis, rawBuffer);
            }
        }
    }

    private List<ProfileRowGeneric> decodeProfileBuffer(GXDLMSProfileGeneric profile, String meterSerial, String profileObis, List<ModelProfileMetadata> metadataList) {
        List<List<Object>> raw = partialDecoder.normalizeProfileBuffer(profile.getBuffer());
        if (raw == null || raw.isEmpty()) return List.of();
        return mapRawLists(raw, metadataList, meterSerial, profileObis);
    }

    List<ProfileRowGeneric> mapRawLists(
            List<List<Object>> raw,
            List<ModelProfileMetadata> metadataList,
            String meterSerial,
            String profileObis
    ) {
        List<ProfileRowGeneric> out = new ArrayList<>();
        int rowIndex = 0;

        for (List<Object> row : raw) {
            rowIndex++;
            if (row == null || row.isEmpty()) continue;

            if (row.size() != metadataList.size()) {
                log.warn("Row width mismatch meter={} obis={} rowIndex={} rowColumns={} metadataColumns={}",
                        meterSerial, profileObis, rowIndex, row.size(), metadataList.size());
            }

            Map<String, Object> values = new LinkedHashMap<>();

            Object tsRaw = row.get(0);
            LocalDateTime ts = timestampDecoder.decodeTimestamp(tsRaw);
            if (ts == null) {
                log.debug("Skipping row {} (no timestamp) meter={} obis={}", rowIndex, meterSerial, profileObis);
                continue;
            }

            String timestampObis = metadataList.get(0).getCaptureObis();
            values.put(timestampObis, ts);

            int columnLimit = Math.min(row.size(), metadataList.size());
            for (int i = 1; i < columnLimit; i++) {
                ModelProfileMetadata meta = metadataList.get(i);
                String key = meta.getCaptureObis() + "-" + meta.getAttributeIndex();
                Object value = row.get(i);
                values.put(key, value);
            }
            ProfileRowGeneric rowGeneric = new ProfileRowGeneric(ts.atZone(ZoneId.systemDefault()).toInstant(), meterSerial, profileObis, values);
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
            List<ProfileRowGeneric> rows = mapRawLists(raw, metadataList, meterSerial, profileObis);
            log.info("Recovered {} partial rows meter={} obis={}", rows.size(), meterSerial, profileObis);
            return rows;
        } catch (Exception e) {
            throw new ProfileReadException("Partial recovery failed: " + e.getMessage(), e);
        }
    }

    public LocalDateTime extractFirstRowTimestampDirect(GXDLMSProfileGeneric profile) {
        Object rawBuf = profile.getBuffer();
        if (rawBuf == null) return null;

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
            return null;
        }

        return extractFirstRowTimestampFromDecoded(firstRow);
    }

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
            firstCol = rowObj;
        }
        return timestampDecoder.decodeTimestamp(firstCol);
    }

    public LocalDateTime parseTimestamp(Object raw) {
        if (raw == null) return null;
        if (raw instanceof GXDateTime gxdt) {
            return gxdt.getValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        if (raw instanceof LocalDateTime dt) return dt;
        if (raw instanceof String str) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(str, formatter);
            } catch (Exception e) {
                log.error("❌ Failed to parse timestamp: {}", str, e);
            }
        }
        if (raw instanceof byte[] bytes) return timestampDecoder.decode(bytes);
        return null;
    }

    public String getUnitSymbol(Unit unit) {
        return switch (unit) {
            case VOLTAGE -> "V";
            case CURRENT -> "A";
            case ACTIVE_POWER -> "kW";
            case APPARENT_POWER -> "kVA";
            case REACTIVE_POWER -> "kVar";
            case ACTIVE_ENERGY -> "kWh";
            case APPARENT_ENERGY -> "kVAh";
            case REACTIVE_ENERGY -> "kvarh";
            case FREQUENCY -> "Hz";
            default -> unit.name();
        };
    }

    private void processReply(GXDLMSClient client, String serial, byte[] response, GXReplyData reply) throws Exception {
        client.getData(response, reply);
        while (reply.isMoreData()) {
            byte[] rr = client.receiverReady(reply);
            response = txRxService.sendReceiveWithContext(serial, rr, 20000);
            client.getData(response, reply);
        }
    }

    private String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public List<ProfileRowGeneric> decodeRecoveredRows(
            List<List<Object>> recovered,
            String meterSerial,
            String profileObis,
            List<ModelProfileMetadata> metadataList) {

        List<ProfileRowGeneric> result = new ArrayList<>();
        if (recovered == null || recovered.isEmpty()) return result;

        int expectedColumns = metadataList.size();
        for (int i = 0; i < recovered.size(); i++) {
            List<Object> row = recovered.get(i);
            if (row == null || row.size() != expectedColumns) continue;

            try {
                Map<String, Object> values = new HashMap<>();
                Instant timestamp = null;

                for (int col = 0; col < expectedColumns; col++) {
                    ModelProfileMetadata meta = metadataList.get(col);
                    Object raw = row.get(col);
                    Object safeValue = sanitizeValue(raw, meta);

                    if ("clock_object".equalsIgnoreCase(meta.getColumnName())) {
                        if (safeValue instanceof Instant inst) timestamp = inst;
                        else if (safeValue instanceof LocalDateTime ldt) timestamp = ldt.atZone(ZoneId.systemDefault()).toInstant();
                    }
                    values.put(meta.getColumnName(), safeValue);
                }

                if (timestamp == null) continue;
                result.add(new ProfileRowGeneric(timestamp, meterSerial, profileObis, values));

            } catch (Exception ex) {
                log.error("Failed to map recovered row idx={} meter={} obis={} cause={}", i, meterSerial, profileObis, ex.getMessage());
            }
        }
        return result;
    }

    private Object sanitizeValue(Object value, ModelProfileMetadata meta) {
        if (value == null) return null;
        if (value instanceof GXDateTime gxDateTime) return gxDateTime.getLocalCalendar().toInstant();
        if (value instanceof Number num) {
            double scaled = num.doubleValue();
            if (meta.getScaler() != null) scaled *= meta.getScaler();
            return scaled;
        }
        if (value instanceof byte[] bytes) return GXCommon.toHex(bytes, false);
        return null;
    }
}
