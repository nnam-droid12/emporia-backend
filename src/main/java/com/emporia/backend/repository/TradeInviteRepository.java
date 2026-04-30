package com.emporia.backend.repository;

import com.emporia.backend.model.TradeInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TradeInviteRepository extends JpaRepository<TradeInvite, UUID> {

    Optional<TradeInvite> findByInviteCodeAndIsUsedFalse(String inviteCode);
}