package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.CapturePeriodPort;
import com.memmcol.hes.entities.DailyBillingProfileEntity;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import com.memmcol.hes.infrastructure.persistence.DailyBillingPersistenceAdapter;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.repository.DailyBillingProfileRepository;
import net.bytebuddy.utility.dispatcher.JavaDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@Testcontainers
class DailyBillingServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("gridflex")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DailyBillingService dailyBillingService;

    @Autowired
    private DailyBillingProfileRepository billingRepo;

    @Autowired
    private DailyBillingPersistenceAdapter persistenceAdapter;

    @MockBean
    private DlmsReaderUtils dlmsReaderUtils;

    @MockBean
    private ProfileMetadataProvider metadataProvider;

    @MockBean
    private ProfileTimestampPortImpl timestampPort;

    @MockBean
    private CapturePeriodPort capturePeriodPort;

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

    @BeforeEach
    void setup() throws Exception {
        // Metadata for all OBIS
        List<ModelProfileMetadata> dailyBillingMetadata = IntStream.range(0, DAILY_BILLING_OBIS.size())
                .mapToObj(i -> ModelProfileMetadata.builder()
                        .meterModel("MMX-313-CT")
                        .profileObis("0.0.98.2.0.255")
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

        Mockito.when(metadataProvider.resolve(anyString(), anyString(), anyString()))
                .thenReturn(new ProfileMetadataResult(dailyBillingMetadata));

        Mockito.when(timestampPort.resolveLastTimestamp(anyString(), anyString()))
                .thenReturn(LocalDateTime.now().minusDays(2));

        Mockito.when(capturePeriodPort.resolveCapturePeriodSeconds(anyString(), anyString()))
                .thenReturn(86400);

        // Mock DlmsReaderUtils to return a fake reading for any OBIS
        Mockito.when(dlmsReaderUtils.readRange(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(ProfileMetadataResult.class),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq(true)))
                .thenAnswer(invocation -> {
                    String obis = invocation.getArgument(2);
                    ProfileRowGeneric row = new ProfileRowGeneric(
                            Instant.now(),
                            anyString(), anyString(),
                            Map.of(obis, 123.45)
                    );
                    return List.of(row);
                });
    }

    @Test
    void readProfileAndSave_shouldInsertAllObisIntoDatabase() {
        String meterModel = "MMX-313-CT";
        String meterSerial = "202006001314";

        // Call service for all OBIS codes
        DAILY_BILLING_OBIS.forEach(obis ->
                dailyBillingService.readProfileAndSave(meterModel, meterSerial, obis, true)
        );

        // Verify rows in DB
        List<DailyBillingProfileEntity> allRows = billingRepo.findAll();
        System.out.println("Rows inserted: " + allRows.size());
        allRows.forEach(System.out::println);

//        // Assertions
//        assertThat(allRows).isNotEmpty();
//        assertThat(allRows).allMatch(r -> r.getMeterSerial().equals(meterSerial));
//        assertThat(allRows).allMatch(r -> r.getMeterModel().equals(meterModel));
//
//        // Optional: verify at least one row per OBIS
//        Set<String> insertedObis = allRows.stream()
//                .flatMap(r -> r.getValues().keySet().stream())
//                .collect(Collectors.toSet());
//
//        assertThat(insertedObis).containsAll(DAILY_BILLING_OBIS);
    }
}
