package com.memmcol.hes.repository;

import com.memmcol.hes.model.DlmsObisObjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DlmsObisObjectRepository extends JpaRepository<DlmsObisObjectEntity, Long> {
}
