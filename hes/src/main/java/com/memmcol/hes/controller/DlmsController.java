package com.memmcol.hes.controller;

import com.memmcol.hes.model.ProfileRowDTO;
import com.memmcol.hes.service.DlmsService;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.objects.GXDLMSObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.function.EntityResponse;

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

@RestController
@RequestMapping("/api/dlms")
public class DlmsController {

    private final DlmsService dlmsService;

    public DlmsController(DlmsService dlmsService) {
        this.dlmsService = dlmsService;
    }

    @GetMapping("/readClock")
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
}
