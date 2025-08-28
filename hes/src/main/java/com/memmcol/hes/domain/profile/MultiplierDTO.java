package com.memmcol.hes.domain.profile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class MultiplierDTO {
    private final Double scaler;
    private final String purpose;
    private final String description;
}
