package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.LoginRequest;
import com.razvan.digital_wallet_api.dto.LoginResponse;
import com.razvan.digital_wallet_api.dto.RefreshTokenRequest;
import com.razvan.digital_wallet_api.dto.RefreshTokenResponse;
import com.razvan.digital_wallet_api.entity.RefreshToken;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.exception.InvalidCredentialsException;
import com.razvan.digital_wallet_api.repository.RefreshTokenRepository;
import com.razvan.digital_wallet_api.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid email or password"
                        )
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        String accessToken =
                jwtService.generateAccessToken(user.getEmail());

        String refreshToken =
                jwtService.generateRefreshToken(user.getEmail());

        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                jwtService.extractExpiration(refreshToken).toInstant(),
                ZoneOffset.UTC
        );

        RefreshToken storedRefreshToken = new RefreshToken(
                refreshToken,
                expiresAt,
                false,
                user
        );

        refreshTokenRepository.save(storedRefreshToken);

        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                accessToken,
                refreshToken
        );
    }

    public RefreshTokenResponse refreshToken(
            RefreshTokenRequest request
    ) {
        String refreshToken = request.getRefreshToken();

        RefreshToken storedRefreshToken =
                refreshTokenRepository.findByToken(refreshToken)
                        .orElseThrow(() ->
                                new InvalidCredentialsException(
                                        "Invalid refresh token"
                                )
                        );

        if (storedRefreshToken.isRevoked()
                || !storedRefreshToken.getExpiresAt().isAfter(
                LocalDateTime.now(ZoneOffset.UTC)
        )) {
            throw new InvalidCredentialsException(
                    "Invalid refresh token"
            );
        }

        String email = validateRefreshToken(refreshToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid refresh token"
                        )
                );

        if (!storedRefreshToken.getUser().getId().equals(user.getId())) {
            throw new InvalidCredentialsException(
                    "Invalid refresh token"
            );
        }

        String newAccessToken =
                jwtService.generateAccessToken(user.getEmail());

        return new RefreshTokenResponse(newAccessToken);
    }

    private String validateRefreshToken(String token) {
        try {
            if (!jwtService.isRefreshToken(token)) {
                throw new InvalidCredentialsException(
                        "Invalid refresh token"
                );
            }

            String email = jwtService.extractEmail(token);

            if (email == null
                    || email.isBlank()
                    || !jwtService.isTokenValid(token, email)) {
                throw new InvalidCredentialsException(
                        "Invalid refresh token"
                );
            }

            return email;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidCredentialsException(
                    "Invalid refresh token"
            );
        }
    }

    public void logout(RefreshTokenRequest request) {
        RefreshToken storedRefreshToken =
                refreshTokenRepository
                        .findByToken(request.getRefreshToken())
                        .orElseThrow(() ->
                                new InvalidCredentialsException(
                                        "Invalid refresh token"
                                )
                        );

        storedRefreshToken.setRevoked(true);
        refreshTokenRepository.save(storedRefreshToken);
    }
}