package com.emporia.backend.api;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.repository.SMEProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final SMEProfileRepository profileRepository;

    @PostMapping("/fcm-token")
    public ResponseEntity<?> updateFcmToken(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal SMEProfile profile) {

        if (profile == null) {
            log.error("Authentication Principal is NULL. Security context is empty!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized user."));
        }

        log.info("Received FCM token registration request for phone: {}", profile.getPhoneNumber());

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