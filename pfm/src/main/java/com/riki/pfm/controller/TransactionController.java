package com.riki.pfm.controller;

import com.riki.pfm.dto.FinancialSummaryResponse;
import com.riki.pfm.dto.TransactionRequest;
import com.riki.pfm.dto.TransactionResponse;
import com.riki.pfm.entity.Transaction;
import com.riki.pfm.service.TransactionService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> addTransaction(
            @Valid @RequestBody TransactionRequest request) {

        Transaction transaction = new Transaction(
                request.amount(),
                request.type(),
                request.description(),
                request.transactionDate()
        );

        Transaction saved = transactionService.addTransaction(transaction);

        TransactionResponse response = toResponse(saved);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public Page<TransactionResponse> getAllTransactions(
            @PageableDefault(
                    size = 20,
                    sort = "transactionDate",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {
        return transactionService.getAllTransactions(pageable)
                .map(this::toResponse);
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getDescription(),
                transaction.getTransactionDate()
        );
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransactionById(
            @PathVariable Long id) {

        Transaction transaction =
                transactionService.getTransactionById(id);

        return toResponse(transaction);
    }

    @PutMapping("/{id}")
    public TransactionResponse updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request) {

        Transaction updatedTransaction = new Transaction(
                request.amount(),
                request.type(),
                request.description(),
                request.transactionDate()
        );

        Transaction saved = transactionService.updateTransaction(
                id, updatedTransaction
        );

        return toResponse(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long id) {

        transactionService.deleteTransaction(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public FinancialSummaryResponse getFinancialSummary() {
        return transactionService.getFinancialSummary();
    }
}