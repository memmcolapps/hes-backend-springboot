package com.memmcol.hes.controller;

import com.memmcol.hes.domain.profile.EventLogService;
import com.memmcol.hes.domain.profile.ProfileTimestampPortImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/dlms/event-profiles")
@RequiredArgsConstructor
@Tag(name = "Event Profiles", description = "Endpoints for reading and managing event profile data from DLMS meters")
public class EventProfileController {

}
