package com.memmcol.hes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@ToString
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @Column(name = "client_id", nullable = false, unique = true)
    private UUID clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "status")
    private String status = "ACTIVE"; // e.g., ACTIVE, DISABLED

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructors, getters, setters
    public Client() {}

    public Client(UUID clientId, String clientSecret, String clientName) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clientName = clientName;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }
}
