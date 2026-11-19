package com.riki.pfm.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.riki.pfm.enums.TransactionType;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(length = 255)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    protected Transaction() {
    }

    public Transaction(
    BigDecimal amount,
    TransactionType type,
    String description,
    LocalDate transactionDate
) {
    this.amount = amount;
    this.type = type;
    this.description = description;
    this.transactionDate = transactionDate;
}

    public Long getId() {
    return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
    return type;
}


    public String getDescription() {
        return description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setType(TransactionType type) {
    this.type = type;
}

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

}
