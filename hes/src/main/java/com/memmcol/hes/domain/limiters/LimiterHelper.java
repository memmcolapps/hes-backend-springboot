package com.memmcol.hes.domain.limiters;

import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.Unit;
import gurux.dlms.objects.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@AllArgsConstructor
@Slf4j
public class LimiterHelper {

    private final DlmsReaderUtils dlmsReaderUtils;

    @Getter
    @Setter
    public static class Threshold {
        private final double actualValue;
        private final double scaledValue;
        private final String unit_str;
        private final Unit unit;

        public Threshold(double actualValue, double scaledValue, String unit_str, Unit unit) {
            this.actualValue = actualValue;
            this.scaledValue = scaledValue;
            this.unit_str = unit_str;
            this.unit = unit;
        }

        @Override
        public String toString() {
            return "Actual Value: "+ actualValue + ", Scaled Value: " + scaledValue + ", Unit_str: " + unit_str + ", Unit: " + unit;
        }
    }

    /**
     * Returns the scaled threshold value with unit for a given limiter and threshold type.
     *
     * @param limiter The GXDLMSLimiter object
     * @param thresholdType The type of threshold: "active", "normal", or "emergency"
     * @return Threshold object containing value and unit
     */
    public Threshold getScaledThresholdWithUnit(GXDLMSLimiter limiter, String thresholdType, GXDLMSClient client, String meterSerial, int attributeIndex) throws Exception {
        if (limiter == null) throw new IllegalArgumentException("Limiter cannot be null");

        Object result;
        double LimiterValue = 0.0;
//        // Read value
        result = dlmsReaderUtils.readAttribute(client, meterSerial, limiter, attributeIndex);
        if (result instanceof Number) {
            LimiterValue = BigDecimal.valueOf(((Number) result).doubleValue())
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        Object rawThreshold;
        switch (thresholdType.toLowerCase()) {
            case "active":
                rawThreshold = limiter.getThresholdActive();
                break;
            case "normal":
                rawThreshold = limiter.getThresholdNormal();
                break;
            case "emergency":
                rawThreshold = limiter.getThresholdEmergency();
                break;
            default:
                throw new IllegalArgumentException("Unknown threshold type: " + thresholdType);
        }

        if (rawThreshold == null) return new Threshold(Double.NaN, Double.NaN, "Unknown", Unit.NO_UNIT);

        GXDLMSObject monitored = limiter.getMonitoredValue();
        int index = limiter.getmonitoredAttributeIndex();
        double scaler = 0.0;
        String unit_str = "Unknown";
        Unit unit = Unit.NO_UNIT;

        if (monitored instanceof GXDLMSRegister reg) {
            // Read Scaler+Unit first
            dlmsReaderUtils.readScalerUnit(client, meterSerial, reg, 3);
            scaler = reg.getScaler();
            unit = reg.getUnit();
            unit_str = dlmsReaderUtils.getUnitSymbol(unit);
        } else if (monitored instanceof GXDLMSExtendedRegister extReg) {
            dlmsReaderUtils.readScalerUnit(client, meterSerial, extReg, index);
            scaler = extReg.getScaler();
            unit_str = extReg.getUnit() != null ? String.valueOf(extReg.getUnit()) : unit_str;
        } else if (monitored instanceof GXDLMSDemandRegister demandReg) {
            dlmsReaderUtils.readScalerUnit(client, meterSerial, demandReg, index);
            scaler = demandReg.getScaler();
            unit_str = demandReg.getUnit() != null ? String.valueOf(demandReg.getUnit()) : unit_str;
        } else if (monitored instanceof GXDLMSLimiter) {
            // Nested limiter (rare) → fallback unit
            unit_str = "Units";
        } // Extend with other monitored object types as needed

        double value = ((Number) rawThreshold).doubleValue();
        double scaledValue = value * Math.pow(10, scaler);
        LimiterValue = LimiterValue * Math.pow(10, scaler);

        log.info("Limiter value: {}, scaledValue: {}, unit: {}", LimiterValue, scaledValue, unit_str);

        return new Threshold(LimiterValue, scaledValue, unit_str, unit);
    }
}
