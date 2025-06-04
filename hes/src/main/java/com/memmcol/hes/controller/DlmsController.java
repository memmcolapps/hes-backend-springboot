package com.memmcol.hes.controller;

import com.memmcol.hes.service.DlmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.EntityResponse;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.HashMap;
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




}
