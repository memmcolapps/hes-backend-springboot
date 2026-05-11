package com.memmcol.hes.domain.token;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenWriteService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;
    private final CreditActivationService creditActivationService;

    public static final String TOKEN_OBIS = "1.0.129.129.2.255";
    public static final int TOKEN_CLASS_ID = 1;
    public static final int TOKEN_ATTRIBUTE = 2;

    public Map<String, Object> writeToken(String meterSerial, String creditToken) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            log.info("Writing credit token to meter {}", meterSerial);

            dlmsReaderUtils.writeAttribute(
                    client,
                    meterSerial,
                    TOKEN_OBIS,
                    TOKEN_CLASS_ID,
                    TOKEN_ATTRIBUTE,
                    creditToken.getBytes(StandardCharsets.UTF_8),
                    DataType.OCTET_STRING
            );

            log.info("Credit token written successfully to meter {}", meterSerial);

            log.info("Activating credit for meter {}", meterSerial);
            Map<String, Object> activationResult = creditActivationService.activateCredit(meterSerial);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", "success");
            result.put("creditToken", creditToken);
            result.put("activation", activationResult);
            return result;
        });
    }
}
