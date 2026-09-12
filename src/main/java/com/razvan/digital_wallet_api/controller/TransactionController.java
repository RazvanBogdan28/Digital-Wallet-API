package com.razvan.digital_wallet_api.controller;

import com.razvan.digital_wallet_api.dto.TransactionResponse;
import com.razvan.digital_wallet_api.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@Tag(
        name = "Transactions",
        description = "Transaction history and wallet transaction queries"
)
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService
    ) {
        this.transactionService = transactionService;
    }

    @GetMapping("/wallet/{walletId}")
    @Operation(
            summary = "Get wallet transaction history",
            description = "Returns a paginated list of transactions associated with the specified wallet."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction history returned successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid page or size parameter"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Wallet not found"
            )
    })
    public ResponseEntity<Page<TransactionResponse>>
    getTransactionsByWalletId(

            @Parameter(
                    description = "Wallet ID",
                    required = true
            )
            @PathVariable Long walletId,

            @Parameter(
                    description = "Page number. Starts from 0.",
                    example = "0"
            )
            @RequestParam(defaultValue = "0") int page,

            @Parameter(
                    description = "Number of transactions per page. Must be between 1 and 100.",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") int size
    ) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Size must be between 1 and 100"
            );
        }

        return ResponseEntity.ok(
                transactionService.getTransactionsByWalletId(
                        walletId,
                        page,
                        size
                )
        );
    }
}