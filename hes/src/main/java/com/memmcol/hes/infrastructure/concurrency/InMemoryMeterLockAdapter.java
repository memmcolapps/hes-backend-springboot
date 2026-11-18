package com.memmcol.hes.infrastructure.concurrency;

import com.memmcol.hes.application.port.out.MeterLockPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 4.4 MetersEntity Lock Registry Adapter
 */
@Component
@Slf4j
public class InMemoryMeterLockAdapter implements MeterLockPort {

    private record LockEntry(ReentrantLock lock, AtomicInteger refs) {}

    private final ConcurrentHashMap<String, LockEntry> locks = new ConcurrentHashMap<>();

    private LockEntry acquire(String meter) {
        return locks.compute(meter, (key, entry) -> {
            if (entry == null) {
                entry = new LockEntry(new ReentrantLock(true), new AtomicInteger());
            }
            entry.refs().incrementAndGet();
            return entry;
        });
    }

    private void release(String meter, LockEntry entry) {
        locks.computeIfPresent(meter, (key, current) -> {
            if (current != entry) {
                return current;
            }
            return current.refs().decrementAndGet() <= 0 ? null : current;
        });
    }

    @Override
    public <T> T withExclusive(String meterSerial, Callable<T> action) throws Exception {
        LockEntry entry = acquire(meterSerial);
        entry.lock().lock();
        try {
            return action.call();
        } finally {
            entry.lock().unlock();
            release(meterSerial, entry);
        }
    }

    @Override
    public boolean tryExclusive(String meterSerial, Runnable action, long millis) {
        LockEntry entry = acquire(meterSerial);
        try {
            if (entry.lock().tryLock(millis, TimeUnit.MILLISECONDS)) {
                try {
                    action.run();
                    return true;
                } finally {
                    entry.lock().unlock();
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            release(meterSerial, entry);
        }
    }
}
