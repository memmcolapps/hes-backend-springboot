package com.memmcol.hesTraining.services;

import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.domain.profile.MeterRatios;
import com.memmcol.hes.infrastructure.dlms.DlmsDataDecoder;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.exception.AssociationLostException;
import com.memmcol.hes.service.MeterRatioService;
import gurux.dlms.*;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.ErrorCode;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.enums.Unit;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@AllArgsConstructor
public class MeterReadingService {
    private final TxRxService txRxService;
    private final SessionManagerMultiVendor sessionManagerMultiVendor;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterRatioService ratioService;

    /*TODO:
     *  1. Create instantaneous read
     *  2. Create profile readings
     *  3. Add Netty API for metrics*/

    //Full step by step reading of object from DLMS meters
    public String readClock(String serial) throws Exception {
        try {
//            GXDLMSClient dlmsClient = new GXDLMSClient(
//                    true,                    // Logical name referencing ✅
//                    1,                       // Client address (usually 1 for public)
//                    1,                       // Server address
//                    Authentication.LOW,     // Auth type
//                    "12345678",              // Password
//                    InterfaceType.WRAPPER    // DLMS WRAPPER mode
//            );

            GXDLMSClient dlmsClient = sessionManagerMultiVendor.getOrCreateClient(serial);

            //2. Generate AARQ Frame
            byte[][] aarq = dlmsClient.aarqRequest();
            log.info("AARQ (hex): {}", GXCommon.toHex(aarq[0]));

            //Send to meter -- aarq[0]
            byte[] response = txRxService.sendReceiveWithContext(serial, aarq[0], 20000);
            GXByteBuffer reply = new GXByteBuffer(response);

            //3. Parse AARE Response from MetersEntity
            log.debug("Parsing AARE response: {}", GXCommon.toHex(response));

// 1. Strip off the 8-byte wrapper header
            byte[] payload = Arrays.copyOfRange(response, 8, response.length);

// 2. Create a GXByteBuffer from the actual AARE payload
            GXByteBuffer replyBuffer = new GXByteBuffer(payload);

// 3. Parse AARE response using the client instance (not statically)
            try {
                dlmsClient.parseAareResponse(replyBuffer); // This validates acceptance
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ AARE parse failed: {}", e.getMessage());
                log.info("Assuming meter accepted AARQ based on external check");
                // Optional: set association manually
//            dlmsClient.getSettings().setConnected(2);
            } catch (GXDLMSException e) {
                log.warn("GXDLMS exception: {}", e.getMessage());
                throw new GXDLMSException("GXDLMS exception: " + e.getMessage());
            }
            log.info("🔓 Session Established: OK");

            //4. Build GET Frame for OBIS Clock (`0.0.1.0.0.255`)
            GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");
            // Attribute 2 = time
            byte[][] readClockRequest = dlmsClient.read(clock, 2);
            //Generate Clock frame
            for (byte[] frame : readClockRequest) {
                log.info("GET Clock Frame: {}", GXCommon.toHex(frame));
            }

            //5. Parse Clock GET.response
            GXReplyData replyClock = new GXReplyData();
            String strclock;
            GXDateTime clockDateTime = new GXDateTime();
            byte[] responseClock = txRxService.sendReceiveWithContext(serial, readClockRequest[0], 20000);

            boolean hasData = dlmsClient.getData(responseClock, replyClock, null);

            if (!hasData || replyClock.getValue() == null) {
                throw new IllegalStateException("❌ Failed to parse clock data or data is null");
            }

            Object result = dlmsClient.updateValue(clock, 2, replyClock.getValue());  // ✅ Use replyClock.getValue()

            if (result instanceof GXDateTime dt) {
                clockDateTime = dt;
//            log.info("🕒 MetersEntity Clock: {}", dt.toFormatString());
            } else if (result instanceof byte[] array) {
                clockDateTime = GXCommon.getDateTime(array);
            } else {
                throw new IllegalArgumentException("❌ Unexpected clock result type: " + result.getClass());
            }

            //   Send this to close the association cleanly.
            //6. Generate Disconnect Frame
            byte[] disconnectFrame = dlmsClient.disconnectRequest();
            if (disconnectFrame != null && disconnectFrame.length > 0) {
                log.info("📤 Disconnect Frame: {}", GXCommon.toHex(disconnectFrame));
                byte[] disconnectResponse = txRxService.sendReceiveWithContext(serial, disconnectFrame, 20000);

                // Some meters return nothing on disconnect, avoid NullPointerException
                if (disconnectResponse != null && disconnectResponse.length > 0) {
                    log.info("📥 Disconnect Response: {}", GXCommon.toHex(disconnectResponse));
                } else {
                    log.warn("⚠️ No response received from meter on disconnect. This may be normal.");
                }
            } else {
                log.warn("⚠️ Disconnect frame was empty or null — not sent.");
            }

            // Convert to LocalDateTime
            LocalDateTime localDateTime = clockDateTime.getMeterCalendar().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            // Format to "YYYY-MM-DD HH:MM:SS"
            strclock = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            strclock = "🕒 Meter Clock for " + serial + ": " + strclock;

            log.info("🕒 Meters Clock: {}", strclock);

            return strclock;

        } catch (Exception e) {
            log.error(e.getMessage());
            return "Error! : "+ e.getMessage();
        }
    }


