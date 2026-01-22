package com.memmcol.ami.auth.domain.policy;

import com.memmcol.ami.auth.entity.AmiUser;

public interface AccountLockPolicy {

    boolean shouldLock(AmiUser user);

    int maxFailedAttempts();
}