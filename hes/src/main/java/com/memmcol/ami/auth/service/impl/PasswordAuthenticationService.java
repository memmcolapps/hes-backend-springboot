package com.memmcol.ami.auth.service.impl;

import com.memmcol.ami.auth.dto.AuthenticationResult;
import com.memmcol.ami.auth.dto.LoginRequest;
import com.memmcol.ami.auth.entity.AmiUser;
import com.memmcol.ami.auth.repository.AmiUserRepository;
import com.memmcol.ami.auth.domain.policy.AccountLockPolicy;
import com.memmcol.ami.auth.service.AuthenticationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class PasswordAuthenticationService implements AuthenticationService {

    private final AmiUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountLockPolicy lockPolicy;

    public PasswordAuthenticationService(
            AmiUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccountLockPolicy lockPolicy
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.lockPolicy = lockPolicy;
    }

    @Override
    @Transactional
    public AuthenticationResult authenticate(LoginRequest request) {

        String email = request.getEmail().toLowerCase();

        AmiUser user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null) {
            // Never reveal user existence
            return AuthenticationResult.failure("Invalid credentials");
        }

        if (!user.isActive() || user.isAccountLocked()) {
            return AuthenticationResult.failure("Account is disabled or locked");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            return AuthenticationResult.failure("Invalid credentials");
        }

        resetFailedAttempts(user);

        user.setLastLoginAt(OffsetDateTime.now());

        return AuthenticationResult.success(
                user.getId(),
                user.getOrganizations().getId(),
                user.getEmail()
        );
    }

    private void registerFailedAttempt(AmiUser user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

        if (lockPolicy.shouldLock(user)) {
            user.setAccountLocked(true);
            user.setAccountLockedAt(OffsetDateTime.now());
        }
    }

    private void resetFailedAttempts(AmiUser user) {
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setAccountLockedAt(null);
    }
}