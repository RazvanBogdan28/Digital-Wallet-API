package com.razvan.digital_wallet_api.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(
            UserAlreadyExistsException ex
    ) {
        return error(
                HttpStatus.CONFLICT,
                "USER_ALREADY_EXISTS",
                ex.getMessage()
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(
            UserNotFoundException ex
    ) {
        return error(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                ex.getMessage()
        );
    }

    @ExceptionHandler(WalletAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleWalletAlreadyExists(
            WalletAlreadyExistsException ex
    ) {
        return error(
                HttpStatus.CONFLICT,
                "WALLET_ALREADY_EXISTS",
                ex.getMessage()
        );
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWalletNotFound(
            WalletNotFoundException ex
    ) {
        return error(
                HttpStatus.NOT_FOUND,
                "WALLET_NOT_FOUND",
                ex.getMessage()
        );
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientFunds(
            InsufficientFundsException ex
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INSUFFICIENT_FUNDS",
                ex.getMessage()
        );
    }

    @ExceptionHandler(SameWalletTransferException.class)
    public ResponseEntity<Map<String, Object>> handleSameWalletTransfer(
            SameWalletTransferException ex
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "SAME_WALLET_TRANSFER",
                ex.getMessage()
        );
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleCurrencyMismatch(
            CurrencyMismatchException ex
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "CURRENCY_MISMATCH",
                ex.getMessage()
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex
    ) {
        return error(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "The wallet was modified by another request. Please try again."
        );
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateTransaction(
            DuplicateTransactionException ex
    ) {
        return error(
                HttpStatus.CONFLICT,
                "DUPLICATE_TRANSACTION",
                ex.getMessage()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(
            HttpMessageNotReadableException ex
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Invalid request body. Check the JSON syntax and field values."
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex
    ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Validation failed");

        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
            InvalidCredentialsException ex
    ) {
        return error(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                ex.getMessage()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex
    ) {
        return error(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                ex.getMessage()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex
    ) {
        ConstraintViolationException violation =
                findConstraintViolation(ex);

        if (violation != null
                && "23505".equals(violation.getSQLState())) {

            String constraint = violation.getConstraintName();

            if ("uk_users_email_normalized".equals(constraint)
                    || "users_email_key".equals(constraint)) {
                return error(
                        HttpStatus.CONFLICT,
                        "USER_ALREADY_EXISTS",
                        "A user with this email already exists"
                );
            }

            if ("uk_wallet_user_currency".equals(constraint)) {
                return error(
                        HttpStatus.CONFLICT,
                        "WALLET_ALREADY_EXISTS",
                        "User already has a wallet in this currency"
                );
            }

            if ("transactions_idempotency_key_key".equals(constraint)) {
                return error(
                        HttpStatus.CONFLICT,
                        "DATA_INTEGRITY_CONFLICT",
                        "Another request used this Idempotency-Key. "
                                + "Retry the same operation with the same key "
                                + "to check its result."
                );
            }
        }

        return error(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_CONFLICT",
                "The request conflicts with stored data."
        );
    }

    private ConstraintViolationException findConstraintViolation(
            Throwable exception
    ) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation;
            }

            Throwable next = current.getCause();

            if (next == current) {
                break;
            }

            current = next;
        }

        return null;
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String code,
            String message
    ) {
        Map<String, Object> body = new HashMap<>();

        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", code);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);
    }
}