package com.emporia.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sme_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SMEProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String businessName;



    @Column(nullable = false)
    private boolean kycVerified;

    // To store the Paystack customer code for easy payouts
    private String paystackCustomerCode;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Automatically set timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    public enum Role {
        BUYER, SELLER, DRIVER
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
}
