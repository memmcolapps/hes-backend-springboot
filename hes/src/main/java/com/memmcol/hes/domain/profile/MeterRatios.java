package com.memmcol.hes.domain.profile;


import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class MeterRatios {
    private final Integer ctRatio;
    private final Integer ptRatio;
    private final Integer ctptRatio;

    public MeterRatios(Integer ctRatio, Integer ptRatio, Integer ctptRatio) {
        this.ctRatio = ctRatio;
        this.ptRatio = ptRatio;
        this.ctptRatio = ctptRatio;
    }

    @Override
    public String toString() {
        return String.format("CT: %s, PT: %s, CTPT: %s", ctRatio, ptRatio, ctptRatio);
    }
}