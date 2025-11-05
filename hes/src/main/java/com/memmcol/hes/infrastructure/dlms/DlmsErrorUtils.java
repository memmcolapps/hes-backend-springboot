package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.exception.DlmsDataAccessException;
import gurux.dlms.GXDLMSExceptionResponse;
import gurux.dlms.GXReplyData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DlmsErrorUtils {
    private DlmsErrorUtils() {
        // Prevent instantiation
    }

    /**
     * Centralized DLMS error validation.
     *
     * @param reply       DLMS response object (from Gurux).
     * @param meterSerial Meter identifier.
     * @param profileObis OBIS or DLMS object reference.
     * @throws DlmsDataAccessException if an error or exception is detected.
     */
    public static void checkError(GXReplyData reply, String meterSerial, String profileObis) {
        try {
            if (reply == null) {
                throw new DlmsDataAccessException(profileObis, "No DLMS reply received.");
            }
            if (reply.getError() != 0) {
                log.warn("⚠️ DLMS Error [{}] for meter {} on OBIS {}", reply.getError(), meterSerial, profileObis);
                throw new DlmsDataAccessException(profileObis, reply.getErrorMessage()
                );
            }
            // Check for DLMS ExceptionResponse
            if (reply.getValue() instanceof GXDLMSExceptionResponse ex) {
                log.warn("⚠️ DLMS Exception from meter [{}]: {}", meterSerial, ex.getExceptionServiceError());
                throw new DlmsDataAccessException(
                        profileObis,
                        "DLMS Exception: " + ex.getExceptionServiceError()
                );
            }
        } catch (DlmsDataAccessException e) {
            throw e; // rethrow as-is
        } catch (Exception e) {
            log.error("Unexpected DLMS response handling error for meter {}: {}", meterSerial, e.getMessage());
        }
    }
}
