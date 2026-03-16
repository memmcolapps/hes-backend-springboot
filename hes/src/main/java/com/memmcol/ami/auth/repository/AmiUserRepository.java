package com.memmcol.ami.auth.repository;

import com.memmcol.ami.auth.entity.AmiUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AmiUserRepository extends JpaRepository<AmiUser, UUID> {

    Optional<AmiUser> findByEmail(String email);

    Optional<AmiUser> findByEmailAndEnabledTrue(String email);

    boolean existsByEmailIgnoreCase(String email);
}
