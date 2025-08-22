package com.memmcol.hes.infrastructure.dlms;

import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXStructure;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.internal.GXDataInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Normalize Gurux profile buffer (attribute 4) into List<List<Object>> rows.
 */
@Slf4j
public class DlmsProfileBufferNormalizer {
    private DlmsProfileBufferNormalizer() {}

    /**
     * Normalize any Gurux reply value for ProfileGeneric attribute 4 into rows.
     *
     * @param raw              reply.getValue() or reply.getData()
     * @param expectedColumns  how many columns you expect per row (timestamp + channels)
     * @return rows (never null; may be empty)
     */
    @SuppressWarnings("unchecked")
    public static List<List<Object>> normalize(Object raw, int expectedColumns) {
        if (raw == null) {
            log.warn("normalize: raw value is null");
            return List.of();
        }

        // 1. If Gurux left it as GXByteBuffer -> decode to object tree
        if (raw instanceof GXByteBuffer buf) {
            return normalize(decodeByteBuffer(buf), expectedColumns);
        }

        // 2. Direct GXStructure (single row)
        if (raw instanceof GXStructure structure) {
            return List.of(structureToList(structure));
        }

        // 3. Already a List<?> of something
        if (raw instanceof List<?> list) {
            return normalizeList(list, expectedColumns);
        }

        // 4. Array
        if (raw.getClass().isArray()) {
            Object[] arr = (Object[]) raw;
            return normalize(Arrays.asList(arr), expectedColumns);
        }

        // 5. byte[] maybe a tagged structure or timestamp (unlikely entire buffer)
        if (raw instanceof byte[] b) {
            // Attempt decode if it looks like a structured BER (tag 0x02 for structure)
            if (looksLikeEncodedStructure(b)) {
                Object decoded = decodeByteArray(b);
                return normalize(decoded, expectedColumns);
            } else {
                log.debug("normalize: byte[] raw length={} not recognized as structure", b.length);
                return List.of();
            }
        }

        log.warn("normalize: Unhandled raw type {}", raw.getClass().getName());
        return List.of();
    }

    /* ---------------------------------------------------------------------
       Internal helpers
       --------------------------------------------------------------------- */

    private static List<List<Object>> normalizeList(List<?> list, int expectedColumns) {
        if (list.isEmpty()) return List.of();

        // Case A: List<GXStructure>
        if (list.get(0) instanceof GXStructure) {
            List<List<Object>> rows = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof GXStructure s) {
                    rows.add(structureToList(s));
                } else {
                    log.debug("normalizeList: mixed element not GXStructure: {}", o.getClass());
                }
            }
            return rows;
        }

        // Case B: Flat stream (timestamp, v1, v2, timestamp, v1, v2...)
        if (isFlatStream(list, expectedColumns)) {
            return chunkFlatStream(list, expectedColumns);
        }

        // Case C: Elements themselves are Lists (already row lists)
        if (list.get(0) instanceof List) {
            // Try cast directly
            List<List<Object>> rows = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof List<?> row) {
                    rows.add(new ArrayList<>(row));
                } else {
                    log.debug("normalizeList: expected row list but got {}", o.getClass());
                }
            }
            return rows;
        }

        // Case D: Mixed byte[] or scalars – attempt detection
        if (containsTimestampLike(list)) {
            return attemptHeuristicRowSplit(list, expectedColumns);
        }

        // Fallback: treat entire list as a single row
        log.debug("normalizeList: treating entire list as a single row, size={}", list.size());
        return List.of(new ArrayList<>(list));
    }

    private static Object decodeByteBuffer(GXByteBuffer buf) {
        try {
            GXDataInfo info = new GXDataInfo();
            buf.position(0);
            return GXCommon.getData(null, buf, info);
        } catch (Exception e) {
            log.warn("decodeByteBuffer failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static Object decodeByteArray(byte[] data) {
        try {
            GXByteBuffer buffer = new GXByteBuffer(data);
            GXDataInfo info = new GXDataInfo();
            return GXCommon.getData(null, buffer, info);
        } catch (Exception e) {
            log.warn("decodeByteArray failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static boolean looksLikeEncodedStructure(byte[] b) {
        // Very lightweight heuristic:
        // 0x02 = STRUCTURE tag in DLMS, next byte is element count (plausible small number)
        return b.length > 2 && (b[0] & 0xFF) == 0x02;
    }

    private static List<Object> structureToList(GXStructure structure) {
        List<Object> row = new ArrayList<>(structure.size());
        for (int i = 0; i < structure.size(); i++) {
            Object val = structure.get(i);
            // In some cases nested structure occurs; flatten shallowly
            if (val instanceof GXStructure nested) {
                row.addAll(structureToList(nested));
            } else {
                row.add(val);
            }
        }
        return row;
    }

    private static boolean isFlatStream(List<?> list, int expectedColumns) {
        if (expectedColumns <= 0) return false;
        // All elements NOT lists/structures AND length multiple of expectedColumns
        if (list.size() % expectedColumns != 0) return false;
        for (Object o : list) {
            if (o instanceof GXStructure || o instanceof List) return false;
        }
        return true;
    }

    private static List<List<Object>> chunkFlatStream(List<?> list, int expectedColumns) {
        List<List<Object>> rows = new ArrayList<>(list.size() / expectedColumns);
        for (int i = 0; i < list.size(); i += expectedColumns) {
            List<Object> row = new ArrayList<>(expectedColumns);
            for (int j = 0; j < expectedColumns; j++) {
                row.add(list.get(i + j));
            }
            rows.add(row);
        }
        return rows;
    }

    private static boolean containsTimestampLike(List<?> list) {
        for (Object o : list) {
            if (o instanceof GXDateTime) return true;
            if (o instanceof byte[] b && (b.length == 12 || (b.length >= 2 && (b[0] & 0xFF) == 0x09))) return true; // 0x09 = OctetString
        }
        return false;
    }

    private static List<List<Object>> attemptHeuristicRowSplit(List<?> list, int expectedColumns) {
        if (expectedColumns > 0 && list.size() % expectedColumns == 0) {
            return chunkFlatStream(list, expectedColumns);
        }
        // fallback: treat each 'GXDateTime' start as a new row boundary
        List<List<Object>> rows = new ArrayList<>();
        List<Object> current = new ArrayList<>();
        for (Object o : list) {
            if (current.isEmpty()) {
                current.add(o);
            } else if (o instanceof GXDateTime) {
                // Start new row
                rows.add(current);
                current = new ArrayList<>();
                current.add(o);
            } else {
                current.add(o);
            }
        }
        if (!current.isEmpty()) rows.add(current);
        return rows;
    }
}
