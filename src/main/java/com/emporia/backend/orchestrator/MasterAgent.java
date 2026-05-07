package com.emporia.backend.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MasterAgent {

    private final GatekeeperAgent gatekeeperAgent;
    private final NavigatorAgent navigatorAgent;
    private final SentinelAgent sentinelAgent;

    /**
     * This is called when a new user signs up or a trade is created.
     */
    public boolean executeIdentityProtocol(String phoneNumber, String name) {
        log.info("MasterAgent: Triggering Gatekeeper Identity Protocol for {}", phoneNumber);
        String decision = gatekeeperAgent.evaluateIdentity(phoneNumber, name);

        log.info("Gatekeeper Response: {}", decision);
        return decision.contains("APPROVED");
    }

    /**
     * This is called from a cron job or when a Buyer clicks "Check Delivery Status"
     */
    public boolean executeLogisticsProtocol(String driverPhone, double buyerLat, double buyerLon) {
        log.info("MasterAgent: Triggering Navigator Logistics Protocol for Driver {}", driverPhone);
        String decision = navigatorAgent.verifyDeliveryLocation(driverPhone, buyerLat, buyerLon);

        log.info("Navigator Response: {}", decision);
        return decision.contains("ARRIVED");
    }

    /**
     * This is called EXACTLY before Paystack transfer is initiated in the PaymentController
     */
    public boolean executeFraudPreventionProtocol(String sellerPhone) {
        log.info("MasterAgent: Triggering Sentinel Fraud Protocol for Seller {}", sellerPhone);
        String decision = sentinelAgent.checkSimSwapRisk(sellerPhone);

        log.info("Sentinel Response: {}", decision);

        // Return true if it is safe to pay out
        return decision.contains("SAFE");
    }
}