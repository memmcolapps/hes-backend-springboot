package com.memmcol.hes.controller;

import com.memmcol.hes.domain.profile.ProfileProcessRequest;
import com.memmcol.hes.domain.profile.ProfileProcessor;
import com.memmcol.hes.domain.profile.ProfileTimestampPortImpl;
import com.memmcol.hes.infrastructure.dlms.CapturePeriodAdapter;
import com.memmcol.hes.model.ProfileRowDTO;
import com.memmcol.hes.model.TimestampRequest;
import com.memmcol.hes.service.DlmsService;
import com.memmcol.hes.service.ProfileMetadataService;
import gurux.dlms.objects.GXDLMSObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "DLMS APIs", description = "Meter reading & profile APIs")
@RestController
@Slf4j
@RequestMapping("/api/dlms")
public class DlmsController {

    private final DlmsService dlmsService;
    private final ProfileMetadataService profileMetadataService;
    private final ProfileProcessor profileProcessor;
    private final ProfileTimestampPortImpl timestampPort;
    private final CapturePeriodAdapter capturePeriodAdapter;

    public DlmsController(DlmsService dlmsService, ProfileMetadataService profileMetadataService, ProfileProcessor profileProcessor, ProfileTimestampPortImpl timestampPort, CapturePeriodAdapter capturePeriodAdapter) {
        this.dlmsService = dlmsService;
        this.profileMetadataService = profileMetadataService;
        this.profileProcessor = profileProcessor;
        this.timestampPort = timestampPort;
        this.capturePeriodAdapter = capturePeriodAdapter;
    }

    @GetMapping("/readClock")
    @Tag(name = "Clock", description = "Read clock by providing only the meter number.")
    public String readClock(@RequestParam String serial) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, SignatureException, InvalidKeyException {
        return dlmsService.readClock(serial);
    }

    @GetMapping("/obis")
    public ResponseEntity<Map<String, Object>> readObisValue(
            @RequestParam String serial,
            @RequestParam String obis) {

        return dlmsService.readObisValue(serial, obis);
    }

    @GetMapping("/greet")
    public ResponseEntity<?> greet(@RequestParam String name) {
        return ResponseEntity.ok(dlmsService.greet(name)) ;
    }

