package com.memmcol.ami.auth.repository;

import com.memmcol.ami.modules.core.model.Organizations;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organizations, Long> {
}