package com.memmcol.hes.infrastructure.dlms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for accumulating partial blocks when DLMS profile reads
 * are interrupted or incomplete.
 */
@Component
@Slf4j
public class DlmsPartialDecoder {
    /**
     * Partial buffers keyed by meter serial + profileObis
     * E.g. "123456::1.0.99.1.0.255" → accumulated profile rows.
     */
    private final Map<String, List<List<Object>>> partialBuffers = new ConcurrentHashMap<>();

    private String key(String meterSerial, String profileObis) {
        return meterSerial + "::" + profileObis;
    }

    /**
     * Called whenever a block is received to accumulate rows.
     * This can be hooked inside the Gurux multi-block loop.
     */
    public void accumulate(String meterSerial, String profileObis, List<List<Object>> buf) {
        String key = key(meterSerial, profileObis);
        partialBuffers.computeIfAbsent(key, k -> new ArrayList<>())
                .addAll(buf);
        log.debug("Accumulated {} rows so far for {}", buf.size(), key);
    }

    /**
     * Drains partial rows (for recovery) and clears buffer.
     */
    public List<List<Object>> drainPartial(String meterSerial, String profileObis) {
        String key = key(meterSerial, profileObis);
        List<List<Object>> rows = partialBuffers.get(key);
        return rows == null ? List.of() : rows;
    }

    /**
     * Clears any partial buffer for a meter/profile (e.g. on success).
     */
    public void clear(String meterSerial, String profileObis) {
        partialBuffers.remove(key(meterSerial, profileObis));
    }

    @SuppressWarnings("unchecked")
    public List<List<Object>> normalizeProfileBuffer(Object raw) {
        if (raw instanceof Object[] arr) {
            List<List<Object>> rows = new ArrayList<>();
            for (Object o : arr) {
                if (o instanceof List<?> l) {
                    rows.add(new ArrayList<>(l));
                } else if (o instanceof Object[] innerArr) {
                    rows.add(Arrays.asList(innerArr));
                } else {
                    rows.add(List.of(o));
                }
            }
            return rows;
        }
        if (raw instanceof List<?> l) {
            List<List<Object>> rows = new ArrayList<>();
            for (Object o : l) {
                if (o instanceof List<?> inner) rows.add(new ArrayList<>(inner));
                else if (o instanceof Object[] innerArr) rows.add(Arrays.asList(innerArr));
                else rows.add(List.of(o));
            }
            return rows;
        }
        return List.of();
    }

}
