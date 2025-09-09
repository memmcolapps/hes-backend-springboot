package com.memmcol.hes.application.port.out;

public interface TxRxService {
    /**
     * Send request bytes to meter and receive response.
     * @param meterSerial Serial number of meter
     * @param request Outgoing APDU frame
     * @param timeoutMs Timeout in milliseconds
     * @return Response frame (raw bytes)
     * @throws Exception if TX/RX fails
     */
    byte[] sendReceiveWithContext(String meterSerial, byte[] request, long timeoutMs) throws Exception;
}
