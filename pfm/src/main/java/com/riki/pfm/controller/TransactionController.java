package com.riki.pfm.controller;

import com.riki.pfm.dto.FinancialSummaryResponse;
import com.riki.pfm.dto.TransactionRequest;
import com.riki.pfm.dto.TransactionResponse;
import com.riki.pfm.entity.Transaction;
import com.riki.pfm.security.AppUserPrincipal;
import com.riki.pfm.service.TransactionService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final Set<String> SORTABLE_PROPERTIES =
            Set.of("transactionDate", "amount", "type", "description", "id");

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> addTransaction(
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        Transaction transaction = new Transaction(
                request.amount(),
                request.type(),
                request.description(),
                request.transactionDate()
        );

        Transaction saved = transactionService.addTransaction(transaction, principal.id());

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
            ) Pageable pageable,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return transactionService.getAllTransactions(withStableSort(pageable), principal.id())
                .map(this::toResponse);
    }

    /**
     * Rejects sort properties outside the API (e.g. "user.passwordHash" or typos that
     * would otherwise fail inside JPA) and appends an id tie-breaker so rows with equal
     * dates/amounts are not duplicated or skipped across pages.
     */
    private Pageable withStableSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported sort property: " + order.getProperty()
                );
            }
        }
        if (pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        Sort sort = pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                toMoney(transaction.getAmount()),
                transaction.getType(),
                transaction.getDescription(),
                transaction.getTransactionDate()
        );
    }

    // SQLite stores NUMERIC values as REAL, so normalise to two decimal places.
    private static BigDecimal toMoney(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransactionById(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        Transaction transaction =
                transactionService.getTransactionById(id, principal.id());

        return toResponse(transaction);
    }

    @PutMapping("/{id}")
    public TransactionResponse updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        Transaction updatedTransaction = new Transaction(
                request.amount(),
                request.type(),
                request.description(),
                request.transactionDate()
        );

        Transaction saved = transactionService.updateTransaction(
                id, updatedTransaction, principal.id()
        );

        return toResponse(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        transactionService.deleteTransaction(id, principal.id());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public FinancialSummaryResponse getFinancialSummary(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return transactionService.getFinancialSummary(principal.id());
    }
}