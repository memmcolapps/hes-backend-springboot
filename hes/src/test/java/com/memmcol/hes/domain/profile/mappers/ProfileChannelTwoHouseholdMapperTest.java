package com.memmcol.hes.domain.profile.mappers;

import com.memmcol.hes.domain.profile.ObisObjectType;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.domain.profile.strategy.MeterProfileStrategyFactory;
import com.memmcol.hes.domain.profile.strategy.MultiPhaseMeterStrategy;
import com.memmcol.hes.domain.profile.strategy.SinglePhaseMeterStrategy;
import com.memmcol.hes.dto.ProfileChannelTwoHouseholdDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.service.MeterRatioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProfileChannelTwoHouseholdMapperTest {

    @Mock
    private MeterRatioService ratioService;

    @Mock
    private DlmsTimestampDecoder dlmsTimestampDecoder;

    private ProfileChannelTwoHouseholdMapper mapper;

    @BeforeEach
    void setUp() {
        MeterProfileStrategyFactory strategyFactory = new MeterProfileStrategyFactory(
                List.of(new SinglePhaseMeterStrategy(), new MultiPhaseMeterStrategy())
        );
        mapper = new ProfileChannelTwoHouseholdMapper(ratioService, dlmsTimestampDecoder, strategyFactory);
    }

    @Test
    @DisplayName("Single-Phase Meter (62525) with 3 profile items maps and normalizes missing current_line_2 without column mismatch exception")
    void testSinglePhaseMeterMapping() throws Exception {
        String meterSerial = "625250000001";
        String modelNumber = "LD_SINGLE_PHASE";

        // Metadata defining OBIS codes for profile items
        List<ModelProfileMetadata> metadataList = List.of(
                ModelProfileMetadata.builder().captureObis("0.0.1.0.0.255").columnName("entry_timestamp").type(ObisObjectType.CLOCK).scaler(1.0).captureIndex(1).build(),
                ModelProfileMetadata.builder().captureObis("1.0.32.7.0.255").columnName("voltage_l1").type(ObisObjectType.SCALER).scaler(0.1).captureIndex(2).build(),
                ModelProfileMetadata.builder().captureObis("1.0.31.7.0.255").columnName("current_l1").type(ObisObjectType.SCALER).scaler(0.01).captureIndex(3).build(),
                ModelProfileMetadata.builder().captureObis("1.0.51.7.0.255").columnName("current_l2").type(ObisObjectType.SCALER).scaler(0.01).captureIndex(4).build()
        );
        ProfileMetadataResult metadataResult = new ProfileMetadataResult(metadataList);

        // Raw row for 62525 meter returning only 3 items (missing current_l2)
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("0.0.1.0.0.255-2", LocalDateTime.of(2025, 5, 20, 10, 0, 0));
        values.put("1.0.32.7.0.255-2", 2300); // 230.0 V
        values.put("1.0.31.7.0.255-2", 500);  // 5.0 A

        ProfileRowGeneric rawRow = new ProfileRowGeneric(Instant.now(), meterSerial, "1.0.99.1.0.255", values);

        List<ProfileChannelTwoHouseholdDTO> dtos = mapper.toDTO(List.of(rawRow), meterSerial, modelNumber, false, metadataResult);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());

        ProfileChannelTwoHouseholdDTO dto = dtos.get(0);
        assertEquals(meterSerial, dto.getMeterSerial());
        assertEquals(230.0, dto.getVoltageL1());
        assertEquals(5.0, dto.getCurrentL1());
        assertNull(dto.getCurrentL2(), "current_l2 should be normalized to null for single phase 62525 meter");
    }

    @Test
    @DisplayName("Multi-Phase Meter (62526) with 4 profile items maps all phase values successfully")
    void testMultiPhaseMeterMapping() throws Exception {
        String meterSerial = "625260000001";
        String modelNumber = "LD_MULTI_PHASE";

        List<ModelProfileMetadata> metadataList = List.of(
                ModelProfileMetadata.builder().captureObis("0.0.1.0.0.255").columnName("entry_timestamp").type(ObisObjectType.CLOCK).scaler(1.0).captureIndex(1).build(),
                ModelProfileMetadata.builder().captureObis("1.0.32.7.0.255").columnName("voltage_l1").type(ObisObjectType.SCALER).scaler(0.1).captureIndex(2).build(),
                ModelProfileMetadata.builder().captureObis("1.0.31.7.0.255").columnName("current_l1").type(ObisObjectType.SCALER).scaler(0.01).captureIndex(3).build(),
                ModelProfileMetadata.builder().captureObis("1.0.51.7.0.255").columnName("current_l2").type(ObisObjectType.SCALER).scaler(0.01).captureIndex(4).build()
        );
        ProfileMetadataResult metadataResult = new ProfileMetadataResult(metadataList);

        // Raw row for 62526 meter returning 4 items (including current_l2)
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("0.0.1.0.0.255-2", LocalDateTime.of(2025, 5, 20, 10, 0, 0));
        values.put("1.0.32.7.0.255-2", 2300); // 230.0 V
        values.put("1.0.31.7.0.255-2", 500);  // 5.0 A
        values.put("1.0.51.7.0.255-2", 450);  // 4.5 A

        ProfileRowGeneric rawRow = new ProfileRowGeneric(Instant.now(), meterSerial, "1.0.99.1.0.255", values);

        List<ProfileChannelTwoHouseholdDTO> dtos = mapper.toDTO(List.of(rawRow), meterSerial, modelNumber, false, metadataResult);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());

        ProfileChannelTwoHouseholdDTO dto = dtos.get(0);
        assertEquals(meterSerial, dto.getMeterSerial());
        assertEquals(230.0, dto.getVoltageL1());
        assertEquals(5.0, dto.getCurrentL1());
        assertEquals(4.5, dto.getCurrentL2());
    }
}
