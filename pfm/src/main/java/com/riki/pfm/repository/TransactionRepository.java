package com.riki.pfm.repository;

import com.riki.pfm.entity.Transaction;
import com.riki.pfm.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

import java.math.BigDecimal;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.type = :type
        AND t.user.id = :userId
    """)
    BigDecimal sumAmountByType(
        @Param("type") TransactionType type,
        @Param("userId") Long userId
    );

    List<Transaction> findByUserId(Long userId);

    Page<Transaction> findByUserId(Long userId, Pageable pageable);

    Optional<Transaction> findByIdAndUserId(
        Long transactionId,
        Long userId
    );
}