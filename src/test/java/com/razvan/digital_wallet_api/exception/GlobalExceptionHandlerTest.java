package com.razvan.digital_wallet_api.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    private DataIntegrityViolationException uniqueViolation(
            String constraint
    ) {
        SQLException sqlException = new SQLException(
                "Duplicate value",
                "23505"
        );

        ConstraintViolationException violation =
                new ConstraintViolationException(
                        "Constraint violated",
                        sqlException,
                        constraint
                );

        return new DataIntegrityViolationException(
                "Could not save",
                violation
        );
    }

    @Test
    void duplicateEmailShouldReturnUserAlreadyExists() {
        for (String constraint : new String[]{
                "users_email_key",
                "uk_users_email_normalized"
        }) {
            var response = handler.handleDataIntegrityViolation(
                    uniqueViolation(constraint)
            );

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

            var body = response.getBody();
            assertNotNull(body);
            assertEquals("USER_ALREADY_EXISTS", body.get("error"));
            assertEquals(
                    "A user with this email already exists",
                    body.get("message")
            );
        }
    }

    @Test
    void duplicateWalletShouldReturnWalletAlreadyExists() {
        var response = handler.handleDataIntegrityViolation(
                uniqueViolation("uk_wallet_user_currency")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        var body = response.getBody();
        assertNotNull(body);
        assertEquals("WALLET_ALREADY_EXISTS", body.get("error"));
    }

    @Test
    void duplicateKeyShouldRequestRetryWithoutClaimingSuccess() {
        var response = handler.handleDataIntegrityViolation(
                uniqueViolation("transactions_idempotency_key_key")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        var body = response.getBody();
        assertNotNull(body);
        assertEquals("DATA_INTEGRITY_CONFLICT", body.get("error"));
        assertTrue(
                body.get("message").toString()
                        .contains("Retry the same operation with the same key")
        );
    }

    @Test
    void unknownConflictShouldNotMentionIdempotencyKey() {
        var response = handler.handleDataIntegrityViolation(
                uniqueViolation("another_constraint")
        );

        var body = response.getBody();
        assertNotNull(body);
        assertEquals(
                "The request conflicts with stored data.",
                body.get("message")
        );
        assertFalse(
                body.get("message").toString().contains("Idempotency-Key")
        );
    }

    @Test
    void nonUniqueViolationShouldNotBeReportedAsDuplicateEmail() {
        SQLException sqlException =
                new SQLException("Invalid value", "23502");

        var violation = new ConstraintViolationException(
                "Constraint violated",
                sqlException,
                "users_email_key"
        );

        var response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException(
                        "Could not save",
                        violation
                )
        );

        var body = response.getBody();
        assertNotNull(body);
        assertEquals("DATA_INTEGRITY_CONFLICT", body.get("error"));
    }
}