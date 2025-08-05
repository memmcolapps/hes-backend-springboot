package com.memmcol.hes.service;

import gurux.dlms.enums.Unit;
import gurux.dlms.objects.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class DlmsScalerUnitHelper {
    public static double extractScaler(GXDLMSObject obj) {
        double scaler = 1.0;

        if (obj instanceof GXDLMSRegister reg) {
            scaler = reg.getScaler();
            if (isBaseUnit(reg.getUnit())) {
                scaler = scaler * Math.pow(10, -3);  // or just hardcode the adjustment
            }
        } else if (obj instanceof GXDLMSDemandRegister demandReg) {
            scaler = demandReg.getScaler();
            if (isBaseUnit(demandReg.getUnit())) {
                scaler = scaler * Math.pow(10, -3);
            }
        } else if (obj instanceof GXDLMSExtendedRegister extReg) {
            scaler = extReg.getScaler();
            if (isBaseUnit(extReg.getUnit())) {
                scaler = scaler * Math.pow(10, -3);
            }
        }

        return scaler == 0 ? 1.0 : scaler;
    }

    public static String extractUnit(GXDLMSObject obj) {
        if (obj instanceof GXDLMSRegister reg) {
            Unit unit = reg.getUnit();
            return DlmsService.getUnitSymbol(unit);
        }
        if (obj instanceof GXDLMSDemandRegister demandReg) {
            Unit unit = demandReg.getUnit();
            return DlmsService.getUnitSymbol(unit);
        }
        if (obj instanceof GXDLMSExtendedRegister extReg) {
            Unit unit = extReg.getUnit();
            return DlmsService.getUnitSymbol(unit);
        }
        return "N/A";
    }

    private static boolean isBaseUnit(Unit unit) {
        return unit == Unit.ACTIVE_POWER || unit == Unit.REACTIVE_POWER || unit == Unit.APPARENT_POWER
                || unit == Unit.APPARENT_ENERGY || unit == Unit.REACTIVE_ENERGY || unit == Unit.ACTIVE_ENERGY;
    }
}
