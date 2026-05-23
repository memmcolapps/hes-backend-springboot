package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.exception.DlmsTransportException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

@Component
public class DlmsExecutionGuard {

    public <T> T execute(String meterSerial, Callable<T> task) {

        try {
            return task.call();

        } catch (ReadTimeoutException e) {
            throw new DlmsTransportException("TIMEOUT", e);

        } catch (Exception e) {
            // Any Netty / IO / unexpected DLMS failure
            throw new DlmsTransportException("TRANSPORT_FAILURE", e);
        }
    }
}
