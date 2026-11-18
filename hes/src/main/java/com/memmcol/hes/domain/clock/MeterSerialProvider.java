package com.memmcol.hes.domain.clock;

import java.util.List;
import java.util.UUID;

public interface MeterSerialProvider {

    List<String> getMeterSerials();

    UUID getId();
    String getMeterNumber();
}
