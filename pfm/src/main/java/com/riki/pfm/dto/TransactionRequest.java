package com.riki.pfm.dto;

import com.riki.pfm.enums.TransactionType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 17, fraction = 2)
    BigDecimal amount,

    @NotNull
    TransactionType type,

    @Size(max = 255)
    String description,

    @NotNull
    LocalDate transactionDate

) {}