package com.memmcol.hes.api.controller;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.domain.profile.ProfileReaderRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * 6. Simple Controller Entry Point (Example)
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Profiles readings", description = "Endpoints for reading and managing profile data from DLMS meters")
@RequestMapping("/api/dlms/profile")
public class ProfileSyncController {
    private final ProfileSyncUseCase syncUseCase;
    private final MetersLockService metersLockService;

     @PostMapping("/{model}/{serial}/{profileObis}/sync")
    public ResponseEntity<?> sync(@PathVariable String model,
                                  @PathVariable String serial,
                                  @PathVariable String profileObis,
                                  @RequestParam(defaultValue = "50") int batchSize) {
        syncUseCase.syncUpToNow(model, serial, profileObis, batchSize);
        return ResponseEntity.accepted().body("Sync started for " + serial + " profile " + profileObis);
    }


    @PostMapping("/readChannelOne/{model}/{serial}/{profileObis}/readAndSave")
    public ResponseEntity<?> readProfile(@PathVariable String model,
                                  @PathVariable String serial,
                                  @PathVariable String profileObis,
                                  @RequestParam(defaultValue = "50") int batchSize) {

        if (Objects.equals(model, "MMX-313-CT") && Objects.equals(profileObis, "1.0.99.1.0.255")) {
            metersLockService.readChannelOneWithLock(model, serial, profileObis, batchSize);
            return ResponseEntity.accepted().body("Sync started for " + serial + " profile " + profileObis);
        } else {
            return ResponseEntity.badRequest().body("Model and Profile Obis wrongly selected");
        }
     }

    @PostMapping("/readMonthlyBilling/{model}/{serial}/{profileObis}/readAndSave")
    public ResponseEntity<?> readMonthlyBillWithLock(@PathVariable String model,
                                         @PathVariable String serial,
                                         @PathVariable String profileObis,
                                         @RequestParam(defaultValue = "50") int batchSize) {

        if (Objects.equals(model, "MMX-313-CT") && Objects.equals(profileObis, "0.0.98.1.0.255")) {
            metersLockService.readMonthlyBillWithLock(model, serial, profileObis, batchSize);
            return ResponseEntity.accepted().body("Sync started for " + serial + " profile " + profileObis);
        } else {
            return ResponseEntity.badRequest().body("Model and Profile Obis wrongly selected");
        }
    }

    @PostMapping("/readDailyBilling/{model}/{serial}/{profileObis}/readAndSave")
    public ResponseEntity<?> readDailyBillWithLock(@PathVariable String model,
                                                     @PathVariable String serial,
                                                     @PathVariable String profileObis,
                                                     @RequestParam(defaultValue = "50") int batchSize) {

        if (Objects.equals(model, "MMX-313-CT") && Objects.equals(profileObis, "0.0.98.2.0.255")) {
            metersLockService.readDailyBillWithLock(model, serial, profileObis, batchSize);
            return ResponseEntity.accepted().body("Sync started for " + serial + " profile " + profileObis);
        } else {
            return ResponseEntity.badRequest().body("Model and Profile Obis wrongly selected");
        }
    }


    @Operation(
            summary = "Read and save event profile from DLMS meter",
            description = "Starts reading the specified event profile from the meter and persists the data. " +
                    "This method acquires a lock on the meter to avoid concurrent reads. " +
                    "Optional iteration parameter can control how many entries are read per iteration."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Sync started successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error during sync")
    })
    @PostMapping("/readEventsProfile/{model}/{serial}/{profileObis}/readAndSave")
    public ResponseEntity<String> readEventsWithLock(
            @Parameter(description = "Meter model", example = "GENERIC")
            @PathVariable String model,

            @Parameter(description = "Meter serial number", example = "123456789")
            @PathVariable String serial,

            @Parameter(description = "OBIS code of the event profile", example = "0.0.99.98.0.255")
            @PathVariable String profileObis,

            @Parameter(description = "Optional number of rows per batch", example = "100")
            @RequestParam(required = false, defaultValue = "1") int batchSize,

            @Parameter(description = "Test mode flag", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean testMode) {

        metersLockService.readEventsWithLock(model, serial, profileObis, batchSize, testMode);
        return ResponseEntity.accepted()
                .body("Sync started for meter " + serial +
                        " profile " + profileObis +
                        " with batchSize=" + batchSize +
                        " testMode=" + testMode);
    }
}