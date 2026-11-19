package com.riki.pfm.controller;

import com.riki.pfm.entity.Transaction;
import com.riki.pfm.enums.TransactionType;
import com.riki.pfm.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.hamcrest.Matchers.hasSize;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new TransactionController(transactionService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getAllTransactions_acceptsPageSizeAndSortParameters() throws Exception {
        Pageable pageable = PageRequest.of(
                1,
                2,
                Sort.by(Sort.Direction.DESC, "transactionDate")
        );
        Transaction transaction = new Transaction(
                new BigDecimal("45.00"),
                TransactionType.EXPENSE,
                "Test transaction",
                LocalDate.of(2026, 1, 1)
        );
        Page<Transaction> page = new PageImpl<>(List.of(transaction), pageable, 5);
        when(transactionService.getAllTransactions(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/transactions")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "transactionDate,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService).getAllTransactions(pageableCaptor.capture());
        assertEquals(pageable, pageableCaptor.getValue());
    }
}
