package com.memmcol.hes.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Service
public class PartitionService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Ensure a partition exists for the given month; create it on-demand if missing.
     */
    @Transactional
    public void ensureMonthlyPartition(YearMonth yearMonth) {
        String partitionName = "monthly_consumption_" + yearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));

        // Validate partition name
        if (!partitionName.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid partition name: " + partitionName);
        }

        String startDate = yearMonth.atDay(1).toString();
        String endDate = yearMonth.plusMonths(1).atDay(1).toString();

        // Check if partition exists
        String checkSql = "SELECT to_regclass('" + partitionName + "')";
        Object result = em.createNativeQuery(checkSql).getSingleResult();

        if (result == null) {
            // Create partition
            String createSql = String.format(
                    "CREATE TABLE %s PARTITION OF monthly_consumption " +
                            "FOR VALUES FROM ('%s') TO ('%s');",
                    partitionName, startDate, endDate
            );
            em.createNativeQuery(createSql).executeUpdate();
        }
    }
}
