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
    private final ProfileChannelOneHouseholdService channelOneHouseholdService;
    private final MonthlyBillingService monthlyBillingService;
    private final MonthlyBillingDataHouseholdService monthlyBillingDataHouseholdService;
    private final MonthlyBillingEnergyHouseholdService monthlyBillingEnergyHouseholdService;
    private final DailyBillingService dailyBillingService;
    private final DailyBillingDataHouseholdService dailyBillingDataHouseholdService;
    private final DailyBillingEnergyHouseholdService dailyBillingEnergyHouseholdService;
    private final EventLogService eventLogService;
    private final HouseholdTokenEventService householdTokenEventService;
    private final HouseholdExtendedEventService householdExtendedEventService;
    private final ProfileChannelTwoService profileChannelTwoService;
    private final ProfileChannelTwoHouseholdService channelTwoHouseholdService;
    private final ProfileChannelThreeHouseholdService channelThreeHouseholdService;

    public void readChannelOneWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readChannelOneWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readChannelOneWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                channelOneServiceExtension.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
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

    public void readChannelOneHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readChannelOneHouseholdWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readChannelOneHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                channelOneHouseholdService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household profile reading completed or aborted. meter={} profile={}", meterSerial, profileObis);
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

    public void readChannelTwoWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readChannelTwoWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readChannelTwoWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                profileChannelTwoService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
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

    public void readChannelTwoHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readChannelTwoHouseholdWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readChannelTwoHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                channelTwoHouseholdService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household profile reading completed or aborted. meter={} profile={}", meterSerial, profileObis);
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

    public void readChannelThreeHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readChannelThreeHouseholdWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readChannelThreeHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                channelThreeHouseholdService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household profile reading completed or aborted. meter={} profile={}", meterSerial, profileObis);
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

    public void readMonthlyBillWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readMonthlyBillWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readMonthlyBillWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                monthlyBillingService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
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

    public void readMonthlyBillingDataHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readMonthlyBillingDataHouseholdWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readMonthlyBillingDataHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                monthlyBillingDataHouseholdService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household monthly billing data read completed or aborted. meter={} profile={}", meterSerial, profileObis);
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

    public void readMonthlyBillingEnergyHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readMonthlyBillingEnergyHouseholdWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readMonthlyBillingEnergyHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                monthlyBillingEnergyHouseholdService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household monthly billing energy read completed or aborted. meter={} profile={}", meterSerial, profileObis);
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

    public void readDailyBillWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readDailyBillWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readDailyBillWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                dailyBillingService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
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

    public void readDailyBillingDataHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readDailyBillingDataHouseholdWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readDailyBillingDataHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {

            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                dailyBillingDataHouseholdService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household daily billing data read completed or aborted. meter={} profile={}", meterSerial, profileObis);
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

    public void readDailyBillingEnergyHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readDailyBillingEnergyHouseholdWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readDailyBillingEnergyHouseholdWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                dailyBillingEnergyHouseholdService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household daily billing energy read completed or aborted. meter={} profile={}", meterSerial, profileObis);
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

    public void readEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readEventsWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            assert lockPort != null;
            lockPort.withExclusive(meterSerial, () -> {
                eventLogService.readProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Events Profile reading completed or aborted. meter={} profile={}", meterSerial, profileObis);
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

    public void readHouseholdRechargeTokenEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readHouseholdRechargeTokenEventsWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readHouseholdRechargeTokenEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            lockPort.withExclusive(meterSerial, () -> {
                householdTokenEventService.readRechargeProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household recharge token event read completed. meter={} profile={}", meterSerial, profileObis);
                return null;
            });
        } catch (IllegalStateException e2) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e2.getMessage(), e2);
            metricsPort.recordFailure(meterSerial, profileObis, "Server restarted");
        } catch (Exception e) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e.getMessage(), e);
            metricsPort.recordFailure(meterSerial, profileObis, "lock_or_sync_error");
        }
    }

    public void readHouseholdManagementTokenEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readHouseholdManagementTokenEventsWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readHouseholdManagementTokenEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            lockPort.withExclusive(meterSerial, () -> {
                householdTokenEventService.readManagementProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household management token event read completed. meter={} profile={}", meterSerial, profileObis);
                return null;
            });
        } catch (IllegalStateException e2) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e2.getMessage(), e2);
            metricsPort.recordFailure(meterSerial, profileObis, "Server restarted");
        } catch (Exception e) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e.getMessage(), e);
            metricsPort.recordFailure(meterSerial, profileObis, "lock_or_sync_error");
        }
    }

    public void readHouseholdFraudEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readHouseholdFraudEventsWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readHouseholdFraudEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            lockPort.withExclusive(meterSerial, () -> {
                householdExtendedEventService.readFraudProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household fraud event read completed. meter={} profile={}", meterSerial, profileObis);
                return null;
            });
        } catch (IllegalStateException e2) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e2.getMessage(), e2);
            metricsPort.recordFailure(meterSerial, profileObis, "Server restarted");
        } catch (Exception e) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e.getMessage(), e);
            metricsPort.recordFailure(meterSerial, profileObis, "lock_or_sync_error");
        }
    }

    public void readHouseholdControlEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD) {
        readHouseholdControlEventsWithLock(model, meterSerial, profileObis, isMD, null, null);
    }

    public void readHouseholdControlEventsWithLock(String model, String meterSerial, String profileObis, boolean isMD, java.time.LocalDateTime rangeFrom, java.time.LocalDateTime rangeTo) {
        try {
            lockPort.withExclusive(meterSerial, () -> {
                householdExtendedEventService.readControlProfileAndSave(model, meterSerial, profileObis, isMD, rangeFrom, rangeTo);
                log.info("Household control event read completed. meter={} profile={}", meterSerial, profileObis);
                return null;
            });
        } catch (IllegalStateException e2) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e2.getMessage(), e2);
            metricsPort.recordFailure(meterSerial, profileObis, "Server restarted");
        } catch (Exception e) {
            log.error("Sync fatal meter={} profile={} reason={}", meterSerial, profileObis, e.getMessage(), e);
            metricsPort.recordFailure(meterSerial, profileObis, "lock_or_sync_error");
        }
    }

}
