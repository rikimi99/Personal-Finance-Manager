package com.riki.pfm.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class AuthenticationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void usersCanOnlyAccessTheirOwnTransactions() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());

        CsrfContext firstCsrf = getCsrfContext();
        String firstEmail = "first-" + UUID.randomUUID() + "@example.com";
        register(firstCsrf, firstEmail);
        mockMvc.perform(post("/api/auth/register")
                        .cookie(firstCsrf.cookie())
                        .header("X-XSRF-TOKEN", firstCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(firstEmail)))
                .andExpect(status().isConflict());
        MockHttpSession firstSession = login(firstCsrf, firstEmail);

        mockMvc.perform(post("/api/transactions")
                        .session(firstSession)
                        .cookie(firstCsrf.cookie())
                        .header("X-XSRF-TOKEN", firstCsrf.token())
                        .param("userId", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 25.00,
                                  "type": "EXPENSE",
                                  "description": "First user's transaction",
                                  "transactionDate": "2026-11-19"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transactions")
                        .session(firstSession)
                        .param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description")
                        .value("First user's transaction"));

        mockMvc.perform(post("/api/auth/logout")
                        .session(firstSession)
                        .cookie(firstCsrf.cookie())
                        .header("X-XSRF-TOKEN", firstCsrf.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/transactions").session(firstSession))
                .andExpect(status().isUnauthorized());

        CsrfContext secondCsrf = getCsrfContext();
        String secondEmail = "second-" + UUID.randomUUID() + "@example.com";
        register(secondCsrf, secondEmail);
        MockHttpSession secondSession = login(secondCsrf, secondEmail);

        mockMvc.perform(get("/api/transactions").session(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void registrationRequiresCsrfProtection() throws Exception {
        CsrfContext csrf = getCsrfContext();

        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("csrf-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationRejectsPasswordsThatExceedBcryptByteLimit() throws Exception {
        CsrfContext csrf = getCsrfContext();

        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(
                                "long-password-" + UUID.randomUUID() + "@example.com",
                                "é".repeat(37)
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedRequestBodiesReturnBadRequestWithMessage() throws Exception {
        CsrfContext csrf = getCsrfContext();

        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void errorDispatchIsNotDeniedBySecurity() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/error was blocked with " + status);
                    }
                });
    }

    private CsrfContext getCsrfContext() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher tokenMatcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(response);
        if (!tokenMatcher.find()) {
            throw new IllegalStateException("CSRF response did not contain a token");
        }
        return new CsrfContext(tokenMatcher.group(1),
                result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private void register(CsrfContext csrf, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(CsrfContext csrf, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String credentials(String email) throws Exception {
        return credentials(email, "strong-password-123");
    }

    private String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private record CsrfContext(String token, Cookie cookie) {
    }
}
