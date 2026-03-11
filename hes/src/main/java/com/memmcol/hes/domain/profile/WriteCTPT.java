package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.enums.DataType;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.objects.GXDLMSData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WriteCTPT {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;

    /**
     * Write CT/PT numerator & denominator values into the meter.
     *
     * OBIS / attrIndex:
     * - CT numerator:      1.0.0.4.2.255 (attr 2)
     * - CT denominator:    1.0.0.4.5.255 (attr 2)
     * - PT numerator:      1.0.0.4.3.255 (attr 2)
     * - PT denominator:    1.0.0.4.6.255 (attr 2)
     */
    public Map<String, Object> writeCtPt(String meterSerial,
                                        long ctNumerator,
                                        long ctDenominator,
                                        long ptNumerator,
                                        long ptDenominator) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);

            // NOTE: CT/PT objects are DLMS Data (classId = 1) with type 'long unsigned' → UINT32.

            // CT numerator: 1.0.0.4.2.255 attr 2
            dlmsReaderUtils.writeAttribute(client, meterSerial, "1.0.0.4.2.255", 1, 2, ctNumerator, DataType.UINT32);
            result.put("ctNumerator", ctNumerator);

            // CT denominator: 1.0.0.4.5.255 attr 2
            dlmsReaderUtils.writeAttribute(client, meterSerial, "1.0.0.4.5.255", 1, 2, ctDenominator, DataType.UINT32);
            result.put("ctDenominator", ctDenominator);

            // PT numerator: 1.0.0.4.3.255 attr 2
            dlmsReaderUtils.writeAttribute(client, meterSerial, "1.0.0.4.3.255", 1, 2, ptNumerator, DataType.UINT32);
            result.put("ptNumerator", ptNumerator);

            // PT denominator: 1.0.0.4.6.255 attr 2
            dlmsReaderUtils.writeAttribute(client, meterSerial, "1.0.0.4.6.255", 1, 2, ptDenominator, DataType.UINT32);
            result.put("ptDenominator", ptDenominator);

            log.info("✅ CT/PT written meter={} ct={}/{} pt={}/{}",
                    meterSerial, ctNumerator, ctDenominator, ptNumerator, ptDenominator);

            return result;
        });
    }
}

