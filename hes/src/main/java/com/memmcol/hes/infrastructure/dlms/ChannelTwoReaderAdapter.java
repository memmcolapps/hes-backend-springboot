package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.application.port.out.PartialProfileRecoveryPort;
import com.memmcol.hes.application.port.out.ProfileDataReaderPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRow;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.exception.AssociationLostException;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.service.*;
import gurux.dlms.*;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import gurux.dlms.objects.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 4. Infrastructure Adapters (Skeletons)
 * 4.1 Gurux Reader Adapter
 */
@Component("channelTwoReaderAdapter")
@Slf4j
@RequiredArgsConstructor
public class ChannelTwoReaderAdapter implements ProfileDataReaderPort<ProfileRow>, PartialProfileRecoveryPort<ProfileRow> {

    private final SessionManager sessionManager;
    private final DlmsPartialDecoder partialDecoder;
    private final DlmsTimestampDecoder timestampDecoder;
    private final TxRxService txRxService;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter STANDARD_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    @Override
    public List<ProfileRow> readRange(String model, String meterSerial, String profileObis, ProfileMetadataResult metadataResult, LocalDateTime from, LocalDateTime to) throws ProfileReadException {
        long t0 = System.currentTimeMillis();
        partialDecoder.clear(meterSerial, profileObis);

        try {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("DLMS client not available for " + meterSerial);
            }
            GXDLMSProfileGeneric profile = new GXDLMSProfileGeneric();
            profile.setLogicalName(profileObis);

            GXDateTime gxFrom = new GXDateTime(Date.from(from.atZone(ZONE).toInstant()));
            GXDateTime gxTo = new GXDateTime(Date.from(to.atZone(ZONE).toInstant()));

            log.info("Building DLMS range request model ={} meter={} obis={} from={} to={}", model, meterSerial, profileObis, from, to);

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
            byte[] resp1 = txRxService.sendReceiveWithContext(meterSerial, firstFrame, 20000);
            if (sessionManager.isAssociationLost(resp1)) {
                sessionManager.removeSession(meterSerial);
                throw new AssociationLostException("Association lost on first frame");
            }
            client.getData(resp1, reply, null);
            updateProfileBufferOnBlock(client, profile, profileObis, meterSerial, reply);

            // --- Multi-block loop
            while (reply.isMoreData()) {
                byte[] nextReq = client.receiverReady(reply);
                if (nextReq == null) break;

                byte[] resp = txRxService.sendReceiveWithContext(meterSerial, nextReq, 20000);
                if (sessionManager.isAssociationLost(resp)) {
                    sessionManager.removeSession(meterSerial);
                    throw new AssociationLostException("Association lost during multi-block read");
                }
                client.getData(resp, reply, null);
                updateProfileBufferOnBlock(client, profile, profileObis, meterSerial, reply);
            }

            List<ProfileRow> rows = decodeProfileBuffer(profile, meterSerial, profileObis);
            partialDecoder.clear(meterSerial, profileObis);

            long ms = System.currentTimeMillis() - t0;
            log.info("Range read complete meter={} obis={} rows={} elapsedMs={}", meterSerial, profileObis, rows.size(), ms);
            return rows;

        } catch (Exception ex) {
            long ms = System.currentTimeMillis() - t0;
            log.warn("Range read FAILED meter={} obis={} elapsedMs={} cause={}", meterSerial, profileObis, ms, ex.getMessage());
            throw new ProfileReadException("Range read failed", ex);
        }
    }

    private void updateProfileBufferOnBlock(GXDLMSClient client, GXDLMSProfileGeneric profile, String profileObis, String meterSerial, GXReplyData reply) {
        Object val = reply.getValue();
        if (val == null) val = reply.getData();

        try {
            if (val instanceof GXByteBuffer buf) {
                GXDataInfo info = new GXDataInfo();
                buf.position(0);
                val = GXCommon.getData(null, buf, info);
            }

            if (val != null) {
                client.updateValue(profile, 2, val);
                List<List<Object>> buf = partialDecoder.normalizeProfileBuffer(profile.getBuffer());
                if (buf != null && !buf.isEmpty()) {
                    partialDecoder.accumulate(meterSerial, profileObis, buf);
                }
            }
        } catch (Exception e) {
            log.debug("Silent buffer update issue (not fatal) meter={} obis={} msg={}", meterSerial, profileObis, e.getMessage());
        }
    }

    private List<ProfileRow> decodeProfileBuffer(GXDLMSProfileGeneric profile, String meterSerial, String profileObis) {
        List<List<Object>> raw = partialDecoder.normalizeProfileBuffer(profile.getBuffer());
        if (raw == null || raw.isEmpty()) return List.of();
        return mapRawLists(raw, meterSerial, profileObis);
    }

    private List<ProfileRow> mapRawLists(List<List<Object>> raw, String meterSerial, String profileObis) {
        List<ProfileRow> out = new ArrayList<>();
        int rowIndex = 0;

        for (List<Object> row : raw) {
            rowIndex++;
            if (row == null || row.isEmpty()) continue;

            LocalDateTime ts = timestampDecoder.decodeTimestamp(row.get(0));
            if (ts == null) {
                log.debug("Skipping row {} (no timestamp) meter={} obis={}", rowIndex, meterSerial, profileObis);
                continue;
            }
            Double active = getNumeric(row, 1);
            Double reactive = getNumeric(row, 2);
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
}
