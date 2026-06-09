package com.memmcol.hes.domain.network;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.DlmsResponse;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.MeterRepository;
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
    private MeterRepository meterRepository;

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
    void testWriteApn_MD() throws Exception {
        // Arrange
        String apn = "Internet.ng.airtel.com";
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);
        when(meterRepository.findMeterDetailsByMeterNumber(serial)).thenReturn(java.util.Optional.of(
                MeterDTO.builder().meterClass("MD").build()
        ));
        when(dlmsReaderUtils.writeAttribute(any(), anyString(), anyString(), anyInt(), anyInt(), any(), any())).thenReturn(
                DlmsResponse.builder().meterSerial(serial).status(com.memmcol.hes.model.DlmsResponseStatus.SUCCESS).build()
        );

        // Act
        Map<String, Object> result = networkWriteService.writeApn(serial, apn);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(apn, result.get("apn"));
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.0.25.4.0.255"), eq(45), eq(2), any(byte[].class), eq(DataType.OCTET_STRING));
    }

    @Test
    void testWriteApn_NonMD() throws Exception {
        // Arrange
        String apn = "Internet.ng.airtel.com";
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);
        when(meterRepository.findMeterDetailsByMeterNumber(serial)).thenReturn(java.util.Optional.of(
                MeterDTO.builder().meterClass("Three-Phase").build()
        ));
        when(dlmsReaderUtils.writeAttribute(any(), anyString(), anyString(), anyInt(), anyInt(), any(), any())).thenReturn(
                DlmsResponse.builder().meterSerial(serial).status(com.memmcol.hes.model.DlmsResponseStatus.SUCCESS).build()
        );

        // Act
        Map<String, Object> result = networkWriteService.writeApn(serial, apn);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(apn, result.get("apn"));
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.11.25.4.0.255"), eq(45), eq(2), any(byte[].class), eq(DataType.OCTET_STRING));
    }

    @Test
    void testWriteIpPort_MD() throws Exception {
        // Arrange
        List<String> ipPorts = List.of("41.216.166.165:29064");
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);
        when(meterRepository.findMeterDetailsByMeterNumber(serial)).thenReturn(java.util.Optional.of(
                MeterDTO.builder().meterClass("MD").build()
        ));
        when(dlmsReaderUtils.writeAttribute(any(), anyString(), anyString(), anyInt(), anyInt(), any(), any())).thenReturn(
                DlmsResponse.builder().meterSerial(serial).status(com.memmcol.hes.model.DlmsResponseStatus.SUCCESS).build()
        );

        // Act
        Map<String, Object> result = networkWriteService.writeIpPort(serial, ipPorts);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(ipPorts, result.get("ipPorts"));
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.0.2.1.0.255"), eq(29), eq(6), anyList(), eq(DataType.ARRAY));
    }

    @Test
    void testWriteIpPort_NonMD() throws Exception {
        // Arrange
        String ip = "41.216.166.165";
        int port = 29064;
        List<String> ipPorts = List.of(ip + ":" + port);
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);
        when(meterRepository.findMeterDetailsByMeterNumber(serial)).thenReturn(java.util.Optional.of(
                MeterDTO.builder().meterClass("Single-Phase").build()
        ));
        when(dlmsReaderUtils.writeAttribute(any(), anyString(), anyString(), anyInt(), anyInt(), any(), any())).thenReturn(
                DlmsResponse.builder().meterSerial(serial).status(com.memmcol.hes.model.DlmsResponseStatus.SUCCESS).build()
        );

        // Act
        Map<String, Object> result = networkWriteService.writeIpPort(serial, ipPorts);

        // Assert
        assertEquals("success", result.get("status"));
        assertEquals(ipPorts, result.get("ipPorts"));

        // Verify Port written first (Class 41, Attr 2)
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.11.25.0.0.255"), eq(41), eq(2), eq(port), eq(DataType.UINT16));
        // Verify IP written second (Class 45, Attr 5)
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.11.25.4.0.255"), eq(45), eq(5), any(byte[].class), eq(DataType.OCTET_STRING));
    }
}
