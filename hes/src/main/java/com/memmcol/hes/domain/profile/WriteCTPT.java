package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
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

            // CT numerator
            GXDLMSData ctNum = new GXDLMSData("1.0.0.4.2.255");
            dlmsReaderUtils.writeAttribute(client, meterSerial, ctNum, 2, ctNumerator);
            result.put("ctNumerator", ctNumerator);

            // CT denominator
            GXDLMSData ctDen = new GXDLMSData("1.0.0.4.5.255");
            dlmsReaderUtils.writeAttribute(client, meterSerial, ctDen, 2, ctDenominator);
            result.put("ctDenominator", ctDenominator);

            // PT numerator
            GXDLMSData ptNum = new GXDLMSData("1.0.0.4.3.255");
            dlmsReaderUtils.writeAttribute(client, meterSerial, ptNum, 2, ptNumerator);
            result.put("ptNumerator", ptNumerator);

            // PT denominator
            GXDLMSData ptDen = new GXDLMSData("1.0.0.4.6.255");
            dlmsReaderUtils.writeAttribute(client, meterSerial, ptDen, 2, ptDenominator);
            result.put("ptDenominator", ptDenominator);

            log.info("✅ CT/PT written meter={} ct={}/{} pt={}/{}",
                    meterSerial, ctNumerator, ctDenominator, ptNumerator, ptDenominator);

            return result;
        });
    }
}

