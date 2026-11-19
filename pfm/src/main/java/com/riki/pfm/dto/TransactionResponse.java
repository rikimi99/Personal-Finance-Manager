package com.riki.pfm.dto;

import com.riki.pfm.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
    Long id,
    BigDecimal amount,
    TransactionType type,
    String description,
    LocalDate transactionDate
) {}