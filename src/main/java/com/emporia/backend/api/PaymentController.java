package com.emporia.backend.api;

import com.emporia.backend.model.BankAccount;
import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.model.TradeRecord;
import com.emporia.backend.repository.BankAccountRepository;
import com.emporia.backend.repository.SMEProfileRepository;
import com.emporia.backend.repository.TradeRecordRepository;
import com.emporia.backend.security.JwtService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final TradeRecordRepository tradeRepository;
    private final SMEProfileRepository profileRepository;
    private final JwtService jwtService;
    private final BankAccountRepository bankAccountRepository;

    @Value("${paystack.secret.key:sk_test_YOUR_PAYSTACK_SECRET_KEY}")
    private String paystackSecretKey;

    private final RestTemplate restTemplate = new RestTemplate();


    @PostMapping("/initialize/{tradeId}")
    public ResponseEntity<?> initializePayment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId,
            @RequestBody InitializePaymentRequest request) {

        String token = authHeader.substring(7);
        String buyerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();

        if (trade.getBuyer() == null || !trade.getBuyer().getPhoneNumber().equals(buyerPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the assigned Buyer can fund this escrow."));
        }

        if (trade.getPaymentStatus() == TradeRecord.PaymentStatus.ESCROW_FUNDED) {
            return ResponseEntity.badRequest().body(Map.of("error", "This trade has already been funded."));
        }

        long amountInKobo = (long) (trade.getAmount() * 100);
        String reference = "EMP_" + tradeId + "_" + UUID.randomUUID().toString().substring(0, 8);

        String url = "https://api.paystack.co/transaction/initialize";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(paystackSecretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "email", request.getEmail(), // Paystack requires an email
                "amount", amountInKobo,
                "reference", reference,
                "callback_url", request.getCallbackUrl()
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseData = (Map<String, Object>) response.getBody().get("data");

            return ResponseEntity.ok(Map.of(
                    "message", "Payment initialized",
                    "authorization_url", responseData.get("authorization_url"),
                    "reference", reference
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to initialize payment with Paystack."));
        }
    }

    @GetMapping("/verify/{tradeId}/{reference}")
    public ResponseEntity<?> verifyPayment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId,
            @PathVariable String reference) {

        String url = "https://api.paystack.co/transaction/verify/" + reference;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(paystackSecretKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            Map<String, Object> data = (Map<String, Object>) body.get("data");

            String status = (String) data.get("status");

            if ("success".equals(status)) {
                // Payment was successful, update the database!
                Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
                if (tradeOpt.isPresent()) {
                    TradeRecord trade = tradeOpt.get();
                    trade.setPaymentStatus(TradeRecord.PaymentStatus.ESCROW_FUNDED);
                    tradeRepository.save(trade);

                    return ResponseEntity.ok(Map.of(
                            "message", "Escrow successfully funded!",
                            "paymentStatus", trade.getPaymentStatus().name()
                    ));
                }
            }
            return ResponseEntity.badRequest().body(Map.of("error", "Payment verification failed or is still pending."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to verify payment with Paystack."));
        }
    }


    @PostMapping("/{tradeId}/release-next-batch")
    public ResponseEntity<?> releaseNextPaymentBatch(@PathVariable String tradeId) {

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();
        double totalAmount = trade.getAmount();
        double currentReleased = trade.getAmountReleased() != null ? trade.getAmountReleased() : 0.0;
        String message = "";
        double amountToTransfer = 0.0;

        switch (trade.getPaymentStatus()) {
            case PENDING:
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot release funds. Buyer has not funded the escrow yet."));

            case ESCROW_FUNDED:
                amountToTransfer = totalAmount / 3.0;
                trade.setPaymentStatus(TradeRecord.PaymentStatus.RELEASE_BATCH_1);
                trade.setAmountReleased(currentReleased + amountToTransfer);
                message = String.format("Batch 1 (1/3) released successfully: $%.2f", amountToTransfer);
                break;

            case RELEASE_BATCH_1:
                amountToTransfer = totalAmount / 2.0;
                trade.setPaymentStatus(TradeRecord.PaymentStatus.RELEASE_BATCH_2);
                trade.setAmountReleased(currentReleased + amountToTransfer);
                message = String.format("Batch 2 (1/2) released successfully: $%.2f", amountToTransfer);
                break;

            case RELEASE_BATCH_2:
                amountToTransfer = totalAmount - currentReleased;
                trade.setPaymentStatus(TradeRecord.PaymentStatus.FULLY_RELEASED);
                trade.setAmountReleased(totalAmount);
                message = String.format("Final Batch released successfully: $%.2f", amountToTransfer);
                break;

            case FULLY_RELEASED:
                return ResponseEntity.badRequest().body(Map.of("error", "All funds have already been released for this trade."));
        }


        try {

            Optional<BankAccount> bankOpt = bankAccountRepository.findBySeller(trade.getSeller());
            if (bankOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Seller has not set up a bank account. Cannot release funds."));
            }
            BankAccount sellerBank = bankOpt.get();

            String recipientCode = createPaystackRecipient(sellerBank);

            if (recipientCode != null) {
                initiatePaystackTransfer(recipientCode, amountToTransfer, trade.getTradeId());
            }
        } catch (Exception e) {
            System.err.println("Paystack Transfer API Error: " + e.getMessage());
        }

        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of(
                "message", message,
                "currentPaymentStatus", trade.getPaymentStatus().name(),
                "totalReleasedSoFar", trade.getAmountReleased()
        ));
    }


    private String createPaystackRecipient(BankAccount bankAccount) {
        String url = "https://api.paystack.co/transferrecipient";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(paystackSecretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);


        Map<String, Object> body = Map.of(
                "type", "nuban",
                "name", bankAccount.getAccountName(),
                "account_number", bankAccount.getAccountNumber(),
                "bank_code", "058",
                "currency", "NGN"
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            return (String) data.get("recipient_code");
        } catch (Exception e) {
            System.err.println("Failed to create recipient: " + e.getMessage());
            return null;
        }
    }

    private void initiatePaystackTransfer(String recipientCode, double amount, String tradeId) {
        String url = "https://api.paystack.co/transfer";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(paystackSecretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);


        long amountInKobo = (long) (amount * 100);

        Map<String, Object> body = Map.of(
                "source", "balance",
                "amount", amountInKobo,
                "recipient", recipientCode,
                "reason", "Emporia Escrow Release for Trade: " + tradeId
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            System.out.println("Transfer initiated successfully for trade: " + tradeId);
        } catch (Exception e) {
            System.err.println("Failed to initiate transfer: " + e.getMessage());
        }
    }

    @Data
    public static class InitializePaymentRequest {
        private String email;
        private String callbackUrl;
    }
}