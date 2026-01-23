package com.memmcol.ami.auth.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
        name = "roles",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_name", columnNames = "name")
)
public class Role {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    protected Role() {
        // JPA
    }

    public Role(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
