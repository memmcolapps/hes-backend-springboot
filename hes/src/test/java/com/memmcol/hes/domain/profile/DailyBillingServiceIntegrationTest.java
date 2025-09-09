package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.CapturePeriodPort;
import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.entities.DailyBillingProfileEntity;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.repository.DailyBillingProfileRepository;
import org.hibernate.annotations.common.reflection.MetadataProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class DailyBillingServiceIntegrationTest {

    // Start a PostgreSQL container for the test
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    // Wire container properties into Spring Boot datasource
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("TEST_DB_URL", postgres::getJdbcUrl);
        registry.add("TEST_DB_USERNAME", postgres::getUsername);
        registry.add("TEST_DB_PASSWORD", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update"); // or validate if schema SQL is applied
    }

    @Autowired
    private DailyBillingService dailyBillingService;

    @Autowired
    private DailyBillingProfileRepository dailyBillingRepository;

    @SpyBean
    private DlmsReaderUtils dlmsReaderUtils;

    @MockitoBean
    private ProfileMetadataProvider metadataProvider;

    @MockitoBean
    private ProfileStatePort statePort;

    @MockitoBean
    private ProfileTimestampPortImpl timestampPort;
    @MockitoBean
    private CapturePeriodPort capturePeriodPort;

    @Test
    void testReadProfileAndSave_PersistsToDB() throws Exception {
        // Arrange
        String meterSerial = "12345";
        String profileObis = "1.0.99.1.0.255";
        String model = "GENERIC";

        final List<String> DAILY_BILLING_OBIS = List.of(
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

        // Mock profile metadata (minimal valid object)
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

        // Act
        dailyBillingService.readProfileAndSave(model, meterSerial, profileObis, 1);

        // Assert → check DB has persisted something
        List<DailyBillingProfileEntity> results = dailyBillingRepository.findAll();
//        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMeterSerial()).isEqualTo(meterSerial);
    }
}
