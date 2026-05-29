package com.memmcol.hes.infrastructure.dlms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    private String buildKey(String meterSerial, String profileObis) {
        return meterSerial + "::" + profileObis;
    }

    /**
     * Stores the current state of accumulated rows.
     * Replaces any existing partial buffer for this key because DLMS profile buffers
     * from Gurux are typically cumulative.
     */
    public void accumulate(String meterSerial, String profileObis, List<List<Object>> buf) {
        if (buf == null || buf.isEmpty()) return;
        String key = buildKey(meterSerial, profileObis);
        
        // We replace rather than append because Gurux client.updateValue(profile, 2, val) 
        // typically manages a cumulative buffer.
        partialBuffers.put(key, new ArrayList<>(buf));
        log.debug("Accumulated {} rows so far for {}", buf.size(), key);
    }

    /**
     * Drains partial rows (for recovery) and clears buffer.
     */
    public List<List<Object>> drainPartial(String meterSerial, String profileObis) {
        String key = buildKey(meterSerial, profileObis);
        List<List<Object>> rows = partialBuffers.remove(key);
        return rows == null ? List.of() : rows;
    }

    /**
     * Returns a copy of accumulated rows without clearing.
     */
    public List<List<Object>> getAccumulated(String meterSerial, String profileObis) {
        String key = buildKey(meterSerial, profileObis);
        List<List<Object>> data = partialBuffers.get(key);
        
        if (data == null || data.isEmpty()) {
            return List.of();
        }

        // Return defensive copy to avoid mutation side-effects
        return data.stream()
                .map(ArrayList::new)
                .collect(Collectors.toList());
    }

    /**
     * Clears any partial buffer for a meter/profile (e.g. on success).
     */
    public void clear(String meterSerial, String profileObis) {
        partialBuffers.remove(buildKey(meterSerial, profileObis));
    }

    @SuppressWarnings("unchecked")
    public List<List<Object>> normalizeProfileBuffer(Object raw) {
        if (raw == null) return List.of();
        
        if (raw instanceof Object[] arr) {
            List<List<Object>> rows = new ArrayList<>();
            for (Object o : arr) {
                rows.add(normalizeRow(o));
            }
            return rows;
        }
        
        if (raw instanceof List<?> l) {
            List<List<Object>> rows = new ArrayList<>();
            for (Object o : l) {
                rows.add(normalizeRow(o));
            }
            return rows;
        }
        
        return List.of();
    }

    private List<Object> normalizeRow(Object o) {
        if (o instanceof List<?> l) {
            return new ArrayList<>(l);
        } else if (o instanceof Object[] innerArr) {
            return new ArrayList<>(Arrays.asList(innerArr));
        } else {
            return new ArrayList<>(List.of(o));
        }
    }
}
