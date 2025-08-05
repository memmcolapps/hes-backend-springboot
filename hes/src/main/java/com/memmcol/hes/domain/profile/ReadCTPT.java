package com.memmcol.hes.domain.profile;

import com.memmcol.hes.nettyUtils.RequestResponseService;
import com.memmcol.hes.nettyUtils.SessionManager;
import com.memmcol.hes.service.AssociationLostException;
import com.memmcol.hes.service.MeterConnections;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@AllArgsConstructor
@Slf4j
public class ReadCTPT {
    private final SessionManager sessionManager;

    public MeterRatios readMeterRatios(String model, String meterSerial) throws Exception {
        List<Map.Entry<GXDLMSObject, Integer>> ratioObisList = new ArrayList<>();
        // Prepare GXDLMSData objects
        ratioObisList.add(new AbstractMap.SimpleEntry<>(new GXDLMSData("1.0.0.4.2.255"), 2));  // OBIS: 1.0.0.4.2.255 = CT numerator
        ratioObisList.add(new AbstractMap.SimpleEntry<>(new GXDLMSData("1.0.0.4.5.255"), 2)); // OBIS: 1.0.0.4.4.255 = CT denominator
        ratioObisList.add(new AbstractMap.SimpleEntry<>(new GXDLMSData("1.0.0.4.3.255"), 2)); // OBIS: 1.0.0.4.3.255 = PT numerator
        ratioObisList.add(new AbstractMap.SimpleEntry<>(new GXDLMSData("1.0.0.4.6.255"), 2)); // OBIS: 1.0.0.4.5.255 = PT denominator

        GXDLMSClient client = null;
        try {
            client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("DLMS client not available for " + meterSerial);
            }
            // Prepare read list request
            byte[][] readListReq = client.readList(ratioObisList);
            GXReplyData reply = new GXReplyData();
            byte[] response = RequestResponseService.sendReceiveWithContext(
                    meterSerial,
                    readListReq[0],
                    20000
            );
            if (sessionManager.isAssociationLost(response)) {
                throw new AssociationLostException("Association lost on first frame");
            }
            // Parse the response
            assert response != null;
            client.getData(response, reply, null);

            // Extract list of values
            List<Object> values = (List<Object>) reply.getValue();

            if (values == null || values.size() < 4) {
//                throw new Exception("Failed to read all CT/PT values");
                log.error("Failed to read all CT/PT values");
            }

            // Convert extracted values to BigDecimal
            BigDecimal ctNumerator = getDecimal(values.get(0));
            BigDecimal ctDenominator = getDecimal(values.get(1));
            BigDecimal ptNumerator = getDecimal(values.get(2));
            BigDecimal ptDenominator = getDecimal(values.get(3));

            BigDecimal ctRatio = safeDivide(ctNumerator, ctDenominator);
            BigDecimal ptRatio = safeDivide(ptNumerator, ptDenominator);
            BigDecimal ctptRatio = ctRatio.multiply(ptRatio);

            return new MeterRatios(ctRatio.intValue(),  ptRatio.intValue(), ctptRatio.intValue());

        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    private BigDecimal getDecimal(Object value) {
        try {
            if (value == null) return BigDecimal.ONE;
            if (value instanceof Number) return new BigDecimal(((Number) value).toString());
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ONE;
        }
    }


    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        try {
            if (denominator.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ONE;
            return numerator.divide(denominator, 0, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ONE;
        }
    }

}
