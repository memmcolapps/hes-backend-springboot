package com.memmcol.hes.domain.profile;


/*
* Let’s write a comprehensive unit test for your readProfileAndSave method using JUnit 5 + Mockito. This will cover:
	•	Cursor advancement
	•	Batch handling
	•	Exception recovery
	•	DTO mapping
	•	Persistence call verification
	•	Metrics recording
	*
	* ✅ Key Points in This Unit Test
	1.	Cursor Logic:
	•	Last timestamp and batch advancement are verified via mocks.
	2.	Batch Processing:
	•	Simulates 1-day batch and ensures DTOs are mapped and saved.
	3.	Exception Recovery:
	•	Tests that attemptRecovery() is called when dlmsReaderUtils.readRange() fails.
	4.	Metrics & State Recording:
	•	Verifies metricsPort.recordBatch() and statePort.upsertState() are invoked.
	5.	Isolation:
	•	No real meter or database is used. Everything is mocked.
* */

import com.memmcol.hes.application.port.out.CapturePeriodPort;
import com.memmcol.hes.application.port.out.ProfileMetricsPort;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.application.port.out.ProfileTimestampPort;
import com.memmcol.hes.domain.profile.mappers.DailyBillingMapper;
import com.memmcol.hes.dto.DailyBillingProfileDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.DailyBillingPersistenceAdapter;
import com.memmcol.hes.model.ModelProfileMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DailyBillingProfileServiceTest {
    private static final List<String> DAILY_BILLING_OBIS = List.of(
            "0.0.1.0.0.255",
            "1.0.15.8.0.255",
            "1.0.15.8.1.255",
            "1.0.15.8.2.255",
            "1.0.15.8.3.255",
            "1.0.15.8.4.255",
            "1.0.129.8.0.255",
            "1.0.129.8.1.255",
            "1.0.129.8.3.255",
            "1.0.129.8.4.255"
    );

    List<ModelProfileMetadata> dailyBillingMetadata = IntStream.range(0, DAILY_BILLING_OBIS.size())
            .mapToObj(i -> ModelProfileMetadata.builder()
                    .meterModel("MMX-313-CT")
                    .profileObis("0.0.1.0.0.255")   // Same parent profile for all
                    .captureObis(DAILY_BILLING_OBIS.get(i))
                    .classId(7)
                    .attributeIndex(2)
                    .scaler(1.0)
                    .unit("kWh")
                    .description("OBIS " + DAILY_BILLING_OBIS.get(i))
                    .captureIndex(i + 1)
                    .columnName("col_" + i)
                    .multiplyBy("CTPT")
                    .type(ObisObjectType.SCALER)
                    .build())
            .toList();

    @Mock
    private ProfileTimestampPortImpl timestampPort;
    @Mock
    private CapturePeriodPort capturePeriodPort;
    @Mock
    private ProfileMetadataProvider metadataProvider;
    @Mock
    private DlmsReaderUtils dlmsReaderUtils;
    @Mock
    private DailyBillingMapper dailyBillingMapper;
    @Mock
    private DailyBillingPersistenceAdapter dailyBillingPersistenceAdapter;
    @Mock
    private ProfileMetricsPort metricsPort;
    @Mock
    private ProfileStatePort statePort;

    @Spy
    @InjectMocks
    private DailyBillingService service;

    @Test
    void readProfileAndSave_shouldAdvanceCursorAndSaveDTOs() throws Exception {
        // Arrange
        LocalDateTime lastTimestamp = LocalDateTime.now().minusDays(2);
        when(timestampPort.resolveLastTimestamp(anyString(), anyString())).thenReturn(lastTimestamp);
        when(capturePeriodPort.resolveCapturePeriodSeconds(anyString(), anyString())).thenReturn(86400);

        when(metadataProvider.resolve(anyString(), anyString(), anyString()))
                .thenReturn(new ProfileMetadataResult(dailyBillingMetadata));

        when(dlmsReaderUtils.readRange(anyString(), anyString(), anyString(), any(), any(), any(), eq(true)))
                .thenReturn(List.of(
                        new ProfileRowGeneric(
                                Instant.now(),
                                anyString(), anyString(),
                                Map.of("1.0.15.8.0.255", 123.45) // sample OBIS → value
                        )
                ));

        // Properly return DTO
        when(dailyBillingMapper.toDTO(anyList(), anyString(), anyString(), eq(true), any()))
                .thenReturn(List.of(
                        DailyBillingProfileDTO.builder()
                                .meterSerial("202006001314")
                                .meterModel("MMX-313-CT")
                                .entryTimestamp(lastTimestamp.plusDays(1))
                                .totalActiveEnergy(123.45)
                                .build()
                ));
        // Properly return ProfileSyncResult with real values
        when(dailyBillingPersistenceAdapter.saveBatchAndAdvanceCursor(anyString(), anyString(), anyList(), any()))
                .thenReturn(new ProfileSyncResult(
                        1, 1, 0,
                        lastTimestamp.plusDays(1),
                        LocalDateTime.now(),   // instead of any()
                        LocalDateTime.now(),   // instead of any()
                        true
                ));
        // Act
        DAILY_BILLING_OBIS.forEach(obis -> service.readProfileAndSave("MMX-313-CT", "202006001314", obis, 1));

        // Assert
        verify(dlmsReaderUtils, atLeast(DAILY_BILLING_OBIS.size()))
                .readRange(anyString(), anyString(), anyString(), any(), any(), any(), eq(true));
        verify(dailyBillingMapper, atLeast(DAILY_BILLING_OBIS.size()))
                .toDTO(anyList(), anyString(), anyString(), eq(true), any());
        verify(dailyBillingPersistenceAdapter, atLeast(DAILY_BILLING_OBIS.size()))
                .saveBatchAndAdvanceCursor(anyString(), anyString(), anyList(), any());
        verify(metricsPort, atLeastOnce()).recordBatch(anyString(), anyString(), anyInt(), anyLong());
        verify(statePort, atLeastOnce()).upsertState(anyString(), anyString(), any(), any());
    }



    @Test
    void readProfileAndSave_shouldHandleReadExceptionGracefully() throws Exception {
        // Arrange
        LocalDateTime lastTimestamp = LocalDateTime.now().minusDays(1);

        when(timestampPort.resolveLastTimestamp(anyString(), anyString()))
                .thenReturn(lastTimestamp);

        when(capturePeriodPort.resolveCapturePeriodSeconds(anyString(), anyString()))
                .thenReturn(86400);

        when(metadataProvider.resolve(anyString(), anyString(), anyString()))
                .thenReturn(new ProfileMetadataResult(dailyBillingMetadata));

        when(dlmsReaderUtils.readRange(
                anyString(), anyString(), anyString(),
                any(), any(), any(), eq(true)))
                .thenThrow(new RuntimeException("Simulated meter failure"));

        doReturn(List.of(new ProfileRowGeneric(
                Instant.now(),
                anyString(), anyString(),
                Map.of("1.0.15.8.0.255", 123.45)
        )))
                .when(service).attemptRecovery(anyString(), anyString(), anyString(), any());

        when(dailyBillingMapper.toDTO(anyList(), anyString(), anyString(), eq(true), any()))
                .thenReturn(List.of(new DailyBillingProfileDTO()));

        // ✅ FIXED: don’t use matchers inside constructor
        ProfileSyncResult fakeResult =
                new ProfileSyncResult(1, 1, 0, lastTimestamp.plusDays(1), null, null, true);

        when(dailyBillingPersistenceAdapter.saveBatchAndAdvanceCursor(
                anyString(), anyString(), anyList(), any()))
                .thenReturn(fakeResult);

        // Act
        for (String obis : DAILY_BILLING_OBIS) {
            service.readProfileAndSave("MMX-313-CT", "202006001314", obis, 1);
        }

        // Assert
        verify(service, atLeast(1))
                .attemptRecovery(anyString(), anyString(), anyString(), any());

        verify(dailyBillingPersistenceAdapter, atLeast(1))
                .saveBatchAndAdvanceCursor(anyString(), anyString(), anyList(), any());
    }
}
