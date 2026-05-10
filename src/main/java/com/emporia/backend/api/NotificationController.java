package com.emporia.backend.api;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.repository.SMEProfileRepository;
import com.emporia.backend.service.NotificationService; // ADD THIS IMPORT
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

    private final NotificationService notificationService;

    @PostMapping("/fcm-token")
    public ResponseEntity<?> updateFcmToken(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal SMEProfile profile) {

        if (profile == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized user."));
        }

        String fcmToken = body.get("fcmToken");

        if (fcmToken == null || fcmToken.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "FCM token cannot be empty."));
        }

        profile.setFcmToken(fcmToken);
        profileRepository.save(profile);

        log.info("Token saved for {}. Triggering test notification...", profile.getPhoneNumber());

        notificationService.sendPushNotification(
                fcmToken,
                "Emporia Connected! 🚀",
                "Your device is successfully linked to the Emporia Trust Engine."
        );

        return ResponseEntity.ok(Map.of("message", "Device registered successfully."));
    }
}