package com.emporia.backend.api;

import com.emporia.backend.mcp.NokiaCamaraTools;
import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.model.TradeInvite;
import com.emporia.backend.model.TradeRecord;
import com.emporia.backend.repository.SMEProfileRepository;
import com.emporia.backend.repository.TradeInviteRepository;
import com.emporia.backend.repository.TradeRecordRepository;
import com.emporia.backend.security.JwtService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SMEProfileRepository profileRepository;
    private final TradeInviteRepository inviteRepository;
    private final JwtService jwtService;
    private final NokiaCamaraTools nokiaCamaraTools;
    private final TradeRecordRepository tradeRepository;



    @PostMapping("/seller/login")
    public ResponseEntity<?> sellerLogin(@RequestBody SellerLoginRequest request) {

        if (nokiaCamaraTools.hasRecentSimSwap(request.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "swapped", true,
                            "error", "SECURITY ALERT: Recent SIM Swap detected. Please use a different number. Account frozen."
                    ));
        }

        String decision = nokiaCamaraTools.verifyKycMatch(request.getPhoneNumber(), request.getBusinessName());
        if (!decision.contains("APPROVED")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "KYC Verification Failed via Nokia Telecom records."));
        }

        String extractedAddress = decision.split("\\|")[1].trim();

        SMEProfile profile = profileRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseGet(() -> profileRepository.save(SMEProfile.builder()
                        .phoneNumber(request.getPhoneNumber())
                        .businessName(request.getBusinessName())
                        .role(SMEProfile.Role.SELLER)
                        .kycVerified(true)
                        .build()));

        Map<String, Object> extraClaims = Map.of("role", profile.getRole().name(), "businessName", profile.getBusinessName());

        return ResponseEntity.ok(Map.of(
                "token", jwtService.generateToken(extraClaims, profile.getPhoneNumber()),
                "message", "Seller Authenticated & Verified",
                "kycAddress", extractedAddress,
                "role", profile.getRole().name()
        ));
    }

    @PostMapping("/buyer/login")
    public ResponseEntity<?> buyerLogin(@RequestBody BuyerLoginRequest request) {

        if (nokiaCamaraTools.hasRecentSimSwap(request.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "SECURITY ALERT: Recent SIM Swap detected. Buyer account frozen."));
        }

        if (!nokiaCamaraTools.verifyPhoneNumber(request.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "swapped", true,
                            "error", "Phone Number Verification Failed."));
        }

        SMEProfile profile = profileRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseGet(() -> profileRepository.save(SMEProfile.builder()
                        .phoneNumber(request.getPhoneNumber())
                        .personalName(request.getPersonalName())
                        .role(SMEProfile.Role.BUYER)
                        .kycVerified(true)
                        .build()));

        if (request.getInviteCode() != null && !request.getInviteCode().isEmpty()) {
            Optional<TradeInvite> inviteOpt = inviteRepository.findByInviteCodeAndIsUsedFalse(request.getInviteCode());

            if (inviteOpt.isPresent()) {
                TradeInvite invite = inviteOpt.get();
                invite.setUsed(true);
                inviteRepository.save(invite);

                TradeRecord trade = invite.getTradeRecord();
                if (trade != null) {
                    trade.setBuyer(profile);
                    if (trade.getDriver() != null && trade.getTradeStatus() != TradeRecord.TradeStatus.DRIVER_PENDING) {
                        trade.setTradeStatus(TradeRecord.TradeStatus.ACTIVE);
                    } else {
                        trade.setTradeStatus(TradeRecord.TradeStatus.BUYER_JOINED);
                    }
                    tradeRepository.save(trade);
                }
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid or expired invite code."));
            }
        }

        Map<String, Object> extraClaims = Map.of(
                "role", profile.getRole().name(),
                "name", profile.getPersonalName() != null ? profile.getPersonalName() : "Buyer"
        );

        return ResponseEntity.ok(Map.of(
                "token", jwtService.generateToken(extraClaims, profile.getPhoneNumber()),
                "message", "Buyer Login Successful",
                "role", profile.getRole().name()
        ));
    }


    @PostMapping("/driver/login")
    public ResponseEntity<?> driverLogin(@RequestBody DriverLoginRequest request) {

        if (nokiaCamaraTools.hasRecentSimSwap(request.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "swapped", true,
                            "error", "SECURITY ALERT: Recent SIM Swap detected. Driver account frozen for investigation."));
        }

        if (!nokiaCamaraTools.verifyPhoneNumber(request.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Phone Number Verification Failed."));
        }

        SMEProfile profile = profileRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseGet(() -> profileRepository.save(SMEProfile.builder()
                        .phoneNumber(request.getPhoneNumber())
                        .businessName(request.getBusinessName())
                        .role(SMEProfile.Role.DRIVER)
                        .kycVerified(false)
                        .build()));


        if (request.getLinkCode() != null && !request.getLinkCode().isEmpty()) {
            Optional<TradeInvite> inviteOpt = inviteRepository.findByInviteCodeAndIsUsedFalse(request.getLinkCode());

            if (inviteOpt.isPresent()) {
                TradeInvite invite = inviteOpt.get();
                invite.setUsed(true);
                inviteRepository.save(invite);

                TradeRecord trade = invite.getTradeRecord();
                if (trade != null) {
                    trade.setDriver(profile);
                    if (trade.getBuyer() != null) {
                        trade.setTradeStatus(TradeRecord.TradeStatus.ACTIVE);
                    } else {
                        trade.setTradeStatus(TradeRecord.TradeStatus.DRIVER_ASSIGNED);
                    }
                    tradeRepository.save(trade);
                }
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid or expired invite code. Please request a new link from the Seller."));
            }
        }

        Map<String, Object> extraClaims = Map.of("role", profile.getRole().name(), "businessName", profile.getBusinessName());
        String simulatedDriverAddress = "Logistics Hub, Terminal B, Sandbox City";

        return ResponseEntity.ok(Map.of(
                "token", jwtService.generateToken(extraClaims, profile.getPhoneNumber()),
                "message", "Driver Authenticated Safely",
                "kycAddress", simulatedDriverAddress,
                "role", profile.getRole().name()
        ));
    }

    // --- DTOs ---
    @Data
    public static class SellerLoginRequest {
        private String phoneNumber;
        private String businessName;
    }

    @Data
    public static class BuyerLoginRequest {
        private String phoneNumber;
        private String inviteCode;
        private String personalName;
    }

    @Data
    public static class DriverLoginRequest {
        private String phoneNumber;
        private String businessName;
        private String linkCode;
    }
}