package com.riki.pfm.service;

import com.riki.pfm.dto.FinancialSummaryResponse;
import com.riki.pfm.entity.Transaction;
import com.riki.pfm.enums.TransactionType;
import com.riki.pfm.exception.TransactionNotFoundException;
import com.riki.pfm.repository.TransactionRepository;
import com.riki.pfm.repository.UserRepository;
import com.riki.pfm.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void addTransaction_savesAndReturnsTransaction() {
        Transaction transaction = transaction("45.00", TransactionType.EXPENSE);
        User user = new User("demo@ledger.local", "not-used");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        Transaction result = transactionService.addTransaction(transaction, 1L);

        assertSame(transaction, result);
        assertSame(user, transaction.getUser());
        verify(transactionRepository).save(transaction);
    }

    @Test
    void getAllTransactions_returnsRequestedPageFromRepository() {
        Pageable pageable = PageRequest.of(
                1,
                2,
                Sort.by(Sort.Direction.DESC, "transactionDate")
        );
        List<Transaction> transactions = List.of(
                transaction("45.00", TransactionType.EXPENSE),
                transaction("100.00", TransactionType.INCOME)
        );
        Page<Transaction> expected = new PageImpl<>(transactions, pageable, 5);
        when(transactionRepository.findByUserId(1L, pageable)).thenReturn(expected);

        Page<Transaction> result = transactionService.getAllTransactions(pageable, 1L);

        assertEquals(transactions, result.getContent());
        assertEquals(1, result.getNumber());
        assertEquals(2, result.getSize());
        assertEquals(5, result.getTotalElements());
        verify(transactionRepository).findByUserId(1L, pageable);
    }

    @Test
    void getTransactionById_returnsTransactionWhenFound() {
        Transaction expected = transaction("45.00", TransactionType.EXPENSE);
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(expected));

        Transaction actual = transactionService.getTransactionById(1L, 1L);

        assertSame(expected, actual);
    }

    @Test
    void getTransactionById_throwsWhenNotFound() {
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        assertThrows(
                TransactionNotFoundException.class,
                () -> transactionService.getTransactionById(1L, 1L)
        );
    }

    @Test
    void updateTransaction_copiesValuesOntoExistingTransactionAndSavesIt() {
        Transaction existing = transaction("45.00", TransactionType.EXPENSE);
        Transaction updates = new Transaction(
                new BigDecimal("125.50"),
                TransactionType.INCOME,
                "Updated description",
                LocalDate.of(2026, 2, 15)
        );
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.save(existing)).thenReturn(existing);

        Transaction result = transactionService.updateTransaction(1L, updates, 1L);

        assertSame(existing, result);
        assertEquals(new BigDecimal("125.50"), existing.getAmount());
        assertEquals(TransactionType.INCOME, existing.getType());
        assertEquals("Updated description", existing.getDescription());
        assertEquals(LocalDate.of(2026, 2, 15), existing.getTransactionDate());
        verify(transactionRepository).save(existing);
    }

    @Test
    void deleteTransaction_deletesTransactionWhenFound() {
        Transaction existing = transaction("45.00", TransactionType.EXPENSE);
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));

        transactionService.deleteTransaction(1L, 1L);

        verify(transactionRepository).delete(existing);
    }

    @Test
    void deleteTransaction_throwsWhenNotFound() {
        when(transactionRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        assertThrows(
                TransactionNotFoundException.class,
                () -> transactionService.deleteTransaction(1L, 1L)
        );
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    @Test
    void getFinancialSummary_returnsZerosWhenThereAreNoTransactions() {
        when(transactionRepository.sumAmountByType(TransactionType.INCOME, 1L)).thenReturn(null);
        when(transactionRepository.sumAmountByType(TransactionType.EXPENSE, 1L)).thenReturn(null);

        FinancialSummaryResponse summary = transactionService.getFinancialSummary(1L);

        assertEquals(new BigDecimal("0.00"), summary.totalIncome());
        assertEquals(new BigDecimal("0.00"), summary.totalExpenses());
        assertEquals(new BigDecimal("0.00"), summary.balance());
    }

    @Test
    void getFinancialSummary_sumsIncomeTransactions() {
        when(transactionRepository.sumAmountByType(TransactionType.INCOME, 1L))
                .thenReturn(new BigDecimal("125.50"));
        when(transactionRepository.sumAmountByType(TransactionType.EXPENSE, 1L))
                .thenReturn(null);

        FinancialSummaryResponse summary = transactionService.getFinancialSummary(1L);

        assertEquals(new BigDecimal("125.50"), summary.totalIncome());
        assertEquals(new BigDecimal("0.00"), summary.totalExpenses());
        assertEquals(new BigDecimal("125.50"), summary.balance());
    }

    @Test
    void getFinancialSummary_sumsExpenseTransactions() {
        when(transactionRepository.sumAmountByType(TransactionType.INCOME, 1L))
                .thenReturn(null);
        when(transactionRepository.sumAmountByType(TransactionType.EXPENSE, 1L))
                .thenReturn(new BigDecimal("42.50"));

        FinancialSummaryResponse summary = transactionService.getFinancialSummary(1L);

        assertEquals(new BigDecimal("0.00"), summary.totalIncome());
        assertEquals(new BigDecimal("42.50"), summary.totalExpenses());
        assertEquals(new BigDecimal("-42.50"), summary.balance());
    }

    @Test
    void getFinancialSummary_subtractsExpensesFromIncome() {
        when(transactionRepository.sumAmountByType(TransactionType.INCOME, 1L))
                .thenReturn(new BigDecimal("100.00"));
        when(transactionRepository.sumAmountByType(TransactionType.EXPENSE, 1L))
                .thenReturn(new BigDecimal("30.00"));

        FinancialSummaryResponse summary = transactionService.getFinancialSummary(1L);

        assertEquals(new BigDecimal("100.00"), summary.totalIncome());
        assertEquals(new BigDecimal("30.00"), summary.totalExpenses());
        assertEquals(new BigDecimal("70.00"), summary.balance());
    }

    @Test
    void getFinancialSummary_roundsFloatingPointSumsToCents() {
        when(transactionRepository.sumAmountByType(TransactionType.INCOME, 1L))
                .thenReturn(new BigDecimal("0.30000000000000004"));
        when(transactionRepository.sumAmountByType(TransactionType.EXPENSE, 1L))
                .thenReturn(new BigDecimal("0.1"));

        FinancialSummaryResponse summary = transactionService.getFinancialSummary(1L);

        assertEquals(new BigDecimal("0.30"), summary.totalIncome());
        assertEquals(new BigDecimal("0.10"), summary.totalExpenses());
        assertEquals(new BigDecimal("0.20"), summary.balance());
    }

    private Transaction transaction(String amount, TransactionType type) {
        return new Transaction(
                new BigDecimal(amount),
                type,
                "Test transaction",
                LocalDate.of(2026, 1, 1)
        );
    }
}
