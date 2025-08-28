package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.application.port.out.PartialProfileRecoveryPort;
import com.memmcol.hes.application.port.out.ProfileDataReaderPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.domain.profile.ProfileRow;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.repository.ModelProfileMetadataRepository;
import com.memmcol.hes.service.*;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXReplyData;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import gurux.dlms.objects.*;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 4. Infrastructure Adapters (Skeletons)
 * 4.1 Gurux Reader Adapter
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GuruxProfileReaderAdapter implements ProfileDataReaderPort, PartialProfileRecoveryPort {

    //DlmsPartialDecoder and DlmsTimestampDecoder are wrappers you create around your existing parsing utilities.
    private final SessionManager sessionManager;           // your existing component
    private final DlmsPartialDecoder partialDecoder;       // -> create wrapper for existing parser
    private final DlmsTimestampDecoder timestampDecoder;   // util for timestamp bytes
    private final ModelProfileMetadataRepository repo;
    private final DlmsReaderUtils dlmsReaderUtils;

    // Optional: if you later need scalers, you can inject a resolver:
    // private final ProfileScalerResolver scalerResolver;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter STANDARD_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Attempt partial recovery after a failed multi-block read.
     */
    @Override
    public List<ProfileRow> recoverPartial(String meterSerial, String profileObis) throws ProfileReadException {
        try {
            List<List<Object>> raw = partialDecoder.drainPartial(meterSerial, profileObis);
            if (raw.isEmpty()) {
                log.info("No partial rows to recover meter={} obis={}", meterSerial, profileObis);
                return List.of();
            }
            List<ProfileRow> rows = mapRawLists(raw, meterSerial, profileObis);
            log.info("Recovered {} partial rows meter={} obis={}", rows.size(), meterSerial, profileObis);
            return rows;
        } catch (Exception e) {
            throw new ProfileReadException("Partial recovery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Read a time-range of profile rows from meter.
     *
     * @param meterSerial meter identifier
     * @param profileObis profile OBIS
     * @param from        inclusive start timestamp
     * @param to          inclusive end timestamp
     */
    @Override
    public List<ProfileRow> readRange(String model, String meterSerial, String profileObis, List<ModelProfileMetadata> metadataList, LocalDateTime from, LocalDateTime to) throws ProfileReadException {
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

            GXDateTime gxFrom = new GXDateTime(Date.from(from.atZone(ZONE).toInstant()));
            GXDateTime gxTo = new GXDateTime(Date.from(to.atZone(ZONE).toInstant()));

            log.info("Building DLMS range request model ={} meter={} obis={} from={} to={}", model, meterSerial, profileObis, from, to);

            // 🔧 Load metadata and setup DLMS profile object
            // Ensure capture objects are populated (attr 3)
            DlmsUtils.populateCaptureObjects(profile, metadataList);

            byte[][] reqFrames = client.readRowsByRange(profile, gxFrom, gxTo);

            if (reqFrames == null || reqFrames.length == 0) {
                log.warn("readRowsByRange produced no frames meter={} obis={}", meterSerial, profileObis);
                return List.of();
            }

            GXReplyData reply = new GXReplyData();

            //Catch read timeout and return partial records.
            try {
                // --- Send first frame
                byte[] firstFrame = reqFrames[0];
//            byte[] resp1 = RequestResponseService.sendCommandWithRetry(meterSerial, firstFrame);
                byte[] resp1 = RequestResponseService.sendOnceListen(meterSerial, firstFrame, 4000, 16000, 100);
                if (sessionManager.isAssociationLost(resp1)) {
                    throw new AssociationLostException("Association lost on first frame");
                }
                client.getData(resp1, reply, null);

                // After each successful data chunk, accumulate partial rows for fallback
                updateProfileBufferOnBlock(client, profile, profileObis, meterSerial, reply);

                // --- Multi-block loop
                while (reply.isMoreData()) {
                    byte[] nextReq = client.receiverReady(reply);
                    if (nextReq == null) break;

//                byte[] resp = RequestResponseService.sendCommandWithRetry(meterSerial, nextReq);
                    byte[] resp = RequestResponseService.sendOnceListen(meterSerial, nextReq, 4000, 16000, 100);
                    if (sessionManager.isAssociationLost(resp1)) {
                        throw new AssociationLostException("Association lost on next frame");
                    }

                    client.getData(resp, reply, null);

                    updateProfileBufferOnBlock(client, profile, profileObis, meterSerial, reply);
                }
            } catch (ReadTimeoutException timeoutException) {
                // --- Timeout occurred, salvage partial rows ---
                log.warn("readRowsByRange timeout, salvaging partial rows meter={} obis={}", meterSerial, profileObis);
//                List<ProfileRow> partialRows = partialDecoder.normalizeProfileBuffer(profile.getBuffer());
                List<ProfileRow> partialRows = decodeProfileBuffer(profile, meterSerial, profileObis);
                if (partialRows != null && !partialRows.isEmpty()) {
                    long ms = System.currentTimeMillis() - t0;
                    log.info("Returning {} partial rows after timeout meter={} obis={} elapsedMs={}", partialRows.size(), meterSerial, profileObis, ms);
                    return partialRows;
                } else {
                    throw new ProfileReadException("Timeout with no partial data", timeoutException);
                }
            }

            // SUCCESS PATH: we now have the buffer inside profile (attr 2)
            List<ProfileRow> rows = decodeProfileBuffer(profile, meterSerial, profileObis);
            // Clear partial buffer because full read succeeded
            partialDecoder.clear(meterSerial, profileObis);

            long ms = System.currentTimeMillis() - t0;
            log.info("Range read complete meter={} obis={} rows={} elapsedMs={}", meterSerial, profileObis, rows.size(), ms);
            return rows;

        } catch (AssociationLostException ex2) {
            sessionManager.removeSession(meterSerial);
            long ms = System.currentTimeMillis() - t0;
            log.warn("Association Lost Exception meter={} obis={} elapsedMs={} cause={}", meterSerial, profileObis, ms, ex2.getMessage());
            // DO NOT clear partial here; salvage may still need it
            throw new ProfileReadException("Association Lost Exception", ex2);
        } catch (Exception ex) {
            long ms = System.currentTimeMillis() - t0;
            log.warn("Range read FAILED meter={} obis={} elapsedMs={} cause={}", meterSerial, profileObis, ms, ex.getMessage());
            // DO NOT clear partial here; salvage may still need it
            throw new ProfileReadException("Range read failed", ex);
        }
    }

    @Override
    public void sendDisconnectRequest(String meterSerial) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, IOException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException {
        //
    }

        /* -----------------------------------------------------------------------
       Internal Helpers
       ----------------------------------------------------------------------- */

    /**
     * After each block is processed, update buffer & accumulate partial rows.
     * Depending on Gurux version, the buffer might be set by updateValue(..,4,..)
     * or embedded in reply.getValue(); we keep a defensive branch.
     */
    private void updateProfileBufferOnBlock1(GXDLMSClient client, GXDLMSProfileGeneric profile, String profileObis, String meterSerial, GXReplyData reply) {

        Object val = reply.getValue() != null ? reply.getValue() : reply.getData();

        try {
            // Attribute 4 is the DLMS Profile Buffer
            client.updateValue(profile, 2, val);
            List<List<Object>> buf = partialDecoder.normalizeProfileBuffer(profile.getBuffer());
            if (buf != null && !buf.isEmpty()) {
                // Accumulate after each new chunk
                partialDecoder.accumulate(meterSerial, profileObis, buf);
            }
        } catch (Exception e) {
            log.debug("Silent buffer update issue (not fatal) meter={} obis={} msg={}", meterSerial, profileObis, e.getMessage());
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

//    private int partialSizeEstimate(String meterSerial, String obis) {
//        // purely optional – if you want to expose size, store it in DlmsPartialDecoder
//        return 0;
//    }


    /**
     * Decode the final profile buffer on a successful full read.
     */
    private List<ProfileRow> decodeProfileBuffer(GXDLMSProfileGeneric profile, String meterSerial, String profileObis) {
        List<List<Object>> raw = partialDecoder.normalizeProfileBuffer(profile.getBuffer());
//        List<List<Object>> raw = profile.getBuffer();
        if (raw == null || raw.isEmpty()) return List.of();
        return mapRawLists(raw, meterSerial, profileObis);
    }

    private List<ProfileRow> mapRawLists(List<List<Object>> raw, String meterSerial, String profileObis) {
        List<ProfileRow> out = new ArrayList<>();
        int rowIndex = 0;
        for (List<Object> row : raw) {
            rowIndex++;
            if (row == null || row.isEmpty()) continue;

            LocalDateTime ts = timestampDecoder.toLocalDateTime(row.get(0));
            if (ts == null) {
                log.debug("Skipping row {} (no timestamp) meter={} obis={}", rowIndex, meterSerial, profileObis);
                continue;
            }

            Double active = getNumeric(row, 1);
            Double reactive = getNumeric(row, 2);

            // OPTIONAL scaling hook:
            // active = scalerResolver.scaleActive(profileObis, active);
            // reactive = scalerResolver.scaleReactive(profileObis, reactive);

            String rawHex = buildRawHex(row);
            out.add(new ProfileRow(new ProfileTimestamp(ts), active, reactive, rawHex));
        }
        return out;
    }

    private Double getNumeric(List<Object> row, int idx) {
        if (idx >= row.size()) return null;
        Object v = row.get(idx);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.valueOf(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

//    private String buildRawHex(List<Object> row) {
//        StringBuilder sb = new StringBuilder();
//        for (Object o : row) {
//            if (o instanceof byte[] b) {
//
//                sb.append(o).append(' ');
//            } else {
//                sb.append(o).append(' ');
//            }
//        }
//        return sb.toString().trim();
//    }



    private String buildRawHex(List<Object> row) {
        StringBuilder sb = new StringBuilder();
        for (Object o : row) {
            if (o instanceof byte[] b) {
                sb.append(Arrays.toString(b)).append(' ');
            } else if (o instanceof GXDateTime gxdt) {
                Date date = gxdt.getValue();
                LocalDateTime ldt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                sb.append(STANDARD_DATE_FORMATTER.format(ldt)).append(' ');
            } else if (o instanceof LocalDateTime dt) {
                sb.append(STANDARD_DATE_FORMATTER.format(dt)).append(' ');
            } else if (o instanceof ZonedDateTime zdt) {
                sb.append(STANDARD_DATE_FORMATTER.format(zdt.toLocalDateTime())).append(' ');
            } else if (o instanceof Date d) {
                sb.append(STANDARD_DATE_FORMATTER.format(d.toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime())).append(' ');
            } else {
                sb.append(o).append(' ');
            }
        }
        return sb.toString().trim();
    }


    private String toHex(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format("%02X", b));
        return sb.toString();
    }


//    private List<ProfileRow> mapBufferToRows(GXDLMSProfileGeneric profile) {
//        List<List<Object>> buffer = profile.getBuffer();
//        return mapRawLists(buffer);
//    }

    private Double toDouble(List<Object> row, int idx) {
        if (idx >= row.size()) return null;
        Object v = row.get(idx);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.valueOf(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private GXDateTime toGX(LocalDateTime ldt) {
        return new GXDateTime(java.util.Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()));
    }

    public List<ModelProfileMetadata> loadFromMeterAndPersist(String sampleSerial, String meterModel, String profileObis) {

        try {
            GXDLMSClient client = sessionManager.getOrCreateClient(sampleSerial);
            if (client == null) throw new IllegalStateException("DLMS session not available");

            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(profileObis);

            // ── 1. Read capture-objects list ──────────────────────────────────────
            byte[][] req = client.read(profile, 3);
            GXReplyData rep = dlmsReaderUtils.readDataBlock(client, sampleSerial, req[0]);
            client.updateValue(profile, 3, rep.getValue());

            List<ModelProfileMetadata> rows = new ArrayList<>();

            for (var coEntry : profile.getCaptureObjects()) {
                GXDLMSObject obj = coEntry.getKey();
                GXDLMSCaptureObject co = coEntry.getValue();

                double scaler = 1.0;
                String unit = "N/A";

                // ── 2. If Register-like, read attribute-3 once to get scaler/unit
                if (obj instanceof GXDLMSRegister) {
                    dlmsReaderUtils.readScalerUnit(client, sampleSerial, obj, 3);
                    scaler = DlmsScalerUnitHelper.extractScaler(obj);
                    unit = DlmsScalerUnitHelper.extractUnit(obj);
                }

                if (obj instanceof GXDLMSExtendedRegister || obj instanceof GXDLMSDemandRegister) {
                    dlmsReaderUtils.readScalerUnit(client, sampleSerial, obj, 4);
                    scaler = DlmsScalerUnitHelper.extractScaler(obj);
                    unit = DlmsScalerUnitHelper.extractUnit(obj);
                }

                ModelProfileMetadata row = ModelProfileMetadata.builder().meterModel(meterModel).profileObis(profileObis).captureObis(obj.getLogicalName()).classId(obj.getObjectType().getValue()).attributeIndex(co.getAttributeIndex()).scaler(scaler).unit(unit).build();

                rows.add(row);
            }

            repo.saveAll(rows);          // ③ Persist once
            log.info("💾 Saved {} metadata rows for model={} profile={}", rows.size(), meterModel, profileObis);
            return rows;


        } catch (AssociationLostException ex) {
            sessionManager.removeSession(sampleSerial);
            log.error("Association lost with meter number: {}", sampleSerial);
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("❌ Metadata load failed", ex);
            return Collections.emptyList();
        }
    }

    public void sendDisconnectRequest(String meterSerial, GXDLMSClient dlmsClient) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, IOException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException, InterruptedException {
        dlmsReaderUtils.sendDisconnectRequest(meterSerial, dlmsClient);
    }

}
