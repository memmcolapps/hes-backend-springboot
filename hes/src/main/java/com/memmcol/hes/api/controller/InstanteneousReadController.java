package com.memmcol.hes.api.controller;

import com.memmcol.hes.domain.profile.InstantaneousReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meters")
public class InstanteneousReadController {
    private final InstantaneousReadService instantaneousReadService;

    @GetMapping("/{serial}/read-vc")
    public ResponseEntity<?> readVoltageAndCurrent(@PathVariable("serial") String meterSerial,
                                                   @RequestParam String model) {
        try {
            Map<String, Object> data = instantaneousReadService.readVoltagesAndCurrents(meterSerial, model);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Could not acquire lock or read meter: " + e.getMessage());
        }
    }

    @GetMapping("/{model}/{serial}/read")
    public ResponseEntity<?> readObis(@PathVariable("model") String model,
                                      @PathVariable("serial") String meterSerial,
                                      @RequestParam String obisCombined,
                                      @RequestParam boolean isMdMeter) {
        try {
            Object data = instantaneousReadService.readObisValue(model, meterSerial, obisCombined, isMdMeter);
            return ResponseEntity.ok(data);
       } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Could not acquire lock or read meter: " + e.getMessage());
        }
    }
}
