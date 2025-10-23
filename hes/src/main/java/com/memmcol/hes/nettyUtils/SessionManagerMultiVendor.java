package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.service.MeterConnections;
import com.memmcol.hes.service.MeterSession;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXICipher;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.enums.Security;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.enums.SecuritySuite;
import gurux.dlms.secure.GXCiphering;
import gurux.dlms.secure.GXDLMSSecureClient;
import io.netty.channel.Channel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.memmcol.hes.nettyUtils.RequestResponseService.TRACKER;
import static com.memmcol.hes.nettyUtils.RequestResponseService.inflightRequests;

@Service
@Slf4j
public class SessionManagerMultiVendor {
    private final Map<String, MeterSession> sessionMap = new ConcurrentHashMap<>();
    private final Duration SESSION_TIMEOUT = Duration.ofMinutes(3);
    private final TxRxService txRxService;

    // --- Optional: external configuration mapping ---
    private final Map<String, DlmsConfig> meterConfigMap = new HashMap<>();

    public SessionManagerMultiVendor(TxRxService txRxService) {
        this.txRxService = txRxService;
        loadDefaultConfigs();
    }

    @PostConstruct
    public void init() {
        cleanInflightRequests();
    }

    /**
     * Determine meter model from serial or DB.
     * This is a simple version – later you can query asset_meter or metadata service.
     */
    private String resolveModelId(String serial) {
        if (serial.startsWith("202006")) return "CLOU";
        if (serial.startsWith("62122")) return "GENERIC";
        if (serial.startsWith("62222")) return "GENERIC";
        if (serial.startsWith("62124")) return "LONGDIAN3";
        if (serial.startsWith("62224")) return "LONGDIAN";
        return "GENERIC";
    }

    /**
     * Define default or vendor-specific DLMS configurations.
     * You can load these later from DB, YAML, or REST config service.
     */
    private void loadDefaultConfigs() {
        meterConfigMap.put("GENERIC", new DlmsConfig(1, 1,
                Authentication.LOW, "11111111", InterfaceType.WRAPPER));

        meterConfigMap.put("CLOU", new DlmsConfig(1, 1,
                Authentication.LOW, "12345678", InterfaceType.WRAPPER));

        meterConfigMap.put("LONGDIAN", new DlmsConfig(1, 1,
                Authentication.HIGH_GMAC, "00000000", InterfaceType.WRAPPER));

        meterConfigMap.put("LONGDIAN2", new DlmsConfig(1, 1,
                Authentication.NONE, null, InterfaceType.WRAPPER));

        meterConfigMap.put("LONGDIAN3", new DlmsConfig(1, 1,
                Authentication.LOW, "00000000", InterfaceType.WRAPPER));

        meterConfigMap.put("LONGDIAN4", new DlmsConfig(16, 1,
                Authentication.LOW, "00000000", InterfaceType.WRAPPER));
    }

    /**
     * Creates a DLMS client dynamically based on model or prefix.
     */
    private GXDLMSClient createDlmsClient(String serial, String modelId) {
        DlmsConfig cfg = meterConfigMap.getOrDefault(modelId, meterConfigMap.get("CLOU"));

        return new GXDLMSClient(
                true,
                cfg.getClientId(),
                cfg.getServerId(),
                cfg.getAuth(),
                cfg.getPassword(),
                cfg.getInterfaceType()
        );
    }

    private GXDLMSClient createSecureDlmsClient2(String serial, String modelId) {
        DlmsConfig cfg = meterConfigMap.getOrDefault(modelId, meterConfigMap.get("CLOU"));
        return new GXDLMSSecureClient(
                true,
                1,                          // Public client
                1,                              // Logical device ID (meter)
                Authentication.NONE,
                null,
                InterfaceType.WRAPPER
        );
    }

    private GXDLMSClient createSecureDlmsClient(String serial, String modelId) {
        DlmsConfig cfg = meterConfigMap.getOrDefault(modelId, meterConfigMap.get("CLOU"));

        GXDLMSSecureClient client = new GXDLMSSecureClient(
                true,
                cfg.getClientId(),
                cfg.getServerId(),
                cfg.getAuth(),
                cfg.getPassword(),
                cfg.getInterfaceType()
        );

        // Only configure ciphering for secure models
        if (cfg.getAuth() == Authentication.HIGH_GMAC || cfg.getAuth() == Authentication.HIGH) {
            client.getCiphering().setSecurity(Security.AUTHENTICATION);  //ENCRYPTION, AUTHENTICATION_ENCRYPTION, NONE, AUTHENTICATION
            client.getCiphering().setSystemTitle(GXCommon.hexToBytes("4C44430000000001")); // System title
            client.getCiphering().setBlockCipherKey(GXCommon.hexToBytes("00000000000000000000000000000000"));
            client.getCiphering().setAuthenticationKey(GXCommon.hexToBytes("30303030303030303030303030303030"));
            client.getCiphering().setSecuritySuite(SecuritySuite.SUITE_0);
            client.getCiphering().setBroadcastBlockCipherKey(GXCommon.hexToBytes("30303030303030303030303030303030"));
            client.getCiphering().setDedicatedKey(GXCommon.hexToBytes("30303030303030303030303030303030"));
            client.getCiphering().setInvocationCounter(1);
        }

        return client;
    }

