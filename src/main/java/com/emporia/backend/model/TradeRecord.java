package com.emporia.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "trade_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private SMEProfile seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private SMEProfile buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private SMEProfile driver; // Nullable until Seller assigns them

    // The Goods & Logistics
    @Column(nullable = false)
    private String goodsType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal amount;

    private LocalDate deliveryDate;
    private LocalTime deliveryTime;

    // The Payout Details
    @Column(nullable = false)
    private String sellerAccountNumber;

    @Column(nullable = false)
    private String sellerBankName;

    // The Security Handshake
    @Column(length = 6)
    private String deliveryOtp; // The code the Buyer gives the Driver

    public enum TradeStatus {
        PENDING_BUYER_PAYMENT,
        FUNDED_IN_ESCROW,
        ASSIGNED_TO_DRIVER,
        IN_TRANSIT,
        DELIVERED,
        PAYOUT_COMPLETED
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
