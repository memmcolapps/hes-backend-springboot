package com.memmcol.hes.repository;

import com.memmcol.hes.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByClientIdAndClientSecretAndStatus( UUID clientId, String clientSecret, String status);
}
