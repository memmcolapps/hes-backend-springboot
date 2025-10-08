package com.memmcol.hes.infrastructure.concurrency;

import com.memmcol.hes.application.port.out.MeterLockPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 4.4 MetersEntity Lock Registry Adapter
 */
@Component
@Slf4j
public class InMemoryMeterLockAdapter implements MeterLockPort {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private ReentrantLock lockFor(String meter) {
        return locks.computeIfAbsent(meter, k -> new ReentrantLock(true));
    }

    @Override
    public <T> T withExclusive(String meterSerial, Callable<T> action) throws Exception {
        ReentrantLock l = lockFor(meterSerial);
        l.lock();
        try {
            return action.call();
        } finally {
            l.unlock();
        }
    }

    @Override
    public boolean tryExclusive(String meterSerial, Runnable action, long millis) {
        ReentrantLock l = lockFor(meterSerial);
        try {
            if (l.tryLock(millis, TimeUnit.MILLISECONDS)) {
                try {
                    action.run();
                    return true;
                } finally {
                    l.unlock();
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
