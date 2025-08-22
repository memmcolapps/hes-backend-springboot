package com.memmcol.hes.bootstrap;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.domain.profile.ProfileSyncService;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 5. Spring Configuration to Wire the Application Service
 */
@Configuration
public class ProfileSyncConfig {

    @Bean
    ProfileSyncUseCase profileSyncUseCase(ProfileStatePort statePort,
                                          ProfileDataReaderPort readerPort,
                                          PartialProfileRecoveryPort recoveryPort,
                                          ProfilePersistencePort persistencePort,
                                          MeterLockPort lockPort,
                                          ProfileMetricsPort metricsPort,
                                          CapturePeriodPort capturePeriodPort,
                                          ProfileTimestampPort timestampPort,
                                          ProfileMetadataProvider metadataProvider,
                                          APIClientPort apiClientPort) {
        return new ProfileSyncService(
                statePort, readerPort, recoveryPort,
                persistencePort, lockPort, metricsPort,
                capturePeriodPort,  timestampPort, metadataProvider,
                apiClientPort
        );
    }
}
