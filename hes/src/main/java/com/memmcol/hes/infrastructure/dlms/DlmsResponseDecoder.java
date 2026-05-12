package com.memmcol.hes.infrastructure.dlms;

import com.memmcol.hes.model.DlmsResponseStatus;
import gurux.dlms.GXDLMSExceptionResponse;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.ErrorCode;
import gurux.dlms.enums.ExceptionServiceError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Helper component to decode DLMS responses and map them to DlmsResponseStatus.
 */
@Component
@Slf4j
public class DlmsResponseDecoder {

    /**
     * Map Gurux GXReplyData status to DlmsResponseStatus.
     *
     * @param reply Gurux reply data
     * @return corresponding DlmsResponseStatus
     */
    public DlmsResponseStatus decodeStatus(GXReplyData reply) {
        if (reply == null) {
            return DlmsResponseStatus.COMMUNICATION_ERROR;
        }

        if (reply.getError() != 0) {
            log.warn("DLMS Error received: Code={}, Message={}", reply.getError(), reply.getErrorMessage());
            ErrorCode errorCode = ErrorCode.forValue(reply.getError());
            if (errorCode != null) {
                return switch (errorCode) {
                    case TEMPORARY_FAILURE -> DlmsResponseStatus.TEMPORARY_FAILURE;
                    case READ_WRITE_DENIED -> DlmsResponseStatus.READ_WRITE_DENIED;
                    case UNDEFINED_OBJECT -> DlmsResponseStatus.OBJECT_NOT_DEFINED;
                    default -> DlmsResponseStatus.OTHER_ERROR;
                };
            }
            return DlmsResponseStatus.OTHER_ERROR;
        }

        if (reply.getValue() instanceof GXDLMSExceptionResponse exceptionResponse) {
            return decodeException(exceptionResponse);
        }

        return DlmsResponseStatus.SUCCESS;
    }

    /**
     * Map DLMS ExceptionResponse to DlmsResponseStatus.
     *
     * @param exceptionResponse Gurux exception response
     * @return corresponding DlmsResponseStatus
     */
    public DlmsResponseStatus decodeException(GXDLMSExceptionResponse exceptionResponse) {
        if (exceptionResponse == null) {
            return DlmsResponseStatus.SUCCESS;
        }

        ExceptionServiceError serviceError = exceptionResponse.getExceptionServiceError();
        log.warn("DLMS Exception received: ServiceError={}", serviceError);

        if (serviceError == null) {
            return DlmsResponseStatus.OTHER_ERROR;
        }

        return switch (serviceError) {
            case OPERATION_NOT_POSSIBLE -> DlmsResponseStatus.TEMPORARY_FAILURE;
            default -> DlmsResponseStatus.OTHER_ERROR;
        };
    }
}
