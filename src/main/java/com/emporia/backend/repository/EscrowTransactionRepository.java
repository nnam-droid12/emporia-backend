package com.emporia.backend.repository;

import com.emporia.backend.model.EscrowTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface EscrowTransactionRepository extends JpaRepository<EscrowTransaction, UUID> {

    List<EscrowTransaction> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<EscrowTransaction> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);
}
