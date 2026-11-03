//package com.memmcol.hes.repository;
//
//import org.springframework.data.jpa.repository.Modifying;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//@Repository
//public interface ClockSyncMeterClaimRepository
//        extends JpaRepository<ClockSyncMeterClaim, String> {
//
//    @Modifying
//    @Query(value = """
//        INSERT INTO clock_sync_meter_claim
//            (meter_number, job_id, claimed_at)
//        VALUES
//            (:meterNumber, :jobId, CURRENT_TIMESTAMP)
//        ON CONFLICT (meter_number) DO NOTHING
//        """, nativeQuery = true)
//    int tryClaim(
//            @Param("meterNumber") String meterNumber,
//            @Param("jobId") Long jobId
//    );
//
//    @Modifying
//    @Query(value = """
//        DELETE FROM clock_sync_meter_claim
//        WHERE meter_number = :meterNumber
//          AND job_id = :jobId
//        """, nativeQuery = true)
//    int releaseClaim(
//            @Param("meterNumber") String meterNumber,
//            @Param("jobId") Long jobId
//    );
//
//    @Modifying
//    @Query(value = """
//        DELETE FROM clock_sync_meter_claim
//        WHERE job_id = :jobId
//        """, nativeQuery = true)
//    int releaseAllClaims(@Param("jobId") Long jobId);
//}
