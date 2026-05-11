package com.memmcol.hes.domain.token;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXReplyData;
import gurux.dlms.enums.DataType;
import gurux.dlms.objects.GXDLMSRegisterActivation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditActivationService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;

    public static final String ACTIVATION_OBIS = "0.0.64.19.10.255";
    public static final int ACTIVATION_CLASS_ID = 11;

    public Map<String, Object> activateCredit(String meterSerial) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            log.info("Activating credit for meter {}", meterSerial);

            GXDLMSRegisterActivation activationObj = new GXDLMSRegisterActivation(ACTIVATION_OBIS);

            byte[][] requests = client.method(activationObj, 1, 0, DataType.NONE);

            GXReplyData reply = new GXReplyData();
            long start = System.currentTimeMillis();

            for (int i = 0; i < requests.length; i++) {
                byte[] requestFrame = requests[i];
                log.debug("DLMS Activation TX [{} / {}] → meter={}", i + 1, requests.length, meterSerial);

                GXReplyData response = dlmsReaderUtils.readDataBlock(client, meterSerial, requestFrame);
                client.getData(response.getData(), reply, null);

                while (reply.isMoreData()) {
                    byte[] rr = client.receiverReady(reply);
                    if (rr == null) break;
                    log.debug("DLMS Activation RR → meter={}", meterSerial);
                    GXReplyData segResponse = dlmsReaderUtils.readDataBlock(client, meterSerial, rr);
                    client.getData(segResponse.getData(), reply, null);
                }

                reply.clear();
            }

            long duration = System.currentTimeMillis() - start;
            log.info("Credit activated for meter {} in {}ms", meterSerial, duration);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", "success");
            result.put("activationOBIS", ACTIVATION_OBIS);
            result.put("classId", ACTIVATION_CLASS_ID);
            result.put("methodId", 1);
            result.put("durationMs", duration);

            return result;
        });
    }
}
