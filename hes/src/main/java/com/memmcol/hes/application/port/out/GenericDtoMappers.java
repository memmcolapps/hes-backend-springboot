package com.memmcol.hes.application.port.out;

import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.model.ProfileChannel2ReadingDTO;

import java.math.BigDecimal;
import java.util.List;

public interface GenericDtoMappers<T> {

    List<T> toDTO(List<ProfileRowGeneric> rawRows,
                                                 String meterSerial,
                                                 String modelNumber,
                                                 boolean mdMeter,
                                                 ProfileMetadataResult captureObjects) throws Exception;


    T mapRow(ProfileRowGeneric raw,
                    String meterSerial,
                    String modelNumber,
                    boolean mdMeter,
                    ProfileMetadataResult captureObjects,
                    MeterRatios meterRatios);

    void setDtoField(T dto, String columnName, BigDecimal value);
}
