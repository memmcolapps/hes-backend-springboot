package com.memmcol.ami.auth.entity;

import com.memmcol.ami.auth.entity.AmiUser;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ami_user_login_attempts",
        uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class AmiUserLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AmiUser user;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

}