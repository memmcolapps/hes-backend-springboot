package com.memmcol.hes.gridflex.sse;

import com.memmcol.hes.nettyUtils.MeterHeartbeatManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeterHeartbeatService {

    private final MeterStatusSsePublisher publisher;
    private final MeterHeartbeatManager heartbeatManager;  // DB batch manager

    /**
     * This is your main method for Login / Heartbeat events.
     */
    public void processFrame(String meterNo, String status) {

        log.debug("Push message from meter {}, status: {}", meterNo, status);

        MeterStatusEvent event = new MeterStatusEvent(
                meterNo,
                ZonedDateTime.now(ZoneId.systemDefault()).toLocalDateTime(),
                status
        );

        // 1️⃣ Publish to SSE clients
        publisher.publish(event);

        // 2️⃣ Push into DB buffer (asynchronously flushed)
        heartbeatManager.handleStatus(meterNo, status);

        log.debug("Processed login/heartbeat frame for meter {}, status: {}", meterNo, status);
    }

}

