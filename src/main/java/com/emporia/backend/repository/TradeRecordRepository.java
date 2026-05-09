package com.emporia.backend.repository;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.model.TradeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {
    List<TradeRecord> findBySeller(SMEProfile seller);
    List<TradeRecord> findByBuyer(SMEProfile buyer);
    Optional<TradeRecord> findByTradeId(String tradeId);


    @Query("SELECT DISTINCT t.buyer FROM TradeRecord t WHERE t.seller = :seller AND t.buyer IS NOT NULL")
    List<SMEProfile> findDistinctBuyersBySeller(@Param("seller") SMEProfile seller);

    @Query("SELECT DISTINCT t.driver FROM TradeRecord t WHERE t.seller = :seller AND t.driver IS NOT NULL")
    List<SMEProfile> findDistinctDriversBySeller(@Param("seller") SMEProfile seller);

    List<TradeRecord> findByDriver(SMEProfile driver);


}