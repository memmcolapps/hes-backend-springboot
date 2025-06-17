package com.memmcol.hes.controller;

import com.memmcol.hes.cache.MeterService;
import com.memmcol.hes.model.ObisMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/obis")
@RequiredArgsConstructor
public class ObisController {

    private final MeterService meterService;

    @GetMapping("/{code}")
    public ResponseEntity<ObisMapping> get(@PathVariable String code) {
        long start = System.currentTimeMillis();
        ObisMapping mapping = meterService.getMapping(code);
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok()
                .header("X-Duration", duration + "ms")
                .body(mapping);
    }

    @PostMapping("/update")
    public ResponseEntity<ObisMapping> update(@RequestBody ObisMapping mapping) {
        return ResponseEntity.ok(meterService.updateMapping(mapping));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<String> evict(@PathVariable String code) {
        meterService.evictMapping(code);
        return ResponseEntity.ok("Evicted " + code);}

}
