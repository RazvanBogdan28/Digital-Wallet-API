package com.razvan.digital_wallet_api.controller;

import com.razvan.digital_wallet_api.dto.TransactionResponse;
import com.razvan.digital_wallet_api.dto.WalletTransactionWindowResponse;
import com.razvan.digital_wallet_api.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@Tag(
        name = "Transactions",
        description = "Transaction history and wallet transaction queries"
)
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/wallet/{walletId}")
    @Operation(
            summary = "Get wallet transaction history",
            description = """
                    Returns a paginated list of wallet transactions.
                    Ordered by createdAt descending, then id descending.
                    Each request reads a consistent view of the database;
                    separate page requests may include newly committed transactions.
                    """
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
            @Parameter(description = "Wallet ID", required = true)
            @PathVariable Long walletId,

            @Parameter(
                    description = "Page number. Starts from 0.",
                    example = "0"
            )
            @RequestParam(defaultValue = "0") int page,

            @Parameter(
                    description = "Page size, between 1 and 100.",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                transactionService.getTransactionsByWalletId(
                        walletId,
                        page,
                        size
                )
        );
    }

    @GetMapping("/wallet/{walletId}/window")
    @Operation(
            summary = "Get wallet balance and recent transactions",
            description = """
                    Returns the wallet balance and its latest transactions
                    from the same database snapshot.
                    Transactions are ordered by createdAt descending,
                    then id descending.
                    complete is true when all wallet transactions are included.
                    snapshotAt is the UTC time when the request began reading.
                    Monetary values are serialized as decimal strings.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet and recent transactions returned successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid size parameter"
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
    public ResponseEntity<WalletTransactionWindowResponse> getRecentWindow(
            @Parameter(description = "Wallet ID", required = true)
            @PathVariable Long walletId,

            @Parameter(
                    description = "Maximum transactions to include, between 1 and 100.",
                    example = "100"
            )
            @RequestParam(defaultValue = "100") int size
    ) {
        return ResponseEntity.ok(
                transactionService.getRecentWindow(walletId, size)
        );
    }
}