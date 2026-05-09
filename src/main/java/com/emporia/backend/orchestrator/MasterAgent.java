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
     * Called during Login/Signup. Returns the raw string so we can extract the KYC Address.
     */
    public String executeIdentityProtocol(String phoneNumber, String name) {
        log.info("MasterAgent: Triggering Gatekeeper Identity Protocol for {}", phoneNumber);
        String decision = gatekeeperAgent.evaluateIdentity(phoneNumber, name);
        log.info("Gatekeeper Response: {}", decision);
        return decision;
    }

    /**
     * Called to verify if a driver is actually at the destination coordinates.
     */
    public boolean executeLogisticsProtocol(String driverPhone, double buyerLat, double buyerLon) {
        log.info("MasterAgent: Triggering Navigator Logistics Protocol for Driver {}", driverPhone);
        String decision = navigatorAgent.verifyDeliveryLocation(driverPhone, buyerLat, buyerLon);
        log.info("Navigator Response: {}", decision);
        return decision.contains("ARRIVED");
    }

    /**
     * Called before Login or Escrow Payout to ensure the phone hasn't been hijacked.
     */
    public boolean executeFraudPreventionProtocol(String phone) {
        log.info("MasterAgent: Triggering Sentinel Fraud Protocol for {}", phone);
        String decision = sentinelAgent.checkSimSwapRisk(phone);
        log.info("Sentinel Response: {}", decision);
        return decision.contains("SAFE");
    }
}