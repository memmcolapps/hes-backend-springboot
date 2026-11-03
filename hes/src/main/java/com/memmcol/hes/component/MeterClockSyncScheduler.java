//package com.memmcol.hes.component;
//
//import com.memmcol.hes.domain.clock.MeterClockSyncService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.util.Map;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class MeterClockSyncScheduler {
//
//    private final MeterClockSyncService clockSyncService;
//
//    @Scheduled(
//            cron = "${hes.clock-sync.cron:0 0 */6 * * *}",
//            zone = "${hes.clock-sync.zone:Africa/Lagos}"
//    )
//    public void synchronizeMeterClocks() {
//
//        log.info("Starting scheduled meter clock synchronization");
//
//        try {
//            Map<String, Object> result =
//                    clockSyncService.synchronizeClocks();
//
//            log.info("Clock synchronization completed: {}", result);
//
//        } catch (Exception e) {
//            log.error("Clock synchronization job failed", e);
//        }
//    }
//}
