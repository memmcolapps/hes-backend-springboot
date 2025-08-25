package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class MetersLockService {


    private final MeterLockPort lockPort;
    private final ProfileMetricsPort metricsPort;
    private final ProfileChannelOneServiceExtension channelOneServiceExtension;
    private final MonthlyBillingService monthlyBillingService;

    public void readChannelOneWithLock(String model, String meterSerial, String profileObis, int batchSize) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                channelOneServiceExtension.readProfileAndSave(model, meterSerial, profileObis, batchSize);
                log.info("Profile reading completed or aborted. meter={} profile={}", meterSerial, profileObis);
                return null;
            });
        } catch (IllegalStateException e2) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e2.getMessage(), e2);
            assert metricsPort != null;
            metricsPort.recordFailure(meterSerial, profileObis, "Server restarted");
        } catch (Exception e) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e.getMessage(), e);
            assert metricsPort != null;
            metricsPort.recordFailure(meterSerial, profileObis, "lock_or_sync_error");
        }
    }

    public void readMonthlyBillWithLock(String model, String meterSerial, String profileObis, int batchSize) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                monthlyBillingService.readProfileAndSave(model, meterSerial, profileObis, batchSize);
                log.info("Profile reading completed or aborted. meter={} profile={}", meterSerial, profileObis);
                return null;
            });
        } catch (IllegalStateException e2) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e2.getMessage(), e2);
            assert metricsPort != null;
            metricsPort.recordFailure(meterSerial, profileObis, "Server restarted");
        } catch (Exception e) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e.getMessage(), e);
            assert metricsPort != null;
            metricsPort.recordFailure(meterSerial, profileObis, "lock_or_sync_error");
        }
    }


}
