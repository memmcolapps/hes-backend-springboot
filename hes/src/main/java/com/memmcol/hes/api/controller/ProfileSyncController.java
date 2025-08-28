package com.memmcol.hes.api.controller;

import com.memmcol.hes.application.port.in.ProfileSyncUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 6. Simple Controller Entry Point (Example)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile")
public class ProfileSyncController {
    private final ProfileSyncUseCase syncUseCase;

     @PostMapping("/{model}/{serial}/{profileObis}/sync")
    public ResponseEntity<?> sync(@PathVariable String model,
                                  @PathVariable String serial,
                                  @PathVariable String profileObis,
                                  @RequestParam(defaultValue = "50") int batchSize) {
        syncUseCase.syncUpToNow(model, serial, profileObis, batchSize);
        return ResponseEntity.accepted().body("Sync started for " + serial + " profile " + profileObis);
    }


}