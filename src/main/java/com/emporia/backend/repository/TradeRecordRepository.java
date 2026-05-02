package com.emporia.backend.repository;

import com.emporia.backend.model.SMEProfile;
import com.emporia.backend.model.TradeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {
    List<TradeRecord> findBySeller(SMEProfile seller);
}