package com.memmcol.hes.domain.token;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.exception.AssociationLostException;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.DlmsResponse;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDLMSExceptionResponse;
import gurux.dlms.GXReplyData;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSRegister;
import gurux.dlms.enums.DataType;
import gurux.dlms.internal.GXCommon;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;
    private final TxRxService txRxService;

    public static final String TOKEN_OBIS = "1.0.129.129.2.255";
    public static final int TOKEN_CLASS_ID = 1;
    public static final int TOKEN_ATTRIBUTE = 2;

    public static final String CREDIT_BALANCE_OBIS = "1.0.140.129.0.255";
    public static final int CREDIT_BALANCE_CLASS_ID = 3;


    public Map<String, Object> writeToken(String meterSerial, String creditToken) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            client.setUseLogicalNameReferencing(true);

            // 2. Define the Token Object
            GXDLMSData tokenObject = new GXDLMSData(TOKEN_OBIS);

            log.info("Step 1: Writing credit token to meter {}", meterSerial);
            log.debug("Token hex bytes: {}", GXCommon.hexToBytes(creditToken));

            byte[] tokenBytes = GXCommon.hexToBytes(creditToken);

            // 4. SET THE VALUE INSIDE THE OBJECT FIRST
            tokenObject.setValue(tokenBytes);

            // 5. Generate the Write Request with 2 parameters
            // Attribute 2 is the 'Value' attribute index
            byte[][] writeRequest = client.write(tokenObject, 2);

            DlmsResponse response = dlmsReaderUtils.executeMethod(client,meterSerial,writeRequest);


Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("creditToken", creditToken);
            result.put("status", response.isSuccess() ? "success" : "failed");
            result.put("dlmsStatus", response.getStatus());
            result.put("message", response.getMessage());
            result.put("rawResponse", response.getRawResponse());
            result.put("resultData", response.getResultData());

            if (response.isSuccess()) {
                log.info("✅ Token written successfully to meter {}", meterSerial);
            } else {
                log.error("❌ Failed to write Token to meter {}: {} ({})", meterSerial, response.getMessage(), response.getStatus());
            }

            return result;
        });
    }

    public Map<String, Object> getCreditBalance(String meterSerial) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("obis", CREDIT_BALANCE_OBIS);

            log.info("Reading credit balance from meter {}", meterSerial);

            GXDLMSRegister creditRegister = new GXDLMSRegister(CREDIT_BALANCE_OBIS);

            byte[][] request = client.read(creditRegister, 2);
            byte[] response = txRxService.sendReceiveWithContext(meterSerial, request[0], 20000);

            if (sessionManager.isAssociationLost(response)) {
                sessionManager.removeSession(meterSerial);
                throw new AssociationLostException("Association lost during credit balance read for meter: " + meterSerial);
            }

            GXReplyData reply = new GXReplyData();
            client.getData(response, reply, null);
            String rawHex = GXCommon.toHex(response);

            if (reply.getError() != 0) {
                log.warn("Credit balance read error for meter {}: code={}, message={}",
                        meterSerial, reply.getError(), reply.getErrorMessage());
                result.put("status", "failed");
                result.put("errorCode", reply.getError());
                result.put("errorMessage", reply.getErrorMessage());
                result.put("rawResponse", rawHex);
                return result;
            } else if (reply.getValue() instanceof GXDLMSExceptionResponse ex) {
                Object errorObj = ex.getExceptionServiceError();
                int errorCode = errorObj != null ? errorObj.hashCode() : -1;
                String errorMsg = errorObj != null ? errorObj.toString() : "Unknown DLMS Exception";
                log.warn("Credit balance read exception for meter {}: {}", meterSerial, errorMsg);
                result.put("status", "failed");
                result.put("errorCode", errorCode);
                result.put("errorMessage", errorMsg);
                result.put("rawResponse", rawHex);
                return result;
            }

            Object value = client.updateValue(creditRegister, 2, reply.getValue());
            double creditBalance = 0.0;

            if (value instanceof Number) {
                creditBalance = ((Number) value).doubleValue();
            } else if (value != null) {
                try {
                    creditBalance = Double.parseDouble(value.toString());
                } catch (NumberFormatException e) {
                    log.warn("Could not parse credit balance: {}", value);
                }
            }

            log.info("Credit balance read successfully for meter {}: {}", meterSerial, creditBalance);

            result.put("status", "success");
            result.put("balance", creditBalance);
            result.put("unit", "kWh");
            result.put("rawResponse", rawHex);

            return result;
        });
    }
}