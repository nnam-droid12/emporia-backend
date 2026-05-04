package com.emporia.backend.repository;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.model.TradeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {
    List<TradeRecord> findBySeller(SMEProfile seller);
    List<TradeRecord> findByBuyer(SMEProfile buyer);
    Optional<TradeRecord> findByTradeId(String tradeId);
}