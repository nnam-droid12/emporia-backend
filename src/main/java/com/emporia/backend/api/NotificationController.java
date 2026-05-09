package com.emporia.backend.api;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.repository.SMEProfileRepository;
import com.emporia.backend.security.JwtService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

        import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final SMEProfileRepository profileRepository;
    private final JwtService jwtService;

    @PutMapping("/fcm-token")
    public ResponseEntity<?> updateFcmToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody FcmTokenRequest request) {


        String token = authHeader.substring(7);
        String phoneNumber = jwtService.extractPhoneNumber(token);

        Optional<SMEProfile> profileOpt = profileRepository.findByPhoneNumber(phoneNumber);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Profile not found."));
        }

        SMEProfile profile = profileOpt.get();

        if (request.getFcmToken() == null || request.getFcmToken().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "FCM token cannot be empty."));
        }

        profile.setFcmToken(request.getFcmToken());
        profileRepository.save(profile);

        return ResponseEntity.ok(Map.of("message", "Device registered successfully for push notifications."));
    }

    // --- DTO ---
    @Data
    public static class FcmTokenRequest {
        private String fcmToken;
    }
}
