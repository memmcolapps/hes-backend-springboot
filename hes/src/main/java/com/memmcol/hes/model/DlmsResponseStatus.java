package com.memmcol.hes.model;

/**
 * Enumeration of DLMS response statuses for meter communication operations.
 */
public enum DlmsResponseStatus {
    SUCCESS,
    TEMPORARY_FAILURE,
    READ_WRITE_DENIED,
    OBJECT_NOT_DEFINED,
    COMMUNICATION_ERROR,
    TIMEOUT,
    OTHER_ERROR
}
