package com.razvan.digital_wallet_api.controller;

import com.razvan.digital_wallet_api.dto.CreateWalletRequest;
import com.razvan.digital_wallet_api.dto.DepositRequest;
import com.razvan.digital_wallet_api.dto.TransferRequest;
import com.razvan.digital_wallet_api.dto.WalletResponse;
import com.razvan.digital_wallet_api.service.WalletService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallets")
@Tag(
        name = "Wallets",
        description = "Wallet management, balance operations and transfers"
)
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @Operation(
            summary = "Create wallet",
            description = "Creates a new wallet for a user in a specific currency."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Wallet created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Wallet already exists for this user and currency"
            )
    })
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request
    ) {

        WalletResponse createdWallet = walletService.createWallet(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdWallet);
    }

    @GetMapping("/user/{userId}")
    @Operation(
            summary = "Get wallets by user",
            description = "Returns all wallets that belong to the specified user."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet list returned successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied"
            )
    })
    public ResponseEntity<List<WalletResponse>> getWalletsByUserId(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId
    ) {

        return ResponseEntity.ok(
                walletService.getWalletsByUserId(userId)
        );
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get wallet by id",
            description = "Returns wallet details for the specified wallet ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Wallet returned successfully"
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
    public ResponseEntity<WalletResponse> getWalletById(
            @Parameter(description = "Wallet ID", required = true)
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                walletService.getWalletById(id)
        );
    }

    @PostMapping("/{id}/deposit")
    @Operation(
            summary = "Deposit money",
            description = "Adds money to the specified wallet."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Deposit completed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request"
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
    public ResponseEntity<WalletResponse> deposit(
            @Parameter(description = "Wallet ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request
    ) {

        return ResponseEntity.ok(
                walletService.deposit(id, request)
        );
    }

    @PostMapping("/{id}/transfer")
    @Operation(
            summary = "Transfer money",
            description = "Transfers money from one wallet to another. Requires the Idempotency-Key header to prevent duplicate transfers."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transfer completed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request, insufficient funds, same wallet transfer or currency mismatch"
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
                    description = "Source or destination wallet not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Duplicate transaction or concurrent modification"
            )
    })
    public ResponseEntity<WalletResponse> transfer(
            @Parameter(description = "Source wallet ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody TransferRequest request,
            @Parameter(
                    description = "Unique idempotency key used to prevent duplicate transfers",
                    required = true
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {

        return ResponseEntity.ok(
                walletService.transfer(id, request, idempotencyKey)
        );
    }
}