    @GetMapping("/read/{serial}/association")
    public ResponseEntity<?> getAssociationView(@PathVariable String serial) {
        try {
            List<GXDLMSObject> objects = dlmsService.readAssociationObjects(serial);
            List<Map<String, Object>> result = objects.stream().map(obj -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("classId", obj.getObjectType().getValue());
                item.put("obis", obj.getLogicalName());
                item.put("shortName", obj.getShortName());
                item.put("description", obj.getDescription());
                return item;
            }).toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("error", "Failed to read association view", "details", e.getMessage())
            );
        }
    }

    @GetMapping("/{serial}/{obis}")
    public ResponseEntity<?> readProfileBuffer(@PathVariable String serial, @PathVariable String obis) {
        try {
//            List<ProfileRowDTO> data = dlmsService.readProfileData(serial, obis);
            List<ProfileRowDTO> data = dlmsService.readProfileBuffer(serial, obis);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("timestamp", LocalDateTime.now());
            response.put("data", data);
            return ResponseEntity.ok(response);  // 200 OK with JSON body
        } catch (Exception e) {
            // Optional: log the error or return more structured error response
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to read profile data");
            error.put("timestamp", LocalDateTime.now());
            error.put("details", e.getMessage());
            return ResponseEntity.status(500).body(error);  // 500 Internal Server Error
        }
    }

    @GetMapping("/readscaler/{serial}/{obis}")
    public ResponseEntity<?> readScaler(@PathVariable String serial, @PathVariable String obis) {
        try {
            /* ⚙️ 1. Load (Cache ➜ DB ➜ Meter) */
            List data = profileMetadataService.getOrLoadMetadata("MMX-313-CT", obis, serial);

            /* ✅ 2. Success payload */
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("timestamp", LocalDateTime.now());
            response.put("data", data);
            return ResponseEntity.ok(response);  // 200 OK with JSON body
            /* 🎯 3. Unique-key / duplicate errors ------------------------------ */
        } catch (DataIntegrityViolationException | ConstraintViolationException ex) {

            Throwable root = ExceptionUtils.getRootCause(ex);
            String msg = (root != null ? root.getMessage() : ex.getMessage());
//            log.warn("Duplicate key violation while saving scaler metadata: {}", msg);
            Map<String,Object> err = new HashMap<>();
            err.put("status", "duplicate");
            err.put("timestamp", LocalDateTime.now());
            err.put("message",
                    "Duplicate entry — a record with the same (meter_model, profile_obis, " +
                            "capture_obis, attribute_index) already exists.");
            err.put("details", msg);
            /* 409 = Conflict (more specific than 400/500) */
            return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
        } catch (Exception e) {
            // Optional: log the error or return more structured error response
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to read profile scaler data");
            error.put("timestamp", LocalDateTime.now());
            error.put("details", e.getMessage());
            return ResponseEntity.status(500).body(error);  // 500 Internal Server Error
        }
    }

    //Read by entry
    @PostMapping("/channel2/{serial}/{model}/{count}")
    public ResponseEntity<?> readAndSaveChannel2(
            @PathVariable String serial,
            @PathVariable String model,
            @PathVariable int count
    ) {
        try {
            dlmsService.readAndSaveProfileChannel2(serial, model, count);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Profile channel 2 readings saved successfully");
            response.put("serial", serial);
            response.put("model", model);
            response.put("count", count);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response); // 200 OK

        } catch (Exception ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to read/save profile channel 2");
            error.put("details", ex.getMessage());
            error.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    //Read by timestamp
    @PostMapping("/channel2DateRange/{serial}/{model}/{endDate}")
    public ResponseEntity<?> readAndSaveChannel2ByTimestamp(
            @PathVariable String serial,
            @PathVariable String model,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        Map<String, Object> response = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        try {
            // 🔄 Call service
//            int recordsSaved = dlmsService.readProfileChannel2ByTimestamp(serial, model, endDate);
            int recordsSaved = dlmsService.readProfileChannel2ByTimestampDatablock(serial, model, endDate);

            // ✅ Success response
            response.put("status", "success");
            response.put("message", "Profile channel 2 readings saved successfully");
            response.put("serial", serial);
            response.put("model", model);
            response.put("endDate", endDate);
            response.put("recordsSaved", recordsSaved);
            response.put("timestamp", now);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            response.put("status", "validation_error");
            response.put("message", ex.getMessage());
            response.put("timestamp", now);
            return ResponseEntity.badRequest().body(response);

        } catch (Exception ex) {
            response.put("status", "error");
            response.put("message", "Failed to read/save profile channel 2");
            response.put("details", ex.getMessage());
            response.put("timestamp", now);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/profiles/process")
    public ResponseEntity<String> processProfiles(@RequestBody ProfileProcessRequest request) {
        try {
            // Pass values from request to service
            profileProcessor.setModel(request.getModel());
            profileProcessor.setMeterSerial(request.getMeterSerial());
            profileProcessor.setProfileObis(request.getProfileObis());
            profileProcessor.setFrom(request.getFrom());
            profileProcessor.setTo(request.getTo());
            profileProcessor.setMdMeter(request.isMdMeter());

            // Call the service
            profileProcessor.processProfiles();

            return ResponseEntity.ok("Profile processing completed successfully.");
        } catch (Exception e) {
            log.error("Error processing profiles", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

     /**
     * Resolve the last timestamp for a given meter and OBIS profile.
     */
    @Operation(
            summary = "Get last timestamp",
            description = "Resolves the last known timestamp for a given meter and OBIS profile. " +
                    "It checks cache → DB → meter read → fallback."
    )
    @GetMapping("/last-timestamp")
    public ResponseEntity<LocalDateTime> refreshLastTimestamp(
            @Parameter(description = "Unique serial number of the meter", example = "123456789")
            @RequestParam String meterSerial,

            @Parameter(description = "OBIS code for the profile", example = "0.0.99.98.0.255")
            @RequestParam String obis) {

        log.info("Request to resolve last timestamp meter={} obis={}", meterSerial, obis);
        LocalDateTime ts = timestampPort.refreshLastTimestamp(meterSerial, obis);
        return ResponseEntity.ok(ts);
    }

    /**
     * Resolve the last timestamp for a given meter and OBIS profile.
     */
    @Operation(
            summary = "Get capture period",
            description = "Resolves the capture period for a given meter and OBIS profile. " +
                    "It checks cache → DB → meter read → fallback."
    )
    @GetMapping("/capture-period")
    public ResponseEntity<Integer> refreshCapturePeriodSeconds(
            @Parameter(description = "Unique serial number of the meter", example = "123456789")
            @RequestParam String meterSerial,

            @Parameter(description = "OBIS code for the profile", example = "0.0.99.98.0.255")
            @RequestParam String obis) {

        log.info("Request to get the capture period meter={} obis={}", meterSerial, obis);
        int ts = capturePeriodAdapter.refreshCapturePeriodSeconds(meterSerial, obis);
        return ResponseEntity.ok(ts);
    }


}
