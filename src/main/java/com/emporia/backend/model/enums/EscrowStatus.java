package com.emporia.backend.model.enums;

public enum EscrowStatus {
    PENDING_DEPOSIT, // Contract created, waiting for Paystack payment
    FUNDED,          // Money is locked in Emporia's account
    IN_TRANSIT,      // Seller shipped, Navigator Agent is tracking
    ARRIVED,         // Driver entered the geofence
    COMPLETED,       // Sentinel Agent approved, money sent to Seller
    FROZEN,          // Sentinel Agent detected SIM Swap / Fraud
    CANCELLED        // Trade aborted before funding
}