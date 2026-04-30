package com.emporia.backend.api;

import com.emporia.backend.model.SMEProfile;
// Import the new tool
import com.emporia.backend.mcp.NokiaCamaraTools;
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
    // Inject the fast REST client instead of the AI Agent
    private final NokiaCamaraTools nokiaCamaraTools;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {

        if (request.getRole() == SMEProfile.Role.DRIVER) {
            return ResponseEntity.badRequest().body(
                    new AuthResponse(null, "Drivers must use the /api/v1/auth/driver/login endpoint", null, null, null)
            );
        }

        // Lightning-fast REST call (takes milliseconds, not minutes)
        String decision = nokiaCamaraTools.verifyKycMatch(request.getPhoneNumber(), request.getBusinessName());

        if (!decision.contains("APPROVED")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "KYC Verification Failed via Nokia.", null, null, null));
        }

        String extractedAddress = decision.split("\\|")[1].trim();

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