       /**
     * Adds or reuses a session for a given meter.
     */
    public synchronized void addSession(String serial, Channel channel) throws Exception {
        if (channel == null) {
            log.info("No channel provided for session with meter {}", serial);
        }

        MeterSession existing = sessionMap.get(serial);
        if (existing != null && existing.isAssociated()) {
            log.debug("Session already exists and is associated: {}", serial);
            return;
        }

        GXDLMSClient dlmsClient = new GXDLMSClient();

        // --- Identify meter model dynamically (from DB or prefix) ---
        String modelId = resolveModelId(serial);
        if (modelId.equals("LONGDIAN")) {
            dlmsClient = createSecureDlmsClient(serial, modelId);
        } else {
            dlmsClient = createDlmsClient(serial, modelId);
        }
        try {
            log.info("🔗 Setting up DLMS Association for {} (Model: {})", serial, modelId);
            byte[][] aarq = dlmsClient.aarqRequest();
            byte[] response = txRxService.sendReceiveWithContext(serial, aarq[0], 20000);

            byte[] payload = Arrays.copyOfRange(response, 8, response.length);
            GXByteBuffer replyBuffer = new GXByteBuffer(payload);
            try {
                dlmsClient.parseAareResponse(replyBuffer);
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ AARE parse failed for {}: {}", serial, e.getMessage());
            }

            log.info("✅ DLMS Association established for {} (Model: {})", serial, modelId);
            MeterSession meterSession = new MeterSession(serial, channel, dlmsClient, txRxService);
            meterSession.setAssociated(true);
            sessionMap.put(serial, meterSession);

        } catch (Exception e) {
            log.error("❌ DLMS Association failed for {}: {}", serial, e.getMessage());
            throw e;
        }
    }

    public GXDLMSClient getClient(String serial) {
        MeterSession session = sessionMap.get(serial);
        if (session != null && session.isAssociated()) {
            return session.getClient();
        }
        return null;
    }

    public GXDLMSClient getOrCreateClient(String serial) throws Exception {
        GXDLMSClient client = getClient(serial);
        if (client == null) {
            addSession(serial, MeterConnections.getChannel(serial));
            client = getClient(serial);
        }
        return client;
    }

    public void removeSession(String serial) {
        sessionMap.remove(serial);
    }

    public boolean isAssociationLost(byte[] response) {
        if (response == null || response.length < 3) return false;
        int len = response.length;
        return response[len - 3] == (byte) 0xD8
                && response[len - 2] == 0x01
                && response[len - 1] == 0x01;
    }

    public void cleanInflightRequests() {
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            inflightRequests.entrySet().removeIf(entry -> {
                DlmsRequestContext ctx = entry.getValue();
                if (now > ctx.getExpiryTime()) {
                    log.warn("🧹 Cleaning expired request: CID={}, Meters={}",
                            entry.getKey(), ctx.getMeterId());
                    TRACKER.remove(entry.getKey());
                    return true;
                }
                return false;
            });
        }, 30, 30, TimeUnit.SECONDS);
    }

//    @Scheduled(fixedDelay = 30000)
    public void cleanupExpiredEntries() {
        log.debug("🔍 Cleaning expired sessions and inflight TXs...");
//
//        int before = sessionMap.size();
//        sessionMap.entrySet().removeIf(entry -> {
//            MeterSession session = entry.getValue();
//            if (session.isExpired(SESSION_TIMEOUT)) {
//                try {
//                    session.sendDisconnectRequest(session);
//                    log.warn("⏳ Disconnected expired meter {}", entry.getKey());
//                } catch (Exception e) {
//                    log.error("❌ Failed to disconnect {}: {}", entry.getKey(), e.getMessage());
//                }
//                return true;
//            }
//            return false;
//        });
//        log.debug("🧹 Removed {} expired sessions", before - sessionMap.size());
    }
}
