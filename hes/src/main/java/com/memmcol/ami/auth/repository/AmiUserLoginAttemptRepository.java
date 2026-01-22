package com.memmcol.ami.auth.repository;


import com.memmcol.ami.auth.entity.AmiUser;
import com.memmcol.ami.auth.entity.AmiUserLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AmiUserLoginAttemptRepository
        extends JpaRepository<AmiUserLoginAttempt, Long> {

    Optional<AmiUserLoginAttempt> findByUser(AmiUser user);

    void deleteByUser(AmiUser user);
}