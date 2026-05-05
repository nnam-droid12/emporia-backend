package com.emporia.backend.api;

import com.emporia.backend.model.BankAccount;
import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.repository.BankAccountRepository;
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
@RequestMapping("/api/v1/bank-account")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountRepository bankRepository;
    private final SMEProfileRepository profileRepository;
    private final JwtService jwtService;

    @PostMapping("/save")
    public ResponseEntity<?> saveOrUpdateAccount(@RequestHeader("Authorization") String authHeader, @RequestBody BankAccountRequest request) {
        String token = authHeader.substring(7);
        Optional<SMEProfile> sellerOpt = profileRepository.findByPhoneNumber(jwtService.extractPhoneNumber(token));

        if (sellerOpt.isEmpty() || sellerOpt.get().getRole() != SMEProfile.Role.SELLER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only Sellers can manage bank accounts."));
        }

        SMEProfile seller = sellerOpt.get();
        Optional<BankAccount> existingOpt = bankRepository.findBySeller(seller);

        BankAccount account;
        if (existingOpt.isPresent()) {
            account = existingOpt.get();
            account.setAccountName(request.getAccountName());
            account.setAccountNumber(request.getAccountNumber());
            account.setBankName(request.getBankName());
        } else {
            account = BankAccount.builder()
                    .seller(seller)
                    .accountName(request.getAccountName())
                    .accountNumber(request.getAccountNumber())
                    .bankName(request.getBankName())
                    .build();
        }

        bankRepository.save(account);
        return ResponseEntity.ok(Map.of("message", "Bank details saved successfully"));
    }

    @GetMapping("/details")
    public ResponseEntity<?> getAccountDetails(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Optional<SMEProfile> sellerOpt = profileRepository.findByPhoneNumber(jwtService.extractPhoneNumber(token));

        if (sellerOpt.isPresent()) {
            Optional<BankAccount> bankOpt = bankRepository.findBySeller(sellerOpt.get());
            if (bankOpt.isPresent()) {
                return ResponseEntity.ok(bankOpt.get());
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Bank account not found."));
    }

    @Data
    public static class BankAccountRequest {
        private String accountNumber;
        private String accountName;
        private String bankName;
    }
}