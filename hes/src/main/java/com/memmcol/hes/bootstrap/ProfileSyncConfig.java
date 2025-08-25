package com.memmcol.hes.bootstrap;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import com.memmcol.hes.application.port.out.*;
import com.memmcol.hes.domain.profile.ChannelTwoService;
import com.memmcol.hes.infrastructure.dlms.ProfileMetadataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 5. Spring Configuration to Wire the Application Service
 */
@Configuration
public class ProfileSyncConfig {

    @Bean
    ProfileSyncUseCase profileSyncUseCase(ProfileStatePort statePort,
                                          Map<String, ProfileDataReaderPort> readerPort,
                                          Map<String, PartialProfileRecoveryPort> recoveryPort,
                                          ProfilePersistencePort persistencePort,
                                          MeterLockPort lockPort,
                                          ProfileMetricsPort metricsPort,
                                          CapturePeriodPort capturePeriodPort,
                                          ProfileTimestampPort timestampPort,
                                          ProfileMetadataProvider metadataProvider) {
        return new ChannelTwoService(
                statePort, readerPort, recoveryPort,
                persistencePort, lockPort, metricsPort,
                capturePeriodPort,  timestampPort, metadataProvider
        );
    }
}
