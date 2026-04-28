package com.emporia.backend.api;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.orchestrator.GatekeeperAgent;
import com.emporia.backend.repository.SMEProfileRepository;
import com.emporia.backend.security.JwtService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SMEProfileRepository profileRepository;
    private final JwtService jwtService;
    private final GatekeeperAgent gatekeeperAgent;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        boolean isVerified = gatekeeperAgent.evaluateIdentity(request.getPhoneNumber(), request.getBusinessName());

        if (!isVerified) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "KYC Verification Failed: Name does not match telecom records."));
        }

        SMEProfile profile = profileRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseGet(() -> {
                    SMEProfile newUser = SMEProfile.builder()
                            .phoneNumber(request.getPhoneNumber())
                            .businessName(request.getBusinessName())
                            .kycVerified(true)
                            .build();
                    return profileRepository.save(newUser);
                });

        String token = jwtService.generateToken(profile.getPhoneNumber());

        return ResponseEntity.ok(new AuthResponse(token, "Login Successful & Verified via Nokia Network-as-Code"));
    }

    // --- DTOs ---

    @Data
    public static class LoginRequest {
        private String phoneNumber;
        private String businessName;
    }

    @Data
    public static class AuthResponse {
        private final String token;
        private final String message;
    }
}