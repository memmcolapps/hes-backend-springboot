package com.memmcol.hes.application.port.out;

import com.memmcol.hes.domain.profile.ProfileRow;

import java.util.List;

/**
 * 2. Port Interfaces (Application Layer Contracts)
 * Salvage partially received rows (e.g., after block interruption).
 */
public interface PartialProfileRecoveryPort {
    List<ProfileRow> recoverPartial(String meterSerial, String profileObis) throws ProfileReadException;
}
