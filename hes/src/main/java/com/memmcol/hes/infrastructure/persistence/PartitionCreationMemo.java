package com.memmcol.hes.infrastructure.persistence;

import java.util.concurrent.ConcurrentHashMap;

public final class PartitionCreationMemo {

    private static final ConcurrentHashMap<String, Boolean> CONFIRMED = new ConcurrentHashMap<>();

    private PartitionCreationMemo() {
    }

    public static boolean isConfirmed(String partitionName) {
        return CONFIRMED.containsKey(partitionName);
    }

    public static void confirm(String partitionName) {
        CONFIRMED.put(partitionName, Boolean.TRUE);
    }
}
