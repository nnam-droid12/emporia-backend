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
    private LocalDate deliveryDate;
    private LocalTime deliveryTime;

    // Seller's Payout Account
    private String accountNumber;
    private String accountName;
    private String bankName;

    // Map Coordinates
    private String deliveryAddress;
    private Double latitude;
    private Double longitude;

    // Escrow Payment Tracking
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    public enum PaymentStatus {
        PENDING,
        ONE_THIRD_FUNDED,
        HALF_FUNDED,
        FULLY_FUNDED,
        RELEASED
    }

    // Logistics Tracking
    @Enumerated(EnumType.STRING)
    private TradeStatus tradeStatus;

    public enum TradeStatus {
        CREATED,
        BUYER_JOINED,
        DRIVER_ASSIGNED,
        IN_TRANSIT,
        DELIVERED
    }

    @PrePersist
    public void prePersist() {
        if(this.paymentStatus == null) this.paymentStatus = PaymentStatus.PENDING;
        if(this.tradeStatus == null) this.tradeStatus = TradeStatus.CREATED;
    }
}