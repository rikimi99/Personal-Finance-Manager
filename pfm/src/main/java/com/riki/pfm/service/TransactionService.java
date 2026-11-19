package com.riki.pfm.service;

import com.riki.pfm.dto.FinancialSummaryResponse;
import com.riki.pfm.entity.Transaction;
import com.riki.pfm.enums.TransactionType;
import com.riki.pfm.exception.TransactionNotFoundException;
import com.riki.pfm.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(
            TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction addTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    public Page<Transaction> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable);
    }

    public Transaction getTransactionById(Long id) {
    return transactionRepository.findById(id)
            .orElseThrow(() ->
                    new TransactionNotFoundException(id)
            );
    }

    public Transaction updateTransaction(
        Long id,
        Transaction updatedTransaction) {

    Transaction existing = getTransactionById(id);

    existing.setAmount(updatedTransaction.getAmount());
    existing.setType(updatedTransaction.getType());
    existing.setDescription(updatedTransaction.getDescription());
    existing.setTransactionDate(updatedTransaction.getTransactionDate());

    return transactionRepository.save(existing);
    }

    public void deleteTransaction(Long id) {
    Transaction transaction = getTransactionById(id);
    transactionRepository.delete(transaction);
    }

    public FinancialSummaryResponse getFinancialSummary() {
        BigDecimal totalIncome = getTotalAmountByType(TransactionType.INCOME);
        BigDecimal totalExpenses = getTotalAmountByType(TransactionType.EXPENSE);
        BigDecimal balance = totalIncome.subtract(totalExpenses);

        return new FinancialSummaryResponse(
                totalIncome,
                totalExpenses,
                balance
        );
    }

    private BigDecimal getTotalAmountByType(TransactionType type) {
        BigDecimal total = transactionRepository.sumAmountByType(type);
        return total != null ? total : BigDecimal.ZERO;
    }

}