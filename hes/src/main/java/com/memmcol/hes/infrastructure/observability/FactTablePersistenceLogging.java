package com.memmcol.hes.infrastructure.observability;

import org.slf4j.Logger;

import java.util.List;
import java.util.function.Function;

/**
 * Structured observability for writes to profile / billing / event fact tables.
 * Use {@link #logBatchOutcome} after each batch persist so logs always state meter, model,
 * row counts, table, and explicit reasons when nothing new was written.
 */
public final class FactTablePersistenceLogging {

    private FactTablePersistenceLogging() {
    }

    public static String nz(String s) {
        return (s == null || s.isBlank()) ? "_unknown" : s;
    }

    public static <T> String firstModel(List<T> rows, Function<T, String> modelExtractor) {
        if (rows == null || rows.isEmpty()) {
            return "_unknown";
        }
        return nz(modelExtractor.apply(rows.get(0)));
    }

    public static void logPersistSuccess(Logger log,
                                         String domain,
                                         String table,
                                         String meterSerial,
                                         String meterModel,
                                         String profileObis,
                                         int recordsNewlyWritten,
                                         int recordsIncomingTotal,
                                         int recordsSkippedAsDuplicateOrUnchanged) {
        log.info(
                "Fact DB persistence: successfully wrote {} new row(s) to table={} (domain={}). meterSerial={} meterModel={} profileObis={} incomingRows={} rowsNotInsertedAsDuplicateOrExisting={} hes.factdb outcome=SUCCESS",
                recordsNewlyWritten,
                table,
                domain,
                nz(meterSerial),
                nz(meterModel),
                nz(profileObis),
                recordsIncomingTotal,
                recordsSkippedAsDuplicateOrUnchanged);
    }

    public static void logPersistZeroRows(Logger log,
                                          String domain,
                                          String table,
                                          String meterSerial,
                                          String meterModel,
                                          String profileObis,
                                          String reasonCode,
                                          String reasonDetail,
                                          Integer incomingRowsTotal) {
        log.warn(
                "Fact DB persistence: 0 new rows written to table={} (domain={}). meterSerial={} meterModel={} profileObis={} incomingRows={} reasonCode={} reasonDetail={} hes.factdb outcome=NO_NEW_ROWS",
                table,
                domain,
                nz(meterSerial),
                nz(meterModel),
                nz(profileObis),
                incomingRowsTotal == null ? "_na" : incomingRowsTotal,
                reasonCode,
                reasonDetail == null ? "" : reasonDetail);
    }

    public static void logPersistFailure(Logger log,
                                         String domain,
                                         String table,
                                         String meterSerial,
                                         String meterModel,
                                         String profileObis,
                                         Throwable error) {
        log.error(
                "Fact DB persistence: error saving to table={} (domain={}). meterSerial={} meterModel={} profileObis={}. recordsSaved=0. cause={} hes.factdb outcome=FAILURE",
                table,
                domain,
                nz(meterSerial),
                nz(meterModel),
                nz(profileObis),
                error != null ? error.getMessage() : "unknown",
                error);
    }

    /**
     * Logs either {@link #logPersistSuccess} or {@link #logPersistZeroRows} from batch counters.
     *
     * @param recordsPersistedCount      value returned from persist layer (insert/merge flush count for this batch)
     * @param incomingRowCount           rows in the incoming batch before or after mapping (adapter-specific)
     * @param approxNotInsertedCounter   adapter-reported difference (e.g. total − persisted); used only in reason text
     */
    public static void logBatchOutcome(Logger log,
                                       String domain,
                                       String table,
                                       String meterSerial,
                                       String meterModel,
                                       String profileOrObis,
                                       int recordsPersistedCount,
                                       int incomingRowCount,
                                       int approxNotInsertedCounter) {
        if (recordsPersistedCount > 0) {
            logPersistSuccess(log, domain, table, meterSerial, meterModel, profileOrObis,
                    recordsPersistedCount, incomingRowCount, approxNotInsertedCounter);
            return;
        }
        if (incomingRowCount <= 0) {
            logPersistZeroRows(log, domain, table, meterSerial, meterModel, profileOrObis,
                    "NO_INCOMING_ROWS",
                    "No readings/DTOs were passed to the persistence batch for this meter (null or empty list).",
                    0);
            return;
        }
        String detail = String.format(
                "%d row(s) were present in the batch for this meter, but the persistence layer reported 0 rows written. "
                        + "Common causes: every timestamp already existed in the fact table (deduplication removed all rows before insert), "
                        + "filtered list was empty after dedupe, or only no-op merge paths. adapterCounterIncomingMinusPersisted=%d",
                incomingRowCount, approxNotInsertedCounter);
        logPersistZeroRows(log, domain, table, meterSerial, meterModel, profileOrObis,
                "NO_NEW_FACT_ROWS_WRITTEN", detail, incomingRowCount);
    }
}
