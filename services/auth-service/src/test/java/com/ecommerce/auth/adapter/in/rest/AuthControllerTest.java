package com.ecommerce.auth.adapter.in.rest;

import com.ecommerce.auth.application.DuplicateEmailException;
import com.ecommerce.auth.application.InvalidCredentialsException;
import com.ecommerce.auth.application.port.TokenIssuer.IssuedToken;
import com.ecommerce.auth.application.service.LoginUseCase;
import com.ecommerce.auth.application.service.RegisterUserUseCase;
import com.ecommerce.auth.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegisterUserUseCase registerUser;
    @MockitoBean
    private LoginUseCase login;

    @Test
    void registerReturns201WithUserAndNoPasswordEcho() throws Exception {
        when(registerUser.handle(any())).thenReturn(user());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"anu@example.com","password":"s3cret-password","displayName":"Anu"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", notNullValue()))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.email").value("anu@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shortPasswordFailsValidation() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"anu@example.com","password":"short","displayName":"Anu"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value(notNullValue()));
    }

    @Test
    void duplicateEmailMapsTo409() throws Exception {
        when(registerUser.handle(any())).thenThrow(new DuplicateEmailException());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"anu@example.com","password":"s3cret-password","displayName":"Anu"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsTokenEnvelope() throws Exception {
        when(login.handle(any())).thenReturn(new LoginUseCase.AuthenticatedUser(
                user(), new IssuedToken("the-jwt", NOW.plusSeconds(1800))));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"anu@example.com","password":"s3cret-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("the-jwt"))
                .andExpect(jsonPath("$.expiresAt").value("2026-07-19T10:30:00Z"))
                .andExpect(jsonPath("$.user.userId").value("user-1"));
    }

    @Test
    void badCredentialsMapTo401() throws Exception {
        when(login.handle(any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"anu@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private User user() {
        return User.register("user-1", "anu@example.com", "hash", "Anu", NOW);
    }
}