    public ResponseEntity<Map<String, Object>> readObisValue_NonMDMeters(String meterModel, String meterSerial, String obis) {
        GXDLMSObject object;
        double scaler = 1.0;
        Unit unit;
        Object result;
        BigDecimal finalValue = BigDecimal.ONE;
        String formattedValue = "";
        DecimalFormat formatter = new DecimalFormat("#,##0.00");

        try {
            String[] parts = obis.split(";");
            if (parts.length != 4) {
                throw new IllegalArgumentException("OBIS format must be: classId;obisCode;attributeIndex;dataIndex");
            }

            int classId = Integer.parseInt(parts[0]);
            String obisCode = parts[1].trim();
            int attributeIndex = Integer.parseInt(parts[2]);


            int dataIndex = Integer.parseInt(parts[3]);

            //Establish DLMS Association
            GXDLMSClient client = sessionManagerMultiVendor.getOrCreateClient(meterSerial);
            if (client == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No active session for meter: " + meterSerial);
                error.put("details", "Please establish association before reading OBIS");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            ObjectType type = ObjectType.forValue(classId);
            switch (type) {
                case REGISTER -> {
                    GXDLMSRegister reg = new GXDLMSRegister();
                    reg.setLogicalName(obisCode);

                    // Read Scaler+Unit first
                    dlmsReaderUtils.readScalerUnit(client, meterSerial, reg, 3);
                    scaler = reg.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = reg.getUnit();

                    // Read value
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, reg, attributeIndex);
                    if (result instanceof Number) {
                        String unitSymbol = getUnitSymbol(unit);
                        switch (unitSymbol) {
                            case "A" ->  // current-related
                            {
                                finalValue = new BigDecimal(result.toString())
                                        .setScale(2, RoundingMode.HALF_UP);
                            }

                            case "V" ->  // voltage-related
                                    finalValue = new BigDecimal(result.toString())
                                            .setScale(2, RoundingMode.HALF_UP);

                            case "KW", "KVA", "KVar", "KWh", "KVAh", "KVarh" -> // power/energy-related
                                    finalValue = new BigDecimal(result.toString())
                                            .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);  //divide by 1,000 to convert to Kilo.....
//                                            .setScale(2, RoundingMode.HALF_UP);

                            default -> // fallback (no scaling)
                                    finalValue = new BigDecimal(result.toString())
                                            .setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                    object = reg;
                    // Format with comma separators
                    formattedValue = formatter.format(finalValue);
                }

                case EXTENDED_REGISTER -> {
                    GXDLMSExtendedRegister dr = new GXDLMSExtendedRegister();
                    dr.setLogicalName(obisCode);

                    // Read Scaler+Unit
                    dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 3);
                    scaler = dr.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = dr.getUnit();

                    // Read value
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, dr, attributeIndex);
                    if (result instanceof Number) {
                        String unitSymbol = getUnitSymbol(unit);
                        switch (unitSymbol) {
                            case "kW", "kVA", "kVar", "kWh", "kVAh", "KVarh" -> // power/energy-related
                                    finalValue = new BigDecimal(result.toString())
                                            .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);  //divide by 1,000 to convert to Kilo.....

                            default -> // fallback (no scaling)
                                    finalValue = new BigDecimal(result.toString()).setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                    object = dr;
                    // Format with comma separators
                    formattedValue = formatter.format(finalValue);
                }

                case DEMAND_REGISTER -> {
                    GXDLMSDemandRegister dr = new GXDLMSDemandRegister();
                    dr.setLogicalName(obisCode);

                    // Read Scaler+Unit
                    dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 3);
                    scaler = dr.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = dr.getUnit();

                    // Read value
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, dr, attributeIndex);
                    if (result instanceof Number) {
                        String unitSymbol = getUnitSymbol(unit);
                        switch (unitSymbol) {
                            case "kW", "kVA", "kVar", "kWh", "kVAh", "KVarh" -> // power/energy-related
                                    finalValue = new BigDecimal(result.toString())
                                            .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);  //divide by 1,000 to convert to Kilo.....

                            default -> // fallback (no scaling)
                                    finalValue = new BigDecimal(result.toString()).setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                    object = dr;
                    // Format with comma separators
                    formattedValue = formatter.format(finalValue);
                }

                case CLOCK -> {
                    GXDLMSClock clk = new GXDLMSClock();
                    clk.setLogicalName(obisCode);
                    GXDateTime clockDateTime;
                    Object raw = dlmsReaderUtils.readAttribute(client, meterSerial, clk, attributeIndex);
                    if (raw instanceof GXDateTime dt) {
                        clockDateTime = dt;
                    } else if (raw instanceof byte[] array) {
                        clockDateTime = GXCommon.getDateTime(array);
                    } else {
                        throw new IllegalArgumentException("❌ Unexpected clock result type: " + raw.getClass());
                    }

                    // Convert to LocalDateTime
                    LocalDateTime localDateTime = clockDateTime.getMeterCalendar().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();

                    // Format to "YYYY-MM-DD HH:MM:SS"
                    result = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    formattedValue = result.toString();
                    object = clk;
                    unit = null;
                }

                case DATA -> {
                    GXDLMSData data = new GXDLMSData();
                    data.setLogicalName(obisCode);
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, data, attributeIndex);
                    if (result instanceof byte[]) {
                        result = DlmsDataDecoder.decodeOctetString((byte[]) result);
                    } else if (result instanceof Number) {
                        result = new BigDecimal(result.toString());
                    } else if (result != null) {
                        result = result.toString();
                    }
                    assert result != null;
                    formattedValue = result.toString();
                    object = data;
                    unit = null;
                }

                default -> {
                    object = GXDLMSClient.createObject(type);
                    object.setLogicalName(obisCode);
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, object, attributeIndex);
                    if (result instanceof byte[]) {
                        result = DlmsDataDecoder.decodeOctetString((byte[]) result);
                    } else if (result instanceof Number) {
                        result = new BigDecimal(result.toString());
                    } else if (result != null) {
                        result = result.toString();
                    }
                    assert result != null;
                    formattedValue = result.toString();
                    unit = null;
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("Meter No", meterSerial);
            response.put("obisCode", obisCode);
            response.put("attributeIndex", attributeIndex);
            response.put("dataIndex", dataIndex);
            response.put("value", result);
            response.put("Actual Value", formattedValue);
            response.put("scaler", scaler);
            if (unit != null) {
                response.put("unit", getUnitSymbol(unit)); // your mapping function
            }
            return ResponseEntity.ok(response);
        } catch (AssociationLostException ex) {
            sessionManagerMultiVendor.removeSession(meterSerial);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Association lost with meter number: " + meterSerial);
            error.put("details", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to read from OBIS");
            error.put("details", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    public ResponseEntity<Map<String, Object>> readObisValue_MDMeters(String meterModel, String meterSerial, String obis) {
        GXDLMSObject object;
        double scaler = 1.0;
        Unit unit;
        Object result;
        BigDecimal finalValue = BigDecimal.ONE;
        String formattedValue = "";
        DecimalFormat formatter = new DecimalFormat("#,##0.00");

        try {
            String[] parts = obis.split(";");
            if (parts.length != 4) {
                throw new IllegalArgumentException("OBIS format must be: classId;obisCode;attributeIndex;dataIndex");
            }

            int classId = Integer.parseInt(parts[0]);
            String obisCode = parts[1].trim();
            int attributeIndex = Integer.parseInt(parts[2]);
            int dataIndex = Integer.parseInt(parts[3]);

            //Establish DLMS Association
            GXDLMSClient client = sessionManagerMultiVendor.getOrCreateClient(meterSerial);
            if (client == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No active session for meter: " + meterSerial);
                error.put("details", "Please establish association before reading OBIS");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            //Read MD CT and PT ratio
            MeterRatios meterRatios = ratioService.readMeterRatios("modelNumber", meterSerial);

            //Read and decode values
            ObjectType type = ObjectType.forValue(classId);
            switch (type) {
                case REGISTER -> {
                    GXDLMSRegister reg = new GXDLMSRegister();
                    reg.setLogicalName(obisCode);

                    // Read Scaler+Unit first
                    dlmsReaderUtils.readScalerUnit(client, meterSerial, reg, 3);
                    scaler = reg.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = reg.getUnit();

                    // Read value
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, reg, attributeIndex);
                    if (result instanceof Number) {
                        String unitSymbol = getUnitSymbol(unit);
                        switch (unitSymbol) {
                            case "A" ->  // current-related
                                    finalValue = new BigDecimal(result.toString())
                                            .multiply(BigDecimal.valueOf(meterRatios.getCtRatio()))
                                            .setScale(2, RoundingMode.HALF_UP);

                            case "V" ->  // voltage-related
                                    finalValue = new BigDecimal(result.toString())
                                            .multiply(BigDecimal.valueOf(meterRatios.getPtRatio()))
                                            .setScale(2, RoundingMode.HALF_UP);

                            case "KW", "KVA", "KVar", "KWh", "KVAh", "KVarh" -> // power/energy-related
                                    finalValue = new BigDecimal(result.toString())
                                            .multiply(BigDecimal.valueOf(meterRatios.getCtRatio()))
                                            .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);  //divide by 1,000 to convert to Kilo.....
//                                            .setScale(2, RoundingMode.HALF_UP);

                            default -> // fallback (no scaling)
                                    finalValue = new BigDecimal(result.toString())
                                            .setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                    object = reg;

                    // Format with comma separators
                    formattedValue = formatter.format(finalValue);
                }

                case EXTENDED_REGISTER -> {
                    GXDLMSExtendedRegister dr = new GXDLMSExtendedRegister();
                    dr.setLogicalName(obisCode);

                    // Read Scaler+Unit
                    dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 3);
                    scaler = dr.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = dr.getUnit();

                    // Read value
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, dr, attributeIndex);
                    if (result instanceof Number) {
                        String unitSymbol = getUnitSymbol(unit);
                        switch (unitSymbol) {
                            case "kW", "kVA", "kVar", "kWh", "kVAh", "KVarh" -> // power/energy-related
                                    finalValue = new BigDecimal(result.toString())
                                            .multiply(BigDecimal.valueOf(meterRatios.getCtRatio()))
                                            .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);  //divide by 1,000 to convert to Kilo.....

                            default -> // fallback (no scaling)
                                    finalValue = new BigDecimal(result.toString()).setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                    object = dr;
                    // Format with comma separators
                    formattedValue = formatter.format(finalValue);
                }

                case DEMAND_REGISTER -> {
                    GXDLMSDemandRegister dr = new GXDLMSDemandRegister();
                    dr.setLogicalName(obisCode);

                    // Read Scaler+Unit
                    dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 3);
                    scaler = dr.getScaler();
                    if (scaler == 0) {
                        scaler = 1.0;
                    }
                    unit = dr.getUnit();

                    // Read value
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, dr, attributeIndex);
                    if (result instanceof Number) {
                        String unitSymbol = getUnitSymbol(unit);
                        switch (unitSymbol) {
                            case "kW", "kVA", "kVar", "kWh", "kVAh", "KVarh" -> // power/energy-related
                                    finalValue = new BigDecimal(result.toString())
                                            .multiply(BigDecimal.valueOf(meterRatios.getCtRatio()))
                                            .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);  //divide by 1,000 to convert to Kilo.....

                            default -> // fallback (no scaling)
                                    finalValue = new BigDecimal(result.toString()).setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                    object = dr;
                    // Format with comma separators
                    formattedValue = formatter.format(finalValue);
                }

                case CLOCK -> {
                    GXDLMSClock clk = new GXDLMSClock();
                    clk.setLogicalName(obisCode);
                    GXDateTime clockDateTime;
                    Object raw = dlmsReaderUtils.readAttribute(client, meterSerial, clk, attributeIndex);
                    if (raw instanceof GXDateTime dt) {
                        clockDateTime = dt;
                    } else if (raw instanceof byte[] array) {
                        clockDateTime = GXCommon.getDateTime(array);
                    } else {
                        throw new IllegalArgumentException("❌ Unexpected clock result type: " + raw.getClass());
                    }

                    // Convert to LocalDateTime
                    LocalDateTime localDateTime = clockDateTime.getMeterCalendar().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();

                    // Format to "YYYY-MM-DD HH:MM:SS"
                    result = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    formattedValue = result.toString();

                    object = clk;
                    unit = null;
                }

                case DATA -> {
                    GXDLMSData data = new GXDLMSData();
                    data.setLogicalName(obisCode);
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, data, attributeIndex);
                    if (result instanceof byte[]) {
                        result = DlmsDataDecoder.decodeOctetString((byte[]) result);
                    } else if (result instanceof Number) {
                        result = new BigDecimal(result.toString());
                    } else if (result != null) {
                        result = result.toString();
                    }
                    assert result != null;
                    formattedValue = result.toString();
                    object = data;
                    unit = null;
                }

                default -> {
                    object = GXDLMSClient.createObject(type);
                    object.setLogicalName(obisCode);
                    result = dlmsReaderUtils.readAttribute(client, meterSerial, object, attributeIndex);
                    if (result instanceof byte[]) {
                        result = DlmsDataDecoder.decodeOctetString((byte[]) result);
                    } else if (result instanceof Number) {
                        result = new BigDecimal(result.toString());
                    } else if (result != null) {
                        result = result.toString();
                    }
                    assert result != null;
                    formattedValue = result.toString();
                    unit = null;
                }
//                default -> throw new IllegalArgumentException("Unsupported object type: " + type);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("Meter No", meterSerial);
            response.put("obisCode", obisCode);
            response.put("attributeIndex", attributeIndex);
            response.put("dataIndex", dataIndex);
            response.put("Raw Value", result);
            response.put("Actual Value", formattedValue);
            response.put("scaler", scaler);
            if (unit != null) {
                response.put("unit", getUnitSymbol(unit)); // your mapping function
            }
            return ResponseEntity.ok(response);
        } catch (AssociationLostException ex) {
            sessionManagerMultiVendor.removeSession(meterSerial);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Association lost with meter number: " + meterSerial);
            error.put("details", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to read from OBIS");
            error.put("details", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    public List<Map<String, Object>> readObisValuesBatch(String meterModel,
                                                         String meterSerial,
                                                         List<String> obisList,
                                                         boolean mdMeter) throws Exception {
        if (obisList == null || obisList.isEmpty()) {
            return List.of();
        }

        GXDLMSClient client = sessionManagerMultiVendor.getOrCreateClient(meterSerial);
        if (client == null) {
            throw new IllegalStateException("No active session for meter: " + meterSerial);
        }

        List<ParsedObis> parsedObis = new ArrayList<>(obisList.size());
        for (String obis : obisList) {
            parsedObis.add(parseObis(obis));
        }

        readScalerUnitsBatch(client, meterSerial, parsedObis);

        List<Map.Entry<GXDLMSObject, Integer>> valueReads = parsedObis.stream()
                .<Map.Entry<GXDLMSObject, Integer>>map(parsed -> new AbstractMap.SimpleEntry<>(parsed.object(), parsed.attributeIndex()))
                .toList();

        List<Object> values = readListValues(client, meterSerial, valueReads);
        MeterRatios meterRatios = mdMeter ? ratioService.readMeterRatios(meterModel, meterSerial) : null;
        DecimalFormat formatter = new DecimalFormat("#,##0.00");
        List<Map<String, Object>> response = new ArrayList<>(parsedObis.size());

        for (int i = 0; i < parsedObis.size(); i++) {
            ParsedObis parsed = parsedObis.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("Meter No", meterSerial);
            item.put("obisCode", parsed.obisCode());
            item.put("attributeIndex", parsed.attributeIndex());
            item.put("dataIndex", parsed.dataIndex());
            if (parsed.object() instanceof GXDLMSRegister register) {
                item.put("scaler", register.getScaler() == 0 ? 1.0 : register.getScaler());
                item.put("unit", getUnitSymbol(register.getUnit()));
            } else if (parsed.object() instanceof GXDLMSExtendedRegister register) {
                item.put("scaler", register.getScaler() == 0 ? 1.0 : register.getScaler());
                item.put("unit", getUnitSymbol(register.getUnit()));
            } else if (parsed.object() instanceof GXDLMSDemandRegister register) {
                item.put("scaler", register.getScaler() == 0 ? 1.0 : register.getScaler());
                item.put("unit", getUnitSymbol(register.getUnit()));
            }

            if (i >= values.size()) {
                item.put("statuscode", -1);
                item.put("error", "No DLMS value returned for this OBIS in batched response");
                item.put("Actual Value", null);
                response.add(item);
                continue;
            }

            Object rawValue = unwrapBatchValue(values.get(i));
            if (isDlmsFailure(rawValue)) {
                item.put("statuscode", -1);
                item.put("error", dlmsFailureMessage(rawValue));
                item.put("Actual Value", null);
                response.add(item);
                continue;
            }

            try {
                Object formattedValue = formatBatchValue(parsed, rawValue, mdMeter, meterRatios, formatter);
                item.put("statuscode", 0);
                item.put(mdMeter ? "Raw Value" : "value", rawValue);
                item.put("Actual Value", formattedValue);
            } catch (Exception ex) {
                item.put("statuscode", -1);
                item.put("error", "Failed to format batched OBIS value: " + ex.getMessage());
                item.put("Actual Value", null);
            }
            response.add(item);
        }

        return response;
    }

    private void readScalerUnitsBatch(GXDLMSClient client, String meterSerial, List<ParsedObis> parsedObis) throws Exception {
        List<ParsedObis> scalerReads = parsedObis.stream()
                .filter(parsed -> supportsScalerUnit(parsed.objectType()))
                .toList();
        if (scalerReads.isEmpty()) {
            return;
        }

        List<Map.Entry<GXDLMSObject, Integer>> reads = scalerReads.stream()
                .<Map.Entry<GXDLMSObject, Integer>>map(parsed -> new AbstractMap.SimpleEntry<>(parsed.object(), 3))
                .toList();
        List<Object> scalerValues = readListValues(client, meterSerial, reads);

        for (int i = 0; i < scalerReads.size(); i++) {
            if (i >= scalerValues.size()) {
                log.warn("Batched scaler/unit read returned no value for meter={} obis={}",
                        meterSerial, scalerReads.get(i).obisCode());
                continue;
            }
            Object scalerValue = unwrapBatchValue(scalerValues.get(i));
            if (isDlmsFailure(scalerValue)) {
                log.warn("Batched scaler/unit read failed for meter={} obis={}: {}",
                        meterSerial, scalerReads.get(i).obisCode(), dlmsFailureMessage(scalerValue));
                continue;
            }
            try {
                client.updateValue(scalerReads.get(i).object(), 3, scalerValue);
            } catch (Exception ex) {
                log.warn("Failed to apply batched scaler/unit for meter={} obis={}: {}",
                        meterSerial, scalerReads.get(i).obisCode(), ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> readListValues(GXDLMSClient client,
                                        String meterSerial,
                                        List<Map.Entry<GXDLMSObject, Integer>> reads) throws Exception {
        byte[][] requests = client.readList(reads);
        GXReplyData reply = new GXReplyData();

        byte[] response = txRxService.sendReceiveWithContext(meterSerial, requests[0], 20000);
        if (sessionManagerMultiVendor.isAssociationLost(response)) {
            sessionManagerMultiVendor.removeSession(meterSerial);
            throw new AssociationLostException("Association lost during batched OBIS read");
        }

        client.getData(response, reply, null);
        Object value = reply.getValue();
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        if (reads.size() == 1) {
            return List.of(value);
        }
        throw new IllegalStateException("Batched OBIS read response was not a value list.");
    }

    private Object unwrapBatchValue(Object value) {
        if (value instanceof GXDLMSAccessItem accessItem) {
            if (accessItem.getError() != null && accessItem.getError() != ErrorCode.OK) {
                return accessItem;
            }
            return accessItem.getValue();
        }
        return value;
    }

    private boolean isDlmsFailure(Object value) {
        if (value instanceof GXDLMSExceptionResponse) {
            return true;
        }
        if (value instanceof GXDLMSAccessItem accessItem) {
            return accessItem.getError() != null && accessItem.getError() != ErrorCode.OK;
        }
        if (value instanceof ErrorCode errorCode) {
            return errorCode != ErrorCode.OK;
        }
        if (value instanceof Map<?, ?> map) {
            return map.containsKey("error");
        }
        return false;
    }

    private String dlmsFailureMessage(Object value) {
        if (value instanceof GXDLMSExceptionResponse exceptionResponse) {
            return "DLMS exception response: state=" + exceptionResponse.getStateError()
                    + ", service=" + exceptionResponse.getExceptionServiceError()
                    + ", value=" + exceptionResponse.getValue();
        }
        if (value instanceof GXDLMSAccessItem accessItem) {
            return "DLMS access error: " + accessItem.getError();
        }
        if (value instanceof ErrorCode errorCode) {
            return "DLMS access error: " + errorCode;
        }
        if (value instanceof Map<?, ?> map && map.containsKey("error")) {
            return String.valueOf(map.get("error"));
        }
        return "DLMS access error";
    }

    private ParsedObis parseObis(String obis) {
        String[] parts = obis.split(";");
        if (parts.length != 4) {
            throw new IllegalArgumentException("OBIS format must be: classId;obisCode;attributeIndex;dataIndex");
        }

        int classId = Integer.parseInt(parts[0]);
        String obisCode = parts[1].trim();
        int attributeIndex = Integer.parseInt(parts[2]);
        int dataIndex = Integer.parseInt(parts[3]);
        ObjectType objectType = ObjectType.forValue(classId);
        GXDLMSObject object = createObject(objectType, obisCode);
        return new ParsedObis(obis, obisCode, classId, attributeIndex, dataIndex, objectType, object);
    }

    private GXDLMSObject createObject(ObjectType objectType, String obisCode) {
        GXDLMSObject object = switch (objectType) {
            case REGISTER -> new GXDLMSRegister();
            case EXTENDED_REGISTER -> new GXDLMSExtendedRegister();
            case DEMAND_REGISTER -> new GXDLMSDemandRegister();
            case CLOCK -> new GXDLMSClock();
            case DATA -> new GXDLMSData();
            default -> GXDLMSClient.createObject(objectType);
        };
        object.setLogicalName(obisCode);
        return object;
    }

    private Object formatBatchValue(ParsedObis parsed,
                                    Object rawValue,
                                    boolean mdMeter,
                                    MeterRatios meterRatios,
                                    DecimalFormat formatter) {
        if (rawValue == null) {
            return null;
        }

        ObjectType type = parsed.objectType();
        if (type == ObjectType.CLOCK) {
            return formatClock(rawValue);
        }
        if (rawValue instanceof byte[] bytes) {
            return DlmsDataDecoder.decodeOctetString(bytes);
        }
        if (!(rawValue instanceof Number)) {
            return rawValue.toString();
        }

        BigDecimal finalValue = new BigDecimal(rawValue.toString());
        if (supportsScalerUnit(type)) {
            String unitSymbol = getRegisterUnit(parsed.object());
            if (mdMeter && meterRatios != null) {
                finalValue = applyMdRatio(finalValue, unitSymbol, meterRatios);
            } else if (isPowerOrEnergy(unitSymbol)) {
                finalValue = finalValue.divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);
            } else {
                finalValue = finalValue.setScale(2, RoundingMode.HALF_UP);
            }
            return formatter.format(finalValue);
        }

        return finalValue.toString();
    }

    private String formatClock(Object rawValue) {
        GXDateTime clockDateTime;
        if (rawValue instanceof GXDateTime dt) {
            clockDateTime = dt;
        } else if (rawValue instanceof byte[] array) {
            clockDateTime = GXCommon.getDateTime(array);
        } else {
            return rawValue.toString();
        }
        LocalDateTime localDateTime = clockDateTime.getMeterCalendar().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private BigDecimal applyMdRatio(BigDecimal value, String unitSymbol, MeterRatios meterRatios) {
        return switch (unitSymbol) {
            case "A" -> value.multiply(BigDecimal.valueOf(meterRatios.getCtRatio()))
                    .setScale(2, RoundingMode.HALF_UP);
            case "V" -> value.multiply(BigDecimal.valueOf(meterRatios.getPtRatio()))
                    .setScale(2, RoundingMode.HALF_UP);
            case "KW", "KVA", "KVar", "KWh", "KVAh", "KVarh" -> value.multiply(BigDecimal.valueOf(meterRatios.getCtRatio()))
                    .divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP);
            default -> value.setScale(2, RoundingMode.HALF_UP);
        };
    }

    private String getRegisterUnit(GXDLMSObject object) {
        if (object instanceof GXDLMSRegister register) {
            return getUnitSymbol(register.getUnit());
        }
        if (object instanceof GXDLMSExtendedRegister register) {
            return getUnitSymbol(register.getUnit());
        }
        if (object instanceof GXDLMSDemandRegister register) {
            return getUnitSymbol(register.getUnit());
        }
        return "";
    }

    private boolean supportsScalerUnit(ObjectType type) {
        return type == ObjectType.REGISTER
                || type == ObjectType.EXTENDED_REGISTER
                || type == ObjectType.DEMAND_REGISTER;
    }

    private boolean isPowerOrEnergy(String unitSymbol) {
        return switch (unitSymbol) {
            case "KW", "KVA", "KVar", "KWh", "KVAh", "KVarh" -> true;
            default -> false;
        };
    }

    static public String getUnitSymbol(Unit unit) {
        return switch (unit) {
            case VOLTAGE -> "V";
            case CURRENT -> "A";
            case ACTIVE_POWER -> "KW";
            case APPARENT_POWER -> "KVA";
            case REACTIVE_POWER -> "KVar";
            case ACTIVE_ENERGY -> "KWh";
            case APPARENT_ENERGY -> "KVAh";
            case REACTIVE_ENERGY -> "KVarh";
            case FREQUENCY -> "Hz";
            // Add more cases as needed
            default -> unit.name(); // fallback to enum name
        };
    }

    private record ParsedObis(String obis,
                              String obisCode,
                              int classId,
                              int attributeIndex,
                              int dataIndex,
                              ObjectType objectType,
                              GXDLMSObject object) {
    }

}
