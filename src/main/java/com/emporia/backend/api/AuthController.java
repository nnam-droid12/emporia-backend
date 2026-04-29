package com.emporia.backend.api;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.orchestrator.GatekeeperAgent;
import com.emporia.backend.repository.SMEProfileRepository;
import com.emporia.backend.security.JwtService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SMEProfileRepository profileRepository;
    private final JwtService jwtService;
    private final GatekeeperAgent gatekeeperAgent;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {

        if (request.getRole() == SMEProfile.Role.DRIVER) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse(null, "Drivers must use the /api/v1/auth/driver/login endpoint", null, null, null)
            );
        }

        String agentDecision = gatekeeperAgent.evaluateIdentity(request.getPhoneNumber(), request.getBusinessName());

        if (!agentDecision.contains("APPROVED")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "KYC Verification Failed via Nokia.", null, null, null));
        }

        String extractedAddress = "Address not found";
        if (agentDecision.contains("|")) {
            extractedAddress = agentDecision.split("\\|")[1].trim();
        }

        SMEProfile profile = profileRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseGet(() -> {
                    SMEProfile newUser = SMEProfile.builder()
                            .phoneNumber(request.getPhoneNumber())
                            .businessName(request.getBusinessName())
                            .role(request.getRole() != null ? request.getRole() : SMEProfile.Role.BUYER)
                            .kycVerified(true)
                            .build();
                    return profileRepository.save(newUser);
                });

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", profile.getRole().name());
        extraClaims.put("businessName", profile.getBusinessName());

        String token = jwtService.generateToken(extraClaims, profile.getPhoneNumber());

        return ResponseEntity.ok(new AuthResponse(
                token,
                "Login Successful & Verified via Nokia Network-as-Code",
                extractedAddress,
                profile.getBusinessName(),
                profile.getRole().name()
        ));
    }


    @PostMapping("/driver/login")
    public ResponseEntity<AuthResponse> driverLogin(@RequestBody LoginRequest request) {

        SMEProfile profile = profileRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseGet(() -> {
                    SMEProfile newUser = SMEProfile.builder()
                            .phoneNumber(request.getPhoneNumber())
                            .businessName(request.getBusinessName() != null ? request.getBusinessName() : "Independent Courier")
                            .role(SMEProfile.Role.DRIVER)
                            .kycVerified(false)
                            .build();
                    return profileRepository.save(newUser);
                });

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", profile.getRole().name());
        extraClaims.put("businessName", profile.getBusinessName());

        String token = jwtService.generateToken(extraClaims, profile.getPhoneNumber());

        return ResponseEntity.ok(new AuthResponse(
                token,
                "Driver Login Successful",
                "N/A",
                profile.getBusinessName(),
                profile.getRole().name()
        ));
    }

    // --- Expanded DTOs ---

    @Data
    public static class LoginRequest {
        private String phoneNumber;
        private String businessName;
        private SMEProfile.Role role;
    }

    @Data
    @AllArgsConstructor
    public static class AuthResponse {
        private final String token;
        private final String message;
        private final String kycAddress;
        private final String businessName;
        private final String role;
    }
}