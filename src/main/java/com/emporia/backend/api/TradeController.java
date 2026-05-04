package com.emporia.backend.api;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class TradeController {

    private final SMEProfileRepository profileRepository;
    private final TradeInviteRepository inviteRepository;
    private final TradeRecordRepository tradeRepository;
    private final JwtService jwtService;


    @PostMapping("/create")
    @Transactional
    public ResponseEntity<?> createTradeRecord(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateTradeRequest request) {

        String token = authHeader.substring(7);
        String sellerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<SMEProfile> sellerOpt = profileRepository.findByPhoneNumber(sellerPhoneNumber);
        if (sellerOpt.isEmpty() || sellerOpt.get().getRole() != SMEProfile.Role.SELLER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only verified Sellers can create trades."));
        }

        SMEProfile seller = sellerOpt.get();
        double[] coords = simulateGeocoding(request.getDeliveryAddress());

        TradeRecord newTrade = TradeRecord.builder()
                .seller(seller)
                .goodsType(request.getGoodsType())
                .quantity(request.getQuantity())
                .deliveryDate(request.getDeliveryDate())
                .deliveryTime(request.getDeliveryTime())
                .accountNumber(request.getAccountNumber())
                .accountName(request.getAccountName())
                .bankName(request.getBankName())
                .deliveryAddress(request.getDeliveryAddress())
                .latitude(coords[0])
                .longitude(coords[1])
                .build();

        TradeRecord savedTrade = tradeRepository.save(newTrade);
        savedTrade.setTradeId("trd_" + savedTrade.getId());

        String uniqueCode = "EMP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        TradeInvite newInvite = TradeInvite.builder()
                .inviteCode(uniqueCode)
                .seller(seller)
                .tradeRecord(savedTrade)
                .isUsed(false)
                .build();
        inviteRepository.save(newInvite);

        return ResponseEntity.ok(Map.of(
                "message", "Trade record created successfully",
                "tradeId", savedTrade.getTradeId(),
                "paymentStatus", savedTrade.getPaymentStatus().name(),
                "inviteCode", uniqueCode,
                "deepLinkUrl", "https://emporia-app.com/onboard?invite=" + uniqueCode
        ));
    }

    @GetMapping("/seller/getTradeForSeller")
    public ResponseEntity<?> getSellerDashboard(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Optional<SMEProfile> sellerOpt = profileRepository.findByPhoneNumber(jwtService.extractPhoneNumber(token));

        if (sellerOpt.isEmpty() || sellerOpt.get().getRole() != SMEProfile.Role.SELLER) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        List<TradeRecord> allTrades = tradeRepository.findBySeller(sellerOpt.get());

        List<Map<String, Object>> mappedTrades = allTrades.stream().map(trade -> {
            Map<String, Object> map = new HashMap<>();
            map.put("tradeId", trade.getTradeId());
            map.put("goods", trade.getGoodsType() + " (x" + trade.getQuantity() + ")");
            map.put("paymentStatus", trade.getPaymentStatus().name());
            map.put("tradeStatus", trade.getTradeStatus().name());
            map.put("deliveryAddress", trade.getDeliveryAddress());
            map.put("latitude", trade.getLatitude());
            map.put("longitude", trade.getLongitude());

            map.put("buyerName", trade.getBuyer() != null ? trade.getBuyer().getBusinessName() : "Awaiting Buyer");
            map.put("buyerPhone", trade.getBuyer() != null ? trade.getBuyer().getPhoneNumber() : null);
            map.put("driverName", trade.getDriver() != null ? trade.getDriver().getBusinessName() : "Unassigned");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("totalTrades", allTrades.size(), "dashboardRecords", mappedTrades));
    }


    @GetMapping("/buyer/getTradeForBuyer")
    public ResponseEntity<?> getBuyerDashboard(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Optional<SMEProfile> buyerOpt = profileRepository.findByPhoneNumber(jwtService.extractPhoneNumber(token));

        if (buyerOpt.isEmpty() || buyerOpt.get().getRole() != SMEProfile.Role.BUYER) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        List<TradeRecord> allTrades = tradeRepository.findByBuyer(buyerOpt.get());

        List<Map<String, Object>> mappedTrades = allTrades.stream().map(trade -> {
            Map<String, Object> map = new HashMap<>();
            map.put("tradeId", trade.getTradeId());
            map.put("goods", trade.getGoodsType() + " (x" + trade.getQuantity() + ")");
            map.put("paymentStatus", trade.getPaymentStatus().name());
            map.put("tradeStatus", trade.getTradeStatus().name());
            map.put("deliveryAddress", trade.getDeliveryAddress());

            map.put("sellerName", trade.getSeller().getBusinessName());
            map.put("sellerPhone", trade.getSeller().getPhoneNumber());
            map.put("driverName", trade.getDriver() != null ? trade.getDriver().getBusinessName() : "Unassigned");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("totalTrades", allTrades.size(), "dashboardRecords", mappedTrades));
    }


    @PostMapping("/{tradeId}/driver-invite")
    public ResponseEntity<?> generateDriverInvite(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId) {

        String token = authHeader.substring(7);
        String sellerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<SMEProfile> sellerOpt = profileRepository.findByPhoneNumber(sellerPhoneNumber);
        if (sellerOpt.isEmpty() || sellerOpt.get().getRole() != SMEProfile.Role.SELLER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only verified Sellers can generate driver links."));
        }

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty() || !tradeOpt.get().getSeller().getId().equals(sellerOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found or does not belong to you."));
        }

        TradeRecord trade = tradeOpt.get();

        String uniqueCode = "DRV-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        TradeInvite newInvite = TradeInvite.builder()
                .inviteCode(uniqueCode)
                .seller(sellerOpt.get())
                .tradeRecord(trade)
                .isUsed(false)
                .build();
        inviteRepository.save(newInvite);

        return ResponseEntity.ok(Map.of(
                "message", "Driver invite generated successfully",
                "tradeId", trade.getTradeId(),
                "driverCode", uniqueCode,
                "deepLinkUrl", "https://emporia-app.com/driver-onboard?code=" + uniqueCode
        ));
    }


    private double[] simulateGeocoding(String address) {
        if (address != null && address.toLowerCase().contains("lagos")) return new double[]{6.5244, 3.3792};
        else if (address != null && address.toLowerCase().contains("tokyo")) return new double[]{35.6762, 139.6503};
        else return new double[]{9.0820, 8.6753};
    }

    @Data
    public static class CreateTradeRequest {
        private String goodsType;
        private Integer quantity;
        private LocalDate deliveryDate;
        private LocalTime deliveryTime;
        private String accountNumber;
        private String accountName;
        private String bankName;
        private String deliveryAddress;
    }
}