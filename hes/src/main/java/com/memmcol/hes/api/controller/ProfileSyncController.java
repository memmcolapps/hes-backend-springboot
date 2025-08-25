package com.memmcol.hes.api.controller;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import com.memmcol.hes.domain.profile.MetersLockService;
import com.memmcol.hes.domain.profile.ProfileReaderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * 6. Simple Controller Entry Point (Example)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile")
public class ProfileSyncController {
    private final ProfileSyncUseCase syncUseCase;
    private final MetersLockService metersLockService;
    private final ProfileReaderRegistry readerRegistry;

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


}