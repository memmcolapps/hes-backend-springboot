package com.memmcol.hes.repository;

import com.memmcol.hes.entities.ProfileChannelOne;
import com.memmcol.hes.entities.ProfileChannelOneId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProfileChannelOneRepository extends JpaRepository<ProfileChannelOne, ProfileChannelOneId> {

    // Find all records for a meter within a time range
    List<ProfileChannelOne> findByMeterSerialAndEntryTimestampBetween(
            String meterSerial,
            LocalDateTime start,
            LocalDateTime end
    );

    // Check if a record exists for a given meterSerial and entryTimestamp
    boolean existsByMeterSerialAndEntryTimestamp(
            String meterSerial,
            LocalDateTime entryTimestamp
    );

}