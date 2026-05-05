package com.emporia.backend.repository;

import com.emporia.backend.model.BankAccount;
import com.emporia.backend.model.SMEProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    Optional<BankAccount> findBySeller(SMEProfile seller);
}