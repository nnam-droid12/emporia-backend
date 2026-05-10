package com.emporia.backend.api;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.repository.SMEProfileRepository;
import com.emporia.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final SMEProfileRepository profileRepository;
    private final JwtService jwtService;

    @PostMapping("/fcm-token")
    public ResponseEntity<?> updateFcmToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String token = authHeader.substring(7);
        String phoneNumber = jwtService.extractPhoneNumber(token);

        log.info("Received FCM token registration request for phone: {}", phoneNumber);

        Optional<SMEProfile> profileOpt = profileRepository.findByPhoneNumber(phoneNumber);
        if (profileOpt.isEmpty()) {
            log.warn("Profile not found for phone: {}", phoneNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Profile not found."));
        }

        SMEProfile profile = profileOpt.get();
        String fcmToken = body.get("fcmToken");

        if (fcmToken == null || fcmToken.trim().isEmpty()) {
            log.warn("FCM Token is NULL or BLANK in the request body!");
            return ResponseEntity.badRequest().body(Map.of("error", "FCM token cannot be empty."));
        }

        log.info("Extracted Token: {}...", fcmToken.substring(0, Math.min(fcmToken.length(), 10)));

        profile.setFcmToken(fcmToken);
        profileRepository.save(profile);

        return ResponseEntity.ok(Map.of("message", "Device registered successfully for push notifications."));
    }
}