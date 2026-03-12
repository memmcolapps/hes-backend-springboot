package com.memmcol.hes.domain.network;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NetworkWriteServiceTest {

    @Mock
    private SessionManagerMultiVendor sessionManager;

    @Mock
    private DlmsReaderUtils dlmsReaderUtils;

    @Mock
    private MeterLockPort meterLockPort;

    @Mock
    private GXDLMSClient dlmsClient;

    @InjectMocks
    private NetworkWriteService networkWriteService;

    private final String serial = "202006002221";

    @BeforeEach
    void setup() throws Exception {
        // Mock meterLockPort to execute the callable directly
        lenient().when(meterLockPort.withExclusive(eq(serial), any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(1);
            return callable.call();
        });
    }

    @Test
    void testWriteApn() throws Exception {
        // Arrange
        String apn = "Internet.ng.airtel.com";
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);

        // Act
        Map<String, Object> result = networkWriteService.writeApn(serial, apn);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(apn, result.get("apn"));
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.0.25.4.0.255"), eq(45), eq(2), any(byte[].class), eq(DataType.OCTET_STRING));
    }

    @Test
    void testWriteIpPort() throws Exception {
        // Arrange
        List<String> ipPorts = List.of("41.216.166.165:29064");
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);

        // Act
        Map<String, Object> result = networkWriteService.writeIpPort(serial, ipPorts);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(ipPorts, result.get("ipPorts"));
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.0.2.1.0.255"), eq(29), eq(6), anyList(), eq(DataType.ARRAY));
    }
}
