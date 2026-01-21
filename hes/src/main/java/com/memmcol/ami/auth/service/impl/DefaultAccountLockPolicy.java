package com.memmcol.ami.auth.service.impl;

import com.memmcol.ami.auth.entity.AmiUser;
import com.memmcol.ami.auth.service.AccountLockPolicy;
import org.springframework.stereotype.Service;

@Service
public class DefaultAccountLockPolicy implements AccountLockPolicy {

    private static final int MAX_ATTEMPTS = 5;

    @Override
    public boolean shouldLock(AmiUser user) {
        return user.getFailedLoginAttempts() >= MAX_ATTEMPTS;
    }

    @Override
    public int maxFailedAttempts() {
        return MAX_ATTEMPTS;
    }
}