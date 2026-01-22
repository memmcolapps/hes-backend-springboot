package com.memmcol.ami.auth.entity;

import com.memmcol.ami.modules.core.model.Organizations;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "ami_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ami_users_email", columnNames = "email")
        }
)
public class AmiUser {

    @Getter
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Setter
    @Getter
    @Column(name = "org_id", nullable = false, columnDefinition = "uuid")
    private UUID orgId;

    @Getter
    @Column(nullable = false, length = 255)
    private String email;

    @Setter
    @Getter
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Setter
    @Getter
    @Column(nullable = false)
    private boolean enabled = true;

    @Setter
    @Getter
    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @Setter
    @Getter
    @Column(nullable = false)
    private boolean active = true;

    @Setter
    @Getter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", nullable = false)
    private Organizations organizations;

    @Setter
    @Getter
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Setter
    @Getter
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Setter
    @Getter
    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Setter
    @Getter
    @Column(name = "account_locked_at")
    private OffsetDateTime accountLockedAt;

    @Getter
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ami_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    /* =========================
       Lifecycle Callbacks
       ========================= */

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void setEmail(String email) {
        this.email = email.toLowerCase();
    }

}
