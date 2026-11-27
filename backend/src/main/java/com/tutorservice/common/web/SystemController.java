package com.tutorservice.common.web;

import com.tutorservice.common.dto.SystemStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    @GetMapping("/status")
    public SystemStatus status() {
        return new SystemStatus("TutorService", "UP", "environment");
    }
}
