
package com.riki.pfm.service;

import com.riki.pfm.dto.FinancialSummaryResponse;
import com.riki.pfm.entity.Transaction;
import com.riki.pfm.entity.User;
import com.riki.pfm.enums.TransactionType;
import com.riki.pfm.exception.TransactionNotFoundException;
import com.riki.pfm.repository.TransactionRepository;
import com.riki.pfm.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            UserRepository userRepository) {

        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Transaction addTransaction(
            Transaction transaction,
            Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found")
                );

        transaction.setId(null);
        transaction.setUser(user);

        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getAllTransactions(
            Pageable pageable,
            Long userId) {

        return transactionRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Transaction getTransactionById(
            Long id,
            Long userId) {

        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() ->
                        new TransactionNotFoundException(id)
                );
    }

    @Transactional
    public Transaction updateTransaction(
            Long id,
            Transaction updatedTransaction,
            Long userId) {

        Transaction existing = getTransactionById(id, userId);

        existing.setAmount(updatedTransaction.getAmount());
        existing.setType(updatedTransaction.getType());
        existing.setDescription(updatedTransaction.getDescription());
        existing.setTransactionDate(updatedTransaction.getTransactionDate());

        return transactionRepository.save(existing);
    }

    @Transactional
    public void deleteTransaction(Long id, Long userId) {

        Transaction transaction = getTransactionById(id, userId);

        transactionRepository.delete(transaction);
    }

    @Transactional(readOnly = true)
    public FinancialSummaryResponse getFinancialSummary(Long userId) {

        BigDecimal totalIncome =
                getTotalAmountByType(TransactionType.INCOME, userId);

        BigDecimal totalExpenses =
                getTotalAmountByType(TransactionType.EXPENSE, userId);

        BigDecimal balance = totalIncome.subtract(totalExpenses);

        return new FinancialSummaryResponse(
                totalIncome,
                totalExpenses,
                balance
        );
    }

    private BigDecimal getTotalAmountByType(
            TransactionType type,
            Long userId) {

        BigDecimal total =
                transactionRepository.sumAmountByType(type, userId);

        // SQLite sums NUMERIC columns as REAL (e.g. 0.1 + 0.2 = 0.30000000000000004),
        // so round the aggregate back to cents.
        return (total != null ? total : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
