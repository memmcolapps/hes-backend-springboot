package com.memmcol.hesTraining.controllers;

import com.memmcol.hesTraining.dto.ProfileRequest;
import com.memmcol.hesTraining.services.MeterReadingService;
import com.memmcol.hesTraining.services.ProfileReadingServices;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/training/obis")
@Tag(name = "DLMS OBIS Training", description = "Training endpoints for reading OBIS values and clock from DLMS meters")
@RequiredArgsConstructor
public class ObisTrainingController {

    private final MeterReadingService trainingService; // <-- your service class from yesterday
    private final ProfileReadingServices profileReadingService;

    // ──────────────────────────────────────────────
    // 🔹 1. Read Meter Clock
    // ──────────────────────────────────────────────
    @GetMapping("/clock/{serial}")
    @Operation(
            summary = "Read DLMS meter clock",
            description = """
                    Establishes association, reads meter clock (OBIS 0.0.1.0.0.255),
                    parses the DLMS response, and returns the local date-time.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Clock value successfully read",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(example = "{\"meterClock\": \"2025-10-09 09:35:00\"}"))),
                    @ApiResponse(responseCode = "400", description = "Bad request or DLMS association error")
            }
    )
    public ResponseEntity<String> readClock(
            @Parameter(description = "Meter serial number", example = "202006001314")
            @PathVariable String serial) throws Exception {

        String clock = trainingService.readClock(serial);
        return ResponseEntity.ok(clock);
    }

    // ──────────────────────────────────────────────
    // 🔹 2. Read OBIS Value (Unified)
    // ──────────────────────────────────────────────
    @GetMapping("/read")
    @Operation(
            summary = "Read OBIS value from DLMS meter",
            description = """
                    Reads the specified OBIS object from a DLMS meter.
                    Use 'isMD' to switch between MD and non-MD meter logic.
                    OBIS format: classId;obisCode;attributeIndex;dataIndex
                    Example: 3;1.0.1.8.0.255;2;0
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "OBIS value successfully read",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(example = """
                                            {
                                              "Description": "Total import active energy register",
                                              "Meter No": "202006001314",
                                              "obisCode": "1.0.1.8.0.255",  //3;1.0.1.8.0.255;2;0
                                              "value": 12456.72,
                                              "unit": "kWh",
                                              "scaler": 1.0
                                            }
                                            """))),
                    @ApiResponse(responseCode = "400", description = "Association lost or invalid OBIS format")
            }
    )
    public ResponseEntity<Map<String, Object>> readObisValue(
            @Parameter(description = "Meter model", example = "MMX-313-CT")
            @RequestParam String meterModel,

            @Parameter(description = "Meter serial number", example = "202006001314")
            @RequestParam String meterSerial,

            @Parameter(description = "OBIS format (classId;obisCode;attributeIndex;dataIndex)", example = "3;1.0.1.8.0.255;2;0")
            @RequestParam String obis,

            @Parameter(description = "Set true for MD meters (with CT/PT ratio scaling)")
            @RequestParam(defaultValue = "false") boolean isMD) {

        if (isMD) {
            return trainingService.readObisValue_MDMeters(meterModel, meterSerial, obis);
        } else {
            return trainingService.readObisValue_NonMDMeters(meterModel, meterSerial, obis);
        }
    }


    //Read profile data for both MD and non-MD
   @PostMapping("/profile")
    public ResponseEntity<String> getProfileData(@RequestBody ProfileRequest request) throws Exception {

        String response;
        if (request.isMD()) {
            response = profileReadingService.readProfile_MD(
                    request.meterSerial(),
                    request.meterModel(),
                    request.profileObis(),
                    request.startDate(),
                    request.endDate()
            );
        } else {
            response = profileReadingService.readProfile_NonMD(
                    request.meterSerial(),
                    request.meterModel(),
                    request.profileObis(),
                    request.startDate(),
                    request.endDate()
            );
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(response);
    }
}