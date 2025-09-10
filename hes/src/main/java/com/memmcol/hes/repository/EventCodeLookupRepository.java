package com.memmcol.hes.repository;

import com.memmcol.hes.entities.EventCodeLookup;
import com.memmcol.hes.entities.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventCodeLookupRepository extends JpaRepository<EventCodeLookup, Long> {
    Optional<EventCodeLookup> findByEventTypeAndCode(EventType eventType, Integer code);
    }
