package com.emporia.backend.api;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.model.TradeInvite;
import com.emporia.backend.repository.SMEProfileRepository;
import com.emporia.backend.repository.TradeInviteRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class TradeController {

    private final SMEProfileRepository profileRepository;
    private final TradeInviteRepository inviteRepository;

    @PostMapping("/generate-invite")
    public ResponseEntity<?> generateInvite(@RequestBody GenerateInviteRequest request) {

        Optional<SMEProfile> sellerOpt = profileRepository.findByPhoneNumber(request.getSellerPhoneNumber());

        if (sellerOpt.isEmpty() || sellerOpt.get().getRole() != SMEProfile.Role.SELLER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only verified Sellers can generate trade invites."));
        }

        SMEProfile seller = sellerOpt.get();

        String uniqueCode = "EMP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        TradeInvite newInvite = TradeInvite.builder()
                .inviteCode(uniqueCode)
                .seller(seller)
                .isUsed(false)
                .build();

        inviteRepository.save(newInvite);

        return ResponseEntity.ok(Map.of(
                "message", "Invite link generated successfully",
                "inviteCode", uniqueCode,
                "deepLinkUrl", "https://emporia-app.com/onboard?invite=" + uniqueCode
        ));
    }

    // --- DTO ---
    @Data
    public static class GenerateInviteRequest {
        private String sellerPhoneNumber;
    }
}