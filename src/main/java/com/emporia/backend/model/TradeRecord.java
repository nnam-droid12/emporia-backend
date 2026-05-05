package com.emporia.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "trade_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String tradeId;

    @ManyToOne
    @JoinColumn(name = "seller_id", nullable = false)
    private SMEProfile seller;

    @ManyToOne
    @JoinColumn(name = "buyer_id")
    private SMEProfile buyer;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private SMEProfile driver;

    // Trade Details
    private String goodsType;
    private Integer quantity;
    private Double amount;

    @Column(nullable = false, columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double amountReleased = 0.0;

    private LocalDate deliveryDate;
    private LocalTime deliveryTime;
    private String deliveryCode;

    // Map Coordinates
    private String deliveryAddress;
    private Double latitude;
    private Double longitude;


    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    public enum PaymentStatus {
        PENDING,          // Buyer hasn't paid yet
        ESCROW_FUNDED,    // Buyer paid 100% to Paystack (Money is safe)
        RELEASE_BATCH_1,  // 1/3 of total released to Seller
        RELEASE_BATCH_2,  // 1/2 of total released to Seller
        FULLY_RELEASED    // Remaining balance released to Seller
    }

    // Logistics Tracking
    @Enumerated(EnumType.STRING)
    private TradeStatus tradeStatus;

    // Dispute Tracking
    private String flagReason;

    public enum TradeStatus {
        CREATED,
        BUYER_JOINED,
        DRIVER_ASSIGNED,
        ACTIVE,
        IN_TRANSIT,
        DELIVERED,
        FLAGGED
    }

    @PrePersist
    public void prePersist() {
        if(this.paymentStatus == null) this.paymentStatus = PaymentStatus.PENDING;
        if(this.tradeStatus == null) this.tradeStatus = TradeStatus.CREATED;

        if(this.amountReleased == null) this.amountReleased = 0.0;
    }
}