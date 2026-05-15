package com.memmcol.hes.domain.relay;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.DlmsResponse;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.DataType;
import gurux.dlms.objects.GXDLMSDisconnectControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class ControlRelayService {


    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;


    public Map<String, Object> controlRelay(String meterSerial,boolean connect) throws Exception {

        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);

            if (client == null) {
                throw new IllegalStateException( "No DLMS session found for meter: " + meterSerial);
            }

            log.info("{} relay for meter {}",connect ? "Connecting" : "Disconnecting",meterSerial);

            GXDLMSDisconnectControl relay = new GXDLMSDisconnectControl("0.0.96.3.10.255");
            int methodId = connect ? 2 : 1;

            byte[][] requests = client.method(
                    relay,
                    methodId,
                    0,
                    DataType.INT8
            );

            DlmsResponse response = dlmsReaderUtils.executeMethod(client,meterSerial,requests);

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put("meterSerial", meterSerial);
            result.put("status",response.isSuccess() ? "success" : "failed");
            result.put("dlmsStatus", response.getStatus());
            result.put("message", response.getMessage());
            result.put("relayStatus",
                    connect ? "Relay Closed" : "Relay Open");

            if (response.isSuccess()) {
                log.info("✅ Relay {} successfully for meter {}",connect ? "connected" : "disconnected",meterSerial);

            } else {
                log.error("❌ Relay operation failed for meter {}: {} ({})",meterSerial,response.getMessage(),response.getStatus());
            }

            return result;
        });
    }

}