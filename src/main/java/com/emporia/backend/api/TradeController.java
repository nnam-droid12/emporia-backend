package com.emporia.backend.api;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.model.TradeInvite;
import com.emporia.backend.model.TradeRecord;
import com.emporia.backend.repository.SMEProfileRepository;
import com.emporia.backend.repository.TradeInviteRepository;
import com.emporia.backend.repository.TradeRecordRepository;
import com.emporia.backend.security.JwtService;
import com.fasterxml.jackson.annotation.JsonFormat;
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

        String secretCode = String.format("%04d", new Random().nextInt(10000));

        TradeRecord newTrade = TradeRecord.builder()
                .seller(seller)
                .goodsType(request.getGoodsType())
                .quantity(request.getQuantity())
                .amount(request.getAmount())
                .deliveryDate(request.getDeliveryDate())
                .deliveryTime(request.getDeliveryTime())
                .deliveryCode(secretCode)
                .build();

        TradeRecord savedTrade = tradeRepository.save(newTrade);
        savedTrade.setTradeId("trd_" + savedTrade.getId());

        if (request.getBuyerPhoneNumber() != null && !request.getBuyerPhoneNumber().isEmpty()) {
            Optional<SMEProfile> buyerOpt = profileRepository.findByPhoneNumber(request.getBuyerPhoneNumber());
            SMEProfile buyerProfile;

            if (buyerOpt.isPresent()) {
                if (buyerOpt.get().getRole() != SMEProfile.Role.BUYER) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Phone number is registered to a non-buyer account."));
                }
                buyerProfile = buyerOpt.get();
            } else {
                if (request.getPersonalName() == null || request.getPersonalName().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "To add a new buyer directly, please provide their personalName."));
                }

                buyerProfile = profileRepository.save(SMEProfile.builder()
                        .phoneNumber(request.getBuyerPhoneNumber())
                        .personalName(request.getPersonalName())
                        .businessName(request.getPersonalName())
                        .role(SMEProfile.Role.BUYER)
                        .kycVerified(true)
                        .build());
            }

            savedTrade.setBuyer(buyerProfile);
            savedTrade.setTradeStatus(TradeRecord.TradeStatus.BUYER_JOINED);
            tradeRepository.save(savedTrade);

            return ResponseEntity.ok(Map.of(
                    "message", buyerOpt.isPresent() ? "Trade instantly assigned to existing Buyer." : "New Buyer pre-registered and trade assigned.",
                    "tradeId", savedTrade.getTradeId(),
                    "buyerName", buyerProfile.getPersonalName() != null ? buyerProfile.getPersonalName() : "Unnamed Buyer",
                    "paymentStatus", savedTrade.getPaymentStatus().name()
            ));
        }


        tradeRepository.save(savedTrade);

        String uniqueCode = "EMP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        TradeInvite invite = TradeInvite.builder()
                .inviteCode(uniqueCode)
                .seller(sellerOpt.get())
                .tradeRecord(savedTrade)
                .isUsed(false)
                .build();
        inviteRepository.save(invite);

        return ResponseEntity.ok(Map.of(
                "message", "Trade record created successfully. Awaiting Buyer Onboarding.",
                "tradeId", savedTrade.getTradeId(),
                "inviteCode", uniqueCode,
                "deepLinkUrl", "https://emporia-frontend.vercel.app/buyer/onboarding?invite=" + uniqueCode,
                "paymentStatus", savedTrade.getPaymentStatus().name()
        ));
    }



    @GetMapping("/seller/network")
    public ResponseEntity<?> getSellerNetwork(@RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        String sellerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<SMEProfile> sellerOpt = profileRepository.findByPhoneNumber(sellerPhoneNumber);
        if (sellerOpt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<TradeRecord> trades = tradeRepository.findBySeller(sellerOpt.get());

        List<Map<String, String>> previousBuyers = trades.stream()
                .filter(t -> t.getBuyer() != null)
                .map(t -> Map.of(
                        "name", t.getBuyer().getPersonalName() != null ? t.getBuyer().getPersonalName() : "Unnamed Buyer",
                        "phoneNumber", t.getBuyer().getPhoneNumber()
                ))
                .distinct()
                .toList();


        List<Map<String, String>> previousDrivers = trades.stream()
                .filter(t -> t.getDriver() != null)
                .map(t -> Map.of(
                        "name", t.getDriver().getBusinessName() != null ? t.getDriver().getBusinessName() : "Unnamed Driver",
                        "phoneNumber", t.getDriver().getPhoneNumber()
                ))
                .distinct()
                .toList();

        return ResponseEntity.ok(Map.of(
                "buyers", previousBuyers,
                "drivers", previousDrivers
        ));
    }

    @PutMapping("/{tradeId}/address")
    public ResponseEntity<?> updateDeliveryAddress(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId,
            @RequestBody UpdateAddressRequest request) {

        String token = authHeader.substring(7);
        String buyerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();

        if (trade.getBuyer() == null || !trade.getBuyer().getPhoneNumber().equals(buyerPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the assigned Buyer can update the delivery address."));
        }

        double[] coords = simulateGeocoding(request.getDeliveryAddress());

        trade.setDeliveryAddress(request.getDeliveryAddress());
        trade.setLatitude(coords[0]);
        trade.setLongitude(coords[1]);
        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of("message", "Delivery address updated successfully. Seller can now view the route."));
    }


    @GetMapping("/seller/network/buyers")
    public ResponseEntity<?> getSellerBuyers(@RequestHeader("Authorization") String authHeader) {
        return getNetworkData(authHeader, true);
    }

    @GetMapping("/seller/network/drivers")
    public ResponseEntity<?> getSellerDrivers(@RequestHeader("Authorization") String authHeader) {
        return getNetworkData(authHeader, false);
    }

    private ResponseEntity<?> getNetworkData(String authHeader, boolean fetchBuyers) {
        String token = authHeader.substring(7);
        Optional<SMEProfile> sellerOpt = profileRepository.findByPhoneNumber(jwtService.extractPhoneNumber(token));

        if (sellerOpt.isEmpty() || sellerOpt.get().getRole() != SMEProfile.Role.SELLER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<SMEProfile> network = fetchBuyers
                ? tradeRepository.findDistinctBuyersBySeller(sellerOpt.get())
                : tradeRepository.findDistinctDriversBySeller(sellerOpt.get());

        List<Map<String, String>> response = network.stream().map(profile -> Map.of(
                "businessName", profile.getBusinessName(),
                "phoneNumber", profile.getPhoneNumber()
        )).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "totalCount", response.size(),
                fetchBuyers ? "buyers" : "drivers", response
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
            map.put("amount", trade.getAmount());
            map.put("deliveryDate", trade.getDeliveryDate());
            map.put("deliveryTime", trade.getDeliveryTime());
            map.put("tradeStatus", trade.getTradeStatus().name());

            // NEW: Payment tracking for the dashboard
            map.put("totalAmount", trade.getAmount());
            map.put("amountReleased", trade.getAmountReleased() != null ? trade.getAmountReleased() : 0.0);
            map.put("paymentStatus", trade.getPaymentStatus().name());


            double remaining = trade.getAmount() - (trade.getAmountReleased() != null ? trade.getAmountReleased() : 0.0);
            map.put("amountRemaining", Math.max(0.0, remaining));
            map.put("deliveryAddress", trade.getDeliveryAddress());
            map.put("latitude", trade.getLatitude());
            map.put("longitude", trade.getLongitude());
            map.put("flagReason", trade.getTradeStatus() == TradeRecord.TradeStatus.FLAGGED ? trade.getFlagReason() : null);

            String displayBuyerName = "awaiting buyer";
            if (trade.getBuyer() != null) {
                if (trade.getBuyer().getPersonalName() != null && !trade.getBuyer().getPersonalName().isEmpty()) {
                    displayBuyerName = trade.getBuyer().getPersonalName();
                } else if (trade.getBuyer().getBusinessName() != null && !trade.getBuyer().getBusinessName().isEmpty()) {
                    displayBuyerName = trade.getBuyer().getBusinessName();
                } else {
                    displayBuyerName = "Unnamed Buyer";
                }
            }
            map.put("buyerName", displayBuyerName);
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
            map.put("amount", trade.getAmount());
            map.put("deliveryDate", trade.getDeliveryDate());
            map.put("deliveryTime", trade.getDeliveryTime());
            map.put("tradeStatus", trade.getTradeStatus().name());
            map.put("deliveryAddress", trade.getDeliveryAddress());
            map.put("deliveryCode", trade.getDeliveryCode());

            map.put("totalAmount", trade.getAmount());
            map.put("amountReleased", trade.getAmountReleased() != null ? trade.getAmountReleased() : 0.0);
            map.put("paymentStatus", trade.getPaymentStatus().name());

            map.put("sellerName", trade.getSeller().getBusinessName());
            map.put("sellerPhone", trade.getSeller().getPhoneNumber());
            map.put("driverName", trade.getDriver() != null ? trade.getDriver().getBusinessName() : "Unassigned");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("totalTrades", allTrades.size(), "dashboardRecords", mappedTrades));
    }

    @GetMapping("/driver/getTradeForDriver")
    public ResponseEntity<?> getDriverDashboard(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Optional<SMEProfile> driverOpt = profileRepository.findByPhoneNumber(jwtService.extractPhoneNumber(token));

        if (driverOpt.isEmpty() || driverOpt.get().getRole() != SMEProfile.Role.DRIVER) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        List<TradeRecord> allTrades = tradeRepository.findByDriver(driverOpt.get());

        List<Map<String, Object>> mappedTrades = allTrades.stream().map(trade -> {
            Map<String, Object> map = new HashMap<>();
            map.put("tradeId", trade.getTradeId());
            map.put("goods", trade.getGoodsType() + " (x" + trade.getQuantity() + ")");
            map.put("tradeStatus", trade.getTradeStatus().name());
            map.put("deliveryDate", trade.getDeliveryDate());
            map.put("deliveryTime", trade.getDeliveryTime());
            map.put("deliveryAddress", trade.getDeliveryAddress());
            map.put("sellerName", trade.getSeller().getBusinessName());
            map.put("sellerPhone", trade.getSeller().getPhoneNumber());
            String displayBuyerName = "awaiting buyer";
            if (trade.getBuyer() != null) {
                if (trade.getBuyer().getPersonalName() != null && !trade.getBuyer().getPersonalName().isEmpty()) {
                    displayBuyerName = trade.getBuyer().getPersonalName();
                } else if (trade.getBuyer().getBusinessName() != null && !trade.getBuyer().getBusinessName().isEmpty()) {
                    displayBuyerName = trade.getBuyer().getBusinessName();
                } else {
                    displayBuyerName = "Unnamed Buyer";
                }
            }
            map.put("buyerName", displayBuyerName);
            map.put("buyerPhone", trade.getBuyer() != null ? trade.getBuyer().getPhoneNumber() : null);
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
                "deepLinkUrl", " https://emporia-frontend.vercel.app/driver/onboarding?code=" + uniqueCode
        ));
    }


    @PostMapping("/{tradeId}/assign-driver")
    public ResponseEntity<?> assignExistingDriver(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId,
            @RequestBody Map<String, String> payload) {

        String token = authHeader.substring(7);
        String sellerPhone = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty() || !tradeOpt.get().getSeller().getPhoneNumber().equals(sellerPhone)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found or unauthorized."));
        }

        String driverPhone = payload.get("driverPhoneNumber");
        Optional<SMEProfile> driverOpt = profileRepository.findByPhoneNumber(driverPhone);
        if (driverOpt.isEmpty() || driverOpt.get().getRole() != SMEProfile.Role.DRIVER) {
            return ResponseEntity.badRequest().body(Map.of("error", "Verified Driver not found."));
        }

        TradeRecord trade = tradeOpt.get();
        trade.setDriver(driverOpt.get());
        trade.setTradeStatus(TradeRecord.TradeStatus.DRIVER_PENDING);
        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of(
                "message", "Assignment sent to driver. Waiting for their acceptance.",
                "tradeStatus", trade.getTradeStatus().name()
        ));
    }


    @PostMapping("/{tradeId}/driver-accept")
    public ResponseEntity<?> driverAcceptTrade(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId) {

        String token = authHeader.substring(7);
        String driverPhone = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found."));

        TradeRecord trade = tradeOpt.get();

        if (trade.getDriver() == null || !trade.getDriver().getPhoneNumber().equals(driverPhone)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not assigned to this trade."));
        }

        if (trade.getTradeStatus() != TradeRecord.TradeStatus.DRIVER_PENDING) {
            return ResponseEntity.badRequest().body(Map.of("error", "This trade is not pending your acceptance."));
        }

        trade.setTradeStatus(TradeRecord.TradeStatus.IN_TRANSIT);
        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of(
                "message", "You have successfully accepted the trade assignment. The goods are now In Transit.",
                "tradeStatus", trade.getTradeStatus().name()
        ));
    }


    @GetMapping("/track/{tradeId}")
    public ResponseEntity<?> trackTrade(@PathVariable String tradeId) {
        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();

        return ResponseEntity.ok(Map.of(
                "tradeId", trade.getTradeId(),
                "goodsName", trade.getGoodsType(),
                "buyerPhoneNumber", trade.getBuyer() != null ? trade.getBuyer().getPhoneNumber() : "Unassigned",
                "driverName", trade.getDriver() != null ? trade.getDriver().getBusinessName() : "Unassigned",
                "tradeStatus", trade.getTradeStatus().name()
        ));
    }

    @PostMapping("/{tradeId}/deliver")
    public ResponseEntity<?> confirmDelivery(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId,
            @RequestBody Map<String, String> payload) {

        String token = authHeader.substring(7);
        String driverPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();

        if (trade.getDriver() == null || !trade.getDriver().getPhoneNumber().equals(driverPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not assigned to this trade."));
        }

        String providedCode = payload.get("deliveryCode");
        if (providedCode == null || !providedCode.equals(trade.getDeliveryCode())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid delivery code. Ask the Buyer for the correct code."));
        }


        trade.setTradeStatus(TradeRecord.TradeStatus.DELIVERED);
        trade.setPaymentStatus(TradeRecord.PaymentStatus. FULLY_RELEASED);
        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of("message", "Delivery confirmed securely! Funds released to Seller."));
    }


    @PutMapping("/{tradeId}/edit")
    public ResponseEntity<?> editTradeRecord(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId,
            @RequestBody EditTradeRequest request) {

        String token = authHeader.substring(7);
        String sellerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();

        if (!trade.getSeller().getPhoneNumber().equals(sellerPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the original Seller can edit this trade."));
        }

        if (trade.getTradeStatus() == TradeRecord.TradeStatus.DELIVERED) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Cannot edit a trade that is already Delivered."));
        }

        if (request.getGoodsType() != null) trade.setGoodsType(request.getGoodsType());
        if (request.getQuantity() != null) trade.setQuantity(request.getQuantity());
        if (request.getAmount() != null) trade.setAmount(request.getAmount());
        if (request.getDeliveryDate() != null) trade.setDeliveryDate(request.getDeliveryDate());
        if (request.getDeliveryTime() != null) trade.setDeliveryTime(request.getDeliveryTime());

        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of("message", "Trade updated successfully.", "tradeId", trade.getTradeId()));
    }


    @PutMapping("/{tradeId}/unassign-driver")
    public ResponseEntity<?> unassignDriver(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId) {

        String token = authHeader.substring(7);
        String sellerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();

        if (!trade.getSeller().getPhoneNumber().equals(sellerPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the Seller can unassign a driver."));
        }

        if (trade.getTradeStatus() == TradeRecord.TradeStatus.IN_TRANSIT ||
                trade.getTradeStatus() == TradeRecord.TradeStatus.DELIVERED) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Cannot unassign a driver once the delivery is In Transit or Delivered."));
        }

        trade.setDriver(null);

        if (trade.getBuyer() != null) {
            trade.setTradeStatus(TradeRecord.TradeStatus.BUYER_JOINED);
        } else {
            trade.setTradeStatus(TradeRecord.TradeStatus.PENDING); // Fixed from CREATED
        }

        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of("message", "Driver has been unassigned successfully. Status reverted."));
    }

    @PostMapping("/{tradeId}/flag")
    public ResponseEntity<?> flagTrade(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId,
            @RequestBody Map<String, String> payload) {

        String token = authHeader.substring(7);
        String buyerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();

        if (trade.getBuyer() == null || !trade.getBuyer().getPhoneNumber().equals(buyerPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the assigned Buyer can flag this trade."));
        }

        String reason = payload.getOrDefault("reason", "No reason provided");

        trade.setTradeStatus(TradeRecord.TradeStatus.FLAGGED);
        trade.setFlagReason(reason);

        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of("message", "Trade has been flagged for dispute. Seller has been notified."));
    }


    @PostMapping("/{tradeId}/unflag")
    public ResponseEntity<?> unflagTrade(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId) {

        String token = authHeader.substring(7);
        String buyerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();

        if (trade.getBuyer() == null || !trade.getBuyer().getPhoneNumber().equals(buyerPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the assigned Buyer can unflag this trade."));
        }

        if (trade.getTradeStatus() != TradeRecord.TradeStatus.FLAGGED) {
            return ResponseEntity.badRequest().body(Map.of("error", "This trade is not currently flagged."));
        }

        if (trade.getDriver() != null) {

            trade.setTradeStatus(TradeRecord.TradeStatus.DRIVER_PENDING);
        } else {
            trade.setTradeStatus(TradeRecord.TradeStatus.BUYER_JOINED);
        }

        trade.setFlagReason(null);

        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of(
                "message", "Trade dispute resolved. The trade has been unflagged successfully.",
                "tradeStatus", trade.getTradeStatus().name()
        ));
    }


    @GetMapping("/{tradeId}/view-dispute")
    public ResponseEntity<?> getTradeDispute(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId) {

        String token = authHeader.substring(7);
        String sellerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));

        TradeRecord trade = tradeOpt.get();

        if (!trade.getSeller().getPhoneNumber().equals(sellerPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the original Seller can view this dispute."));
        }

        if (trade.getTradeStatus() != TradeRecord.TradeStatus.FLAGGED) {
            return ResponseEntity.ok(Map.of(
                    "message", "This trade is currently not flagged.",
                    "tradeStatus", trade.getTradeStatus().name()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "tradeId", trade.getTradeId(),
                "tradeStatus", trade.getTradeStatus().name(),
                "buyerName", trade.getBuyer() != null ? trade.getBuyer().getBusinessName() : "Unknown",
                "buyerPhone", trade.getBuyer() != null ? trade.getBuyer().getPhoneNumber() : "Unknown",
                "flagReason", trade.getFlagReason() != null ? trade.getFlagReason() : "No specific reason provided."
        ));
    }


    @PostMapping("/{tradeId}/buyer-invite")
    public ResponseEntity<?> generateBuyerInvite(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId) {

        String token = authHeader.substring(7);
        String sellerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<SMEProfile> sellerOpt = profileRepository.findByPhoneNumber(sellerPhoneNumber);
        if (sellerOpt.isEmpty() || sellerOpt.get().getRole() != SMEProfile.Role.SELLER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only verified Sellers can generate buyer links."));
        }

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty() || !tradeOpt.get().getSeller().getId().equals(sellerOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found or does not belong to you."));
        }

        TradeRecord trade = tradeOpt.get();

        String uniqueCode = "EMP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        TradeInvite newInvite = TradeInvite.builder()
                .inviteCode(uniqueCode)
                .seller(sellerOpt.get())
                .tradeRecord(trade)
                .isUsed(false)
                .build();
        inviteRepository.save(newInvite);

        return ResponseEntity.ok(Map.of(
                "message", "New Buyer invite generated successfully",
                "tradeId", trade.getTradeId(),
                "inviteCode", uniqueCode,
                "deepLinkUrl", "https://emporia-frontend.vercel.app/buyer/onboarding?invite=" + uniqueCode
        ));
    }

    @GetMapping("/{tradeId}")
    public ResponseEntity<?> getTradeById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String tradeId) {

        String token = authHeader.substring(7);
        String sellerPhoneNumber = jwtService.extractPhoneNumber(token);

        Optional<TradeRecord> tradeOpt = tradeRepository.findByTradeId(tradeId);
        if (tradeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Trade not found"));
        }

        TradeRecord trade = tradeOpt.get();

        if (!trade.getSeller().getPhoneNumber().equals(sellerPhoneNumber)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to view this trade."));
        }


        Map<String, Object> response = new HashMap<>();
        response.put("tradeId", trade.getTradeId());
        response.put("goodsType", trade.getGoodsType());
        response.put("quantity", trade.getQuantity());
        response.put("amount", trade.getAmount());
        response.put("deliveryDate", trade.getDeliveryDate());
        response.put("deliveryTime", trade.getDeliveryTime());
        response.put("tradeStatus", trade.getTradeStatus().name());
        response.put("paymentStatus", trade.getPaymentStatus().name());
        response.put("deliveryAddress", trade.getDeliveryAddress());
        response.put("latitude", trade.getLatitude());
        response.put("longitude", trade.getLongitude());

        response.put("flagReason", trade.getTradeStatus() == TradeRecord.TradeStatus.FLAGGED ? trade.getFlagReason() : null);

        response.put("buyerName", trade.getBuyer() != null ? trade.getBuyer().getBusinessName() : "Awaiting Buyer");
        response.put("buyerPhone", trade.getBuyer() != null ? trade.getBuyer().getPhoneNumber() : null);
        response.put("driverName", trade.getDriver() != null ? trade.getDriver().getBusinessName() : "Unassigned");
        response.put("driverPhone", trade.getDriver() != null ? trade.getDriver().getPhoneNumber() : null);

        return ResponseEntity.ok(response);
    }


    private double[] simulateGeocoding(String address) {
        if (address != null && address.toLowerCase().contains("lagos")) return new double[]{6.5244, 3.3792};
        else if (address != null && address.toLowerCase().contains("tokyo")) return new double[]{35.6762, 139.6503};
        else return new double[]{9.0820, 8.6753};
    }

    // ---------------------------------------------------------
    // DTOs
    // ---------------------------------------------------------
    @Data
    public static class CreateTradeRequest {
        private String goodsType;
        private Integer quantity;
        private Double amount;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate deliveryDate;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime deliveryTime;

        private String accountNumber;
        private String accountName;
        private String bankName;
        private String personalName;


        private String buyerPhoneNumber;

    }

    @Data
    public static class EditTradeRequest {
        private String goodsType;
        private Integer quantity;
        private Double amount;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate deliveryDate;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime deliveryTime;
    }

    @Data
    public static class UpdateAddressRequest {
        private String deliveryAddress;
    }